(ns ^:figwheel-always brainiac.components.filters
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [cljs.core.async :refer [>! <! put! chan]]))

(defn checkbox-onclick [n e]
  (.preventDefault e)
  (let [checked (boolean (get-in @app/app-state [:applied n]))
        applied (:applied @app/app-state)]
    (swap! app/app-state assoc :applied (assoc applied n (not checked)))))

(rum/defc filters-component < rum/reactive []
  (let [applied (:applied (rum/react app/app-state))
        filters (filter #(= "boolean" (:type (second %)))
                        (:mappings (rum/react app/app-state)))]
    [:div
      [:h3 "filters"]
      [:ul (for [[n filter-data] filters]
        [:li
          [:label
            [:input {:type "checkbox"
                      :onClick #(checkbox-onclick n %)
                      :checked (get applied n)}]]
            (name n)])]]))
