(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                     [brainiac.macros :refer [<?]])
    (:require [brainiac.utils :as u]
              [brainiac.appstate :as app]
              [brainiac.ajax :refer [GET POST]]
              [clojure.string]
              [cljs.core.async :as async :refer [<! chan sliding-buffer close! timeout]]
              [cljs.core.match :refer-macros [match]]))

(defn es-endpoint []
  (let [endpoint (-> @app/app-state :endpoint)]
    (str "http://" (:host endpoint) "/" (:index endpoint))))

(defn es-endpoint-mapping []
  (when-let [doc-type (-> @app/app-state :endpoint :doc-type)]
    (str (es-endpoint) "/" doc-type "/_mapping")))

(defn es-endpoint-search []
  (when-let [doc-type (-> @app/app-state :endpoint :doc-type)]
    (str (es-endpoint) "/" doc-type "/_search")))

(defn get-mapping []
  (when-let [es-index (-> @app/app-state :endpoint :index keyword)]
    (go
      (swap! app/app-state assoc :mapping
        (-> (<! (GET (es-endpoint-mapping)))
          vals
          first
          :mappings
          ((-> @app/app-state :endpoint :doc-type keyword)))))))


(defn get-filter-cond [[n f]]
  (match [(-> @app/app-state :endpoint :script-filters n :script) f]
    [nil {:type :boolean :value v}] {:term {n v}}
    [s {:type :boolean :value v}] {:script {:script (get s v)}}

    [nil {:type :number :value {:min a :max b}}] {:range {n {:gte a :lte b}}}
    [nil {:type :number :value {:min a}}] {:range {n {:gte a}}}
    [nil {:type :number :value {:max b}}] {:range {n {:lte b}}}
    [s {:type :number :value {:min a :max b}}] {:script {:script (get s #{:min :max}) :params {:min a :max b}}}
    [s {:type :number :value {:min a}}] {:script {:script (get s #{:min}) :params {:min a}}}
    [s {:type :number :value {:max b}}] {:script {:script (get s #{:max}) :params {:max b}}}
    :else nil))


(defn get-mapping-data [field]
  (-> @app/app-state :mapping :properties field :properties))

(defn get-match-cond [[n f]]
  (let [agg-field (when (-> n get-mapping-data :id) (str (name n) ".id"))]
    (match [agg-field f]
      [_ {:type :string :value v}] {:query {:match {n v}}}
      [nil {:type :obj :value v}] {:query {:match {(name n) (:name v)}}}
      [(_ :guard some?) {:type :obj :value {:id v}}] {:query {:match {agg-field v}}}
      :else nil)))

(defn get-obj-fields []
  (->> @app/app-state
      :mapping
      :properties
      (filter #(-> % second :properties :id))
      keys))

(def req-chan (chan (sliding-buffer 1)))
(def req-suggestions-chan (chan (sliding-buffer 1)))

(defn extract-suggestions [resp field]
  (let [state @app/app-state
        field-settings (-> state :endpoint :suggesters field)
        display-field (when (-> field get-mapping-data :name)
                        :name)]
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

(defn extract-counters [resp field]
  (let [aggs (-> resp :aggregations)
        terms-agg (-> aggs ((keyword (str (name field) "-terms"))))
        cardinality-agg (-> aggs ((keyword (str (name field) "-cardinality"))))]

    {:buckets (-> terms-agg :buckets)
      :total (-> cardinality-agg :value)}))

(defn perform-search [msg]
  (let [ch (chan 1)]
    (go
      (let [state @app/app-state
            applied (filter #(not (nil? (second %))) (:applied state))
            applied-filtered (filter some? (map get-filter-cond applied))
            applied-match (filter some? (map get-match-cond applied))
            suggesters (-> state :endpoint :suggesters keys)
            counter-fields (-> state :endpoint :facet-counters)
            agg-fields (concat suggesters (get-obj-fields))
            suggesters-params (into {} (for [field agg-fields]
                                          (let [settings (-> state :endpoint :suggesters field)
                                                agg-field (cond
                                                            (:agg-field settings) (:agg-field settings)
                                                            (-> field get-mapping-data :id) (str (name field) ".id")
                                                            :else (name field))
                                                display-field (when
                                                                (-> field get-mapping-data :name)
                                                                :name)]
                                            {field
                                              (if display-field
                                                  {:terms {:field agg-field}
                                                    :aggs {:top {:top_hits {:size 1
                                                                            :_source {:include field}}}}}
                                                  {:terms {:field agg-field}})})))
            facet-counters-params (into {} (for [field counter-fields]
                                              { (keyword (str (name field) "-terms"))
                                                {:terms {:field (name field) :size 20}}
                                                (keyword (str (name field) "-cardinality"))
                                                {:cardinality {:field (name field)}}}))
            params {:aggs (merge suggesters-params facet-counters-params)
                    :query {:filtered {:filter
                                        {:bool
                                          {:must
                                            (concat applied-filtered applied-match)}}}}
                    :size (or (:size msg) 24)}
            raw (try
                  (<? (POST (es-endpoint-search) {:params params}))
                  (catch js/Error e
                    nil))
            suggestions (into {} (for [field agg-fields] {field (extract-suggestions raw field)}))
            facet-counters (into {} (for [field counter-fields] {field (extract-counters raw field)}))
            search-result (-> raw
                              (assoc :suggestions suggestions)
                              (assoc :facet-counters facet-counters)
                              (dissoc :aggregations))]
        (>! ch search-result)))
    ch))

(let [requests-ch (chan (sliding-buffer 5))]
  (go-loop [msg (<! req-chan)]
    (when msg
      (swap! app/app-state assoc :loading true)
      (>! requests-ch (perform-search msg))
      (recur (<! req-chan))))
  (go
    (while true
      (when-let [request (<! requests-ch)]
        (swap! app/app-state assoc :search-result (<! request))
        (when-not (some some? (-> requests-ch .-buf .-buf .-arr))
          (swap! app/app-state assoc :loading false))))))

(defn number-of-encounters [value target]
  (let [value (.toLowerCase value)
        target (.toLowerCase target)]

    (apply +
      (map
        #(when (and (not (clojure.string/blank? %)) (> (.indexOf target %) -1)) 1)
        (clojure.string/split value #" ")))))

(defn get-suggestions [msg]
  (let [ch (chan 1)]
    (go
      (let [field (-> msg :field)
            state @app/app-state
            field-settings (-> state :endpoint :suggesters field)
            query-field (cond
                          (:query-field field-settings) (:query-field field-settings)
                          (-> field get-mapping-data :name) (str (name field) ".name")
                          :else (name field))
            agg-field (cond
                        (:agg-field field-settings) (:agg-field field-settings)
                        (-> field get-mapping-data :id) (str (name field) ".id")
                        :else (name field))
            display-field (when (-> field get-mapping-data :name)
                            :name)
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
                    :size 0
                    :query
                      {:filtered
                        {:query {:match {query-field {:operator :and :fuzziness :AUTO :query value}}}}}}
            raw (<! (POST (es-endpoint-search) {:params params}))]
          (>! ch [field (->> raw
                            (#(extract-suggestions % field))
                            (sort-by #(number-of-encounters value (display-field %)) >))])))
    ch))

(def throttled-req-suggestions-chan (u/throttle req-suggestions-chan 2000))

(let [requests-ch (chan (sliding-buffer 5))]
  (go-loop [msg (<! throttled-req-suggestions-chan)]
    (when msg
      (swap! app/app-state assoc :loading true)
      (>! requests-ch (get-suggestions msg))
      (recur (<! throttled-req-suggestions-chan))))
  (go-loop [request (<! requests-ch)]
    (when request
      (let [[field field-suggestions] (<! request)]
        (swap! app/app-state assoc-in [:search-result :suggestions field] field-suggestions)
        (when-not (some some? (-> requests-ch .-buf .-buf .-arr))
          (swap! app/app-state assoc :loading false)))
      (recur (<! requests-ch)))))

(defn setup-watcher []
  (get-mapping))
