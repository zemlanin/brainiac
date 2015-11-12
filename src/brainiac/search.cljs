(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go]]
                      [brainiac.macros :refer [<?]])
    (:require [brainiac.utils :as u]
              [brainiac.appstate :as app]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :refer [<! chan sliding-buffer close!]]
              [cljs.core.match :refer-macros [match]]))

(defn es-endpoint []
  (when-let [selected (-> @app/app-state :endpoint :selected)]
      (str "http://" (:host selected) "/" (:index selected))))

(defn es-endpoint-mapping []
  (if-let [doc-type (-> @app/app-state :endpoint :selected :doc-type)]
      (str (es-endpoint) "/" doc-type "/_mapping")
      (str (es-endpoint) "/_mapping")))

(defn es-endpoint-search []
  (when-let [doc-type (-> @app/app-state :endpoint :selected :doc-type)]
      (str (es-endpoint) "/" doc-type "/_search")))

(defn get-mapping []
  (when-let [es-index (-> @app/app-state :endpoint :selected :index keyword)]
    (go
      (swap! app/app-state assoc :mappings
          (-> (<! (GET (es-endpoint-mapping)))
              es-index
              :mappings)))))

(defn get-filter-cond [[n f]]
  (match f
    {:type :boolean :value v} {:term {n v}}
    {:type :integer :value {:min a :max b}} {:range {n {:gte a :lte b}}}
    {:type :integer :value {:min a}} {:range {n {:gte a}}}
    {:type :integer :value {:max b}} {:range {n {:lte b}}}
    :else nil))

(defn get-match-cond [[n f]]
  (match [n f]
    [_ {:type :string :value v}] {:query {:match {n v}}}
    [:categories {:type :obj :value {:id v} :obj-field obj-field}] {:query {:match {"categories.id" v}}}
    [:categories _] nil
    [_ {:type :obj :value v :obj-field obj-field}] {:query {:match {(str (name n)) (:name v)}}}
    :else nil))

(def req-chan (chan (sliding-buffer 1)))
(def cats-chan (chan (sliding-buffer 1)))

(defn extract-categories-suggestions [resp field]
  (let [state @app/app-state
        field-settings (-> state :cloud :suggesters field)
        display-field (:display-field field-settings)]
    (-> resp
        :aggregations
        field
        :buckets
        ((fn [v]
          (for [{id :key top :top doc_count :doc_count}
                v] {:id id
                    :name (if (= :categories field)
                            (->> top
                                :hits
                                :hits
                                first
                                :_source
                                field
                                (some #(and (= id (:id %)) %))
                                display-field)
                            id)
                    :count doc_count}))))))

(go
  (while true
    (let [msg (<! req-chan)
          applied (filter #(not (nil? (second %))) (:applied @app/app-state))
          applied-filtered (filter some? (map get-filter-cond applied))
          applied-match (filter some? (map get-match-cond applied))
          state @app/app-state
          suggesters (-> state :cloud :suggesters)
          params {:aggs (into {} (for [[field settings] suggesters]
                                    {field
                                      (if (:display-field settings)
                                          {:terms {:field (:agg-field settings)}
                                            :aggs {:top {:top_hits {:size 1
                                                    :_source {:include field}}}}}
                                          {:terms {:field (:agg-field settings)}})}))
                  :query {:filtered {:filter
                                      {:bool
                                        {:must
                                          (concat applied-filtered applied-match)}}}}}
          raw (try
                (<? (POST (es-endpoint-search) {:params params}))
                (catch js/Error e
                  nil))
          suggestions (into {} (for [[field] suggesters] {field (extract-categories-suggestions raw field)}))
          search-result (-> raw
                            (assoc :suggestions suggestions)
                            (dissoc :aggregations))]
      (swap! app/app-state assoc :search-result search-result))))

(go
  (while true
    (let [msg (<! cats-chan)
          field (-> msg :field)
          state @app/app-state
          field-settings (-> state :cloud :suggesters field)
          value (-> state
                    :applied
                    field
                    :value
                    :name)
          params {:aggs {field
                          {:terms {:field (:agg-field field-settings)
                                    :size 100}
                            :aggs {:top {:top_hits {:size 1
                                                    :_source {:include field}}}}}}
                  :query
                    {:filtered
                      {:query {:prefix {(:query-field field-settings) value}}}}}
          raw (<! (POST (es-endpoint-search) {:params params}))
          field-suggestions (->> raw
                                  (#(extract-categories-suggestions % field))
                                  (sort-by #(= -1 (.indexOf ((:display-field field-settings) %) value))))]
      (swap! app/app-state assoc-in [:search-result :suggestions field] field-suggestions))))

(defn setup-watcher []
  (get-mapping))
