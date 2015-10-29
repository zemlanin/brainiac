(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [brainiac.utils :as u]
              [brainiac.appstate :as app]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :refer [<!]]
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
    {:type :integer :value {:min v}} {:range {n {:gte v}}}
    {:type :integer :value {:max v}} {:range {n {:lte v}}}
    :else {:term {n f}}))

(defn perform-search [_ _ prev cur]
  (let [applied (filter #(not (nil? (second %))) (:applied cur))
        filter-cond (case (count applied)
                        0 {}
                        1 (get-filter-cond (first applied))
                        {:bool
                          {:must (map get-filter-cond applied)}})]

    (when-not (u/=in prev cur :applied)
      (go
        (swap! app/app-state assoc :search-result
          (<! (POST (es-endpoint-search)
                        {:params
                          {:query
                            {:filtered
                              {:filter filter-cond}}}})))))))

(defn setup-watcher []
  (get-mapping)
  (remove-watch app/app-state :search-watcher)
  (add-watch app/app-state :search-watcher perform-search))
