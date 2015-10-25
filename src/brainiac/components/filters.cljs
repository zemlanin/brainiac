(ns ^:figwheel-always brainiac.components.filters
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [cljs.core.async :refer [>! <! put! chan]]))

(defn checkbox-onclick [n e]
  (.preventDefault e)
  (let [checked (boolean (get-in @app/app-state [:applied n]))]
    (swap! app/app-state assoc-in [:applied] {n (not checked)})))

(rum/defc filters-component < rum/reactive []
  (let [applied (:applied (rum/react app/app-state))]
    [:div
      [:h3 "filters"]
      [:label "has_upper_case"
        [:input {:type "checkbox"
                  :onClick #(checkbox-onclick "has_upper_case" %)
                  :checked (get applied "has_upper_case")}]]]))
