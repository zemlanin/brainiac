(ns ^:figwheel-always brainiac.components.controls
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [cljs.core.async :refer [>! <! put! chan]]
              [cljs.core.match :refer-macros [match]]))

(rum/defc controls-component []
  [:div
    [:a {:className "action fa-cloud-download"}]
    [:a {:className "action fa-cloud-upload"}]])
