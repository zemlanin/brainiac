(ns ^:figwheel-always brainiac.ajax
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [ajax.core :as ajax]
              [cljs.core.async :refer [>! chan close!]]))

(defn chan-hand [ch]
  (fn [event]
    (go (>! ch event) (close! ch))))

(defn chan-hand-error [ch]
  (fn [event]
    (go (>! ch (new js/Error event)) (close! ch))))

(defn GET
  ([url] (GET url {}))
  ([url m] (let [ch (chan 1)]
              (ajax/GET url (merge m {:handler (chan-hand ch)
                                      :error-handler (chan-hand-error ch)
                                      :response-format :json
                                      :format :json
                                      :keywords? true}))
              ch)))

(defn POST
  ([url] (POST url {}))
  ([url m] (let [ch (chan 1)]
              (ajax/POST url (merge m {:handler (chan-hand ch)
                                        :error-handler (chan-hand-error ch)
                                        :response-format :json
                                        :format :json
                                        :keywords? true}))
              ch)))
