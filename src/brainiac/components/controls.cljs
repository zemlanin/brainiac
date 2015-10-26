(ns ^:figwheel-always brainiac.components.controls
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [cljs.reader :refer [read-string]]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :refer [<!]]))

(defn export-state []
  (when-let [new-state (js/prompt "Copy or paste state" (dissoc @app/app-state :search-result))]
    (reset! app/app-state (read-string new-state))))

(defn cloud-import []
  (let [cloud (:cloud @app/app-state)
        new-cloud (js/prompt "Paste brainiac endpoint" (or cloud ""))]
    (when new-cloud (swap! app/app-state assoc :cloud new-cloud))))

(defn update-controls [_ _ prev cur]
  (when-not (= (:cloud prev) (:cloud cur))
    (go
      (swap! app/app-state assoc :endpoint
        (<! (GET (:cloud cur)))))))

(rum/defc controls-component []
  [:div
    [:a {:className "action fa fa-cloud-download"
          :onClick cloud-import}]
    [:a {:className "action fa fa-upload"
          :onClick export-state}]])

(defn setup-watcher []
  (remove-watch app/app-state :controls-watcher)
  (add-watch app/app-state :controls-watcher update-controls))
