(ns ^:figwheel-always brainiac.utils)

(defn tap-n-print [v] (println v) v)

(defn =in [a b & p] (= (get-in a p) (get-in b p)))

(defn throw-err [e]
  (when (instance? js/Error e) (throw e))
  e)