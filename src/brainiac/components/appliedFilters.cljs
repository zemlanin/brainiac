(ns ^:figwheel-always brainiac.components.appliedFilters
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [cljs.core.async :refer [>!]]
              [brainiac.search :as search]))

(defn toggle-value [k v]
  (let [applied (:applied @app/app-state)]
    (swap! app/app-state assoc :applied (assoc applied k {:type :boolean :value (not v)}))
    (go (>! search/req-chan {}))))

(defn remove-value [k]
  (let [applied (:applied @app/app-state)]
    (swap! app/app-state assoc :applied (dissoc applied k))
    (go (>! search/req-chan {}))))

(rum/defc appliedFilters-component < rum/reactive []
  (when-let [applied (:applied (rum/react app/app-state))]
    [:div
      [:ul (for [[k {v :value t :type}] applied]
              (if (and (= k :categories) (not (contains? v :id)))
                nil
                [:li {:key (name k)}
                      (when (= :boolean t)
                        [:a {:className (if v "fa fa-toggle-on" "fa fa-toggle-off")
                            :style {:marginRight "0.5em"}
                            :onClick #(toggle-value k v)}])
                      (name k)
                      (when (= k :categories) [:span ": " (:name v)])
                      [:a {:className "fa fa-remove"
                            :style {:marginLeft "0.5em"}
                            :onClick #(remove-value k)}]]))]]))
