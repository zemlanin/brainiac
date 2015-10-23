(ns ^:figwheel-always sho.search
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [om.core :as om :include-macros true]
              [sho.appstate :as app]
              [ajax.core :as ajax]
              [cljs.core.async :refer [>! <! chan close!]]))

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

(defn perform-search [_ _ prev cur]
  (when-not (= (:applied prev) (:applied cur))
    (go
      (swap! app/app-state assoc :products
        (-> (<! (POST "http://localhost:9200/uaprom2_brainiac/product/_search"
                      {:params
                        {:query
                          {:filtered
                            {:filter
                              {:term (:applied cur)}}}}}))))
      (.log js/console "searching"))))

(defn setup-watcher []
  (remove-watch app/app-state :search-watcher)
  (add-watch app/app-state :search-watcher perform-search))
