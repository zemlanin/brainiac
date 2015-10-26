(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [brainiac.appstate :as app]
              [ajax.core :as ajax]
              [cljs.core.async :refer [>! <! chan close!]]))

(def es-endpoint "http://localhost:9200/uaprom2_brainiac/product")
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
            :uaprom2_brainiac
            :mappings
            :product
            :properties))))

(defn perform-search [_ _ prev cur]
  (when-not (= (:applied prev) (:applied cur))
    (go
      (swap! app/app-state assoc :search-result
        (-> (<! (POST es-endpoint-search
                      {:params
                        {:query
                          {:filtered
                            {:filter
                              {:bool
                                {:must (for [[f v] (:applied cur)]
                                  {:term {f v}})}}}}}})))))))

(defn setup-watcher []
  (get-mapping)
  (remove-watch app/app-state :search-watcher)
  (add-watch app/app-state :search-watcher perform-search))
