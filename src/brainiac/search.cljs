(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [brainiac.appstate :as app]
              [ajax.core :as ajax]
              [cljs.core.async :refer [>! <! chan close!]]))

(def es-host "http://localhost:9200")
(def es-index "uaprom2_brainiac")
(def es-doc-type "product")

(def es-endpoint (str es-host "/" es-index "/" es-doc-type))
(def es-endpoint-mapping (str es-endpoint "/_mapping"))
(def es-endpoint-search (str es-endpoint "/_search"))

(defn chan-hand [ch]
  (fn [event]
    (go (>! ch event) (close! ch))))

(defn GET
  ([url] (GET url {}))
  ([url m] (let [ch (chan 1)]
              (ajax/GET url (merge m {:handler (chan-hand ch)
                                      :response-format :json
                                      :format :json
                                      :keywords? true}))
              ch)))

(defn POST
  ([url] (POST url {}))
  ([url m] (let [ch (chan 1)]
              (ajax/POST url (merge m {:handler (chan-hand ch)
                                        :response-format :json
                                        :format :json
                                        :keywords? true}))
              ch)))

(defn get-mapping []
  (go
    (swap! app/app-state assoc :mappings
        (-> (<! (GET es-endpoint-mapping))
            ((keyword es-index))
            :mappings
            ((keyword es-doc-type))
            :properties))))

(defn perform-search [_ _ prev cur]
  (let [applied (filter #(not (nil? (second %))) (:applied cur))
        filter-cond (case (count applied)
                        0 {}
                        1 (let [[n v] (first applied)] {:term {n v}})
                        {:bool
                          {:must (for [[f v] applied]
                            {:term {f v}})}})]

    (when-not (= (:applied prev) (:applied cur))
      (go
        (swap! app/app-state assoc :search-result
          (-> (<! (POST es-endpoint-search
                        {:params
                          {:query
                            {:filtered
                              {:filter filter-cond}}}}))))))))

(defn setup-watcher []
  (get-mapping)
  (remove-watch app/app-state :search-watcher)
  (add-watch app/app-state :search-watcher perform-search))
