(ns ^:figwheel-always brainiac.components.appliedFilters
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]))

(defn toggle-value [k v]
  (let [applied (:applied @app/app-state)]
    (swap! app/app-state assoc :applied (assoc applied k (not v)))))

(defn remove-value [k]
  (let [applied (:applied @app/app-state)]
    (swap! app/app-state assoc :applied (dissoc applied k))))

(rum/defc appliedFilters-component < rum/reactive []
  (let [applied (:applied (rum/react app/app-state))]
    [:div
      [:ul (for [[k v] applied]
              (when-not (nil? v)
                [:li {:key (name k)}
                    [:a {:className (if v "fa fa-toggle-on" "fa fa-toggle-off")
                          :style {:marginRight "0.5em"}
                          :onClick #(toggle-value k v)}]
                    (name k)
                    [:a {:className "fa fa-remove"
                          :style {:marginLeft "0.5em"}
                          :onClick #(remove-value k)}]]))]]))
