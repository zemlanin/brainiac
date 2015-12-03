(ns ^:figwheel-always brainiac.utils
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ajax.core :as ajax]
            [cljs.core.async :refer [>! <! chan timeout]]))

(defn t [v] (println v) v)

(defn =in [a b & p] (= (get-in a p) (get-in b p)))

(defn throw-err [e]
  (when (instance? js/Error e) (throw e))
  e)

(defn throttle [c ms]
  (let [c' (chan)]
    (go
      (while true
        (>! c' (<! c))
        (<! (timeout ms))))
    c'))
