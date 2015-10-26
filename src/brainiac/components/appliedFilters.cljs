(ns ^:figwheel-always brainiac.components.appliedFilters
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]))

(rum/defc appliedFilters-component < rum/reactive []
  (let [applied (:applied (rum/react app/app-state))]
    [:div
      [:ul (for [[k x] applied]
              (case x
                nil nil
                false [:li {:key (name k)}
                        [:strike (name k)]]
                true [:li {:key (name k)}
                        (name k)]))]]))
