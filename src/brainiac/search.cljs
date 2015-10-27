(ns ^:figwheel-always brainiac.search
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [brainiac.appstate :as app]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :refer [<!]]))

; TODO: get rid of all if-lets 
(defn es-endpoint []
  (if-let [selected (-> @app/app-state :endpoint :selected)]
      (str "http://" (:host selected) "/" (:index selected))
      "http://localhost:9200/product"))

(defn es-endpoint-mapping []
  (if-let [selected (-> @app/app-state :endpoint :selected)]
      (str (es-endpoint) "/" (:doc-type selected) "/_mapping")
      (str (es-endpoint) "/product/_mapping")))

(defn es-endpoint-search []
  (if-let [selected (-> @app/app-state :endpoint :selected)]
      (str (es-endpoint) "/" (:doc-type selected) "/_search")
      (str (es-endpoint) "/product/_search")))

(defn get-mapping []
  (let [es-index (-> @app/app-state :endpoint :selected :index keyword)]
    (go
      (swap! app/app-state assoc :mappings
          (-> (<! (GET (es-endpoint-mapping)))
              es-index
              :mappings)))))

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
