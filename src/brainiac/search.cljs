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
    {:type :number :value {:min a :max b}} {:range {n {:gte a :lte b}}}
    {:type :number :value {:min a}} {:range {n {:gte a}}}
    {:type :number :value {:max b}} {:range {n {:lte b}}}
    :else nil))


(defn get-mapping-data [field]
  (-> @app/app-state :mappings :product :properties field :properties))

(defn get-match-cond [[n f]]
  (let [agg-field (cond
                    (-> n get-mapping-data :id) (str (name n) ".id")
                    :else nil)]
    (match [agg-field f]
      [_ {:type :string :value v}] {:query {:match {n v}}}
      [nil {:type :obj :value v :obj-field obj-field}] {:query {:match {(name n) (:name v)}}}
      [(_ :guard some?) {:type :obj :value {:id v} :obj-field obj-field}] {:query {:match {agg-field v}}}
      :else nil)))

(defn get-obj-fields []
  (->> @app/app-state
      :mappings
      :product
      :properties
      (filter #(-> % second :properties :id))
      keys))


(def req-chan (chan (sliding-buffer 1)))
(def cats-chan (chan (sliding-buffer 1)))

(defn extract-categories-suggestions [resp field]
  (let [state @app/app-state
        field-settings (-> state :cloud :suggesters field)
        display-field (cond
                        (:display-field field-settings) (:display-field field-settings)
                        (-> field get-mapping-data :name) :name
                        :else nil)]
    (-> resp
        :aggregations
        field
        :buckets
        ((fn [v]
          (for [{id :key top :top doc_count :doc_count}
                v] {:id id
                    :name (if display-field
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
    (when-let [msg (<! req-chan)]
      (swap! app/app-state assoc :loading true)
      (let [applied (filter #(not (nil? (second %))) (:applied @app/app-state))
            applied-filtered (filter some? (map get-filter-cond applied))
            applied-match (filter some? (map get-match-cond applied))
            state @app/app-state
            suggesters (-> state :cloud :suggesters keys)
            agg-fields (concat suggesters (get-obj-fields))
            params {:aggs (into {} (for [field agg-fields]
                                      (let [settings (-> state :cloud :suggesters field)
                                            agg-field (cond
                                                        (:agg-field settings) (:agg-field settings)
                                                        (-> field get-mapping-data :id) (str (name field) ".id")
                                                        :else (name field))
                                            display-field (cond
                                                            (:display-field settings) (:display-field settings)
                                                            (-> field get-mapping-data :name) :name
                                                            :else nil)]
                                        {field
                                          (if display-field
                                              {:terms {:field agg-field}
                                                :aggs {:top {:top_hits {:size 1
                                                                        :_source {:include field}}}}}
                                              {:terms {:field agg-field}})})))
                    :query {:filtered {:filter
                                        {:bool
                                          {:must
                                            (concat applied-filtered applied-match)}}}}
                    :size (if (:only-aggs msg) 0 12)}
            raw (try
                  (<? (POST (es-endpoint-search) {:params params}))
                  (catch js/Error e
                    nil))
            suggestions (into {} (for [field agg-fields] {field (extract-categories-suggestions raw field)}))
            search-result (-> raw
                              (assoc :suggestions suggestions)
                              (dissoc :aggregations))]
        (swap! app/app-state dissoc :loading)
        (swap! app/app-state assoc :search-result search-result)))))

(go
  (while true
    (when-let [msg (<! cats-chan)]
      (swap! app/app-state assoc :loading true)
      (let [field (-> msg :field)
            state @app/app-state
            field-settings (-> state :cloud :suggesters field)
            query-field (cond
                          (:query-field field-settings) (:query-field field-settings)
                          (-> field get-mapping-data :name) (str (name field) ".name")
                          :else (name field))
            agg-field (cond
                        (:agg-field field-settings) (:agg-field field-settings)
                        (-> field get-mapping-data :id) (str (name field) ".id")
                        :else (name field))
            display-field (cond
                            (:display-field field-settings) (:display-field field-settings)
                            (-> field get-mapping-data :name) :name
                            :else identity)
            value (-> state
                      :applied
                      field
                      :value
                      :name)
            params {:aggs {field
                            {:terms {:field agg-field
                                      :size 100}
                              :aggs {:top {:top_hits {:size 1
                                                      :_source {:include field}}}}}}
                    :query
                      {:filtered
                        {:query {:prefix {query-field value}}}}}
            raw (<! (POST (es-endpoint-search) {:params params}))
            field-suggestions (->> raw
                                    (#(extract-categories-suggestions % field))
                                    (sort-by #(= -1 (.indexOf (display-field %) value))))]
        (swap! app/app-state dissoc :loading)
        (swap! app/app-state assoc-in [:search-result :suggestions field] field-suggestions)))))

(defn setup-watcher []
  (get-mapping))
