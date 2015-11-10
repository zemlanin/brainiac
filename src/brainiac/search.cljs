(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go]])
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
    [_ {:type :string :value v}] {n v}
    [:categories {:type :obj :value {:id v} :obj-field obj-field}] {"categories.id" v}
    [:categories _] nil
    [_ {:type :obj :value v :obj-field obj-field}] {(str (name n) "." (name obj-field)) v}
    :else nil))

(def req-chan (chan (sliding-buffer 1)))
(def cats-chan (chan (sliding-buffer 1)))

(defn extract-categories-suggestions [resp]
  (-> resp
      :aggregations
      :categories
      :buckets
      ((fn [v]
        (for [{id :key top :top} v] {:id id
                                      :name (->> top
                                                :hits
                                                :hits
                                                first
                                                :_source
                                                :categories
                                                (some #(and (= id (:id %)) %))
                                                :name)})))))

(go
  (while true
    (let [msg (<! req-chan)
          applied (filter #(not (nil? (second %))) (:applied @app/app-state))
          applied-filtered (filter some? (map get-filter-cond applied))
          applied-match (filter some? (map get-match-cond applied))
          filter-cond (case (count applied-filtered)
                          0 {}
                          1 (first applied-filtered)
                          {:bool
                            {:must applied-filtered}})
          match-cond (into {} applied-match)
          params {:aggs {:categories
                          {:terms {:field "categories.id"}
                            :aggs {:top {:top_hits {:size 1
                                                    :_source {:include :categories}}}}}}
                  :query
                    {:filtered
                      (into {} [{:filter filter-cond}
                                (if (empty? match-cond)
                                  {}
                                  {:query {:match match-cond}})])}}
          raw (<! (POST (es-endpoint-search) {:params params}))
          categories-suggestions (extract-categories-suggestions raw)
          search-result (-> raw
                            (assoc :categories-suggestions categories-suggestions)
                            (dissoc :aggregations))]
      (swap! app/app-state assoc :search-result search-result))))

(go
  (while true
    (let [msg (<! cats-chan)
          state @app/app-state
          value (-> state
                    :applied
                    :categories
                    :value
                    :name)
          params {:aggs {:categories
                          {:terms {:field "categories.id"
                                    :size 100}
                            :aggs {:top {:top_hits {:size 1
                                                    :_source {:include :categories}}}}}}
                  :query
                    {:filtered
                      {:query {:prefix {"categories.name" value}}}}}
          raw (<! (POST (es-endpoint-search) {:params params}))
          categories-suggestions (->> raw
                                      extract-categories-suggestions
                                      (sort-by #(= -1 (.indexOf (:name %) value))))]
      (swap! app/app-state assoc-in [:search-result :categories-suggestions] categories-suggestions))))

(defn setup-watcher []
  (get-mapping))
