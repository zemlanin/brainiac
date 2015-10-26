(ns ^:figwheel-always brainiac.components.controls
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [cljs.reader :refer [read-string]]))

(defn export-state []
  (js/prompt "Copy current state" (dissoc @app/app-state :search-result)))

(defn import-state []
  (let [new-state (read-string (js/prompt "Paste state"))]
    (reset! app/app-state new-state)))

(rum/defc controls-component []
  [:div
    [:a {:className "action fa fa-download"
          :onClick export-state}]
    [:a {:className "action fa fa-upload"
          :onClick import-state}]])
