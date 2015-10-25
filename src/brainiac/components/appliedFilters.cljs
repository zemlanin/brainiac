(ns ^:figwheel-always brainiac.components.appliedFilters
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]))

(rum/defc appliedFilters-component < rum/reactive []
  (let [applied (:applied (rum/react app/app-state))]
    [:div
      [:h3 "appliedFilters"]
      [:ul (for [[k x] applied]
              (when x [:li {:key k} k]))]]))
