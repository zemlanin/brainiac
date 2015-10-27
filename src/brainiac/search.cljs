(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [brainiac.appstate :as app]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :refer [<!]]))

(def es-host "http://localhost:9200")
(def es-index "uaprom2_brainiac")
(def es-doc-type "product")

(defn es-endpoint []
  (if-let [endpoint (:endpoint @app/app-state)]
      (str "http://" (first (:hosts endpoint)) "/" (:index endpoint))
      (str es-host "/" es-index)))
(defn es-endpoint-mapping [] (str (es-endpoint) "/_mapping"))
(defn es-endpoint-search [] (str (es-endpoint) "/" es-doc-type "/_search"))

(defn get-mapping []
  (go
    (swap! app/app-state assoc :mappings
        (-> (<! (GET (es-endpoint-mapping)))
            ((keyword es-index))
            :mappings))))

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
          (<! (POST (es-endpoint-search)
                        {:params
                          {:query
                            {:filtered
                              {:filter filter-cond}}}})))))))

(defn setup-watcher []
  (get-mapping)
  (remove-watch app/app-state :search-watcher)
  (add-watch app/app-state :search-watcher perform-search))
