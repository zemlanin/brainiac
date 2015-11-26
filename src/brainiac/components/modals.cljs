(ns ^:figwheel-always brainiac.components.modals
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]))

(defn close-modal []
  (swap! app/app-state assoc :modals (pop (:modals app/app-state))))

(rum/defc modals-component < rum/reactive []
  (let [modals (:modals (rum/react app/app-state))]
    (if (zero? (count modals))
      [:div]
      [:div {:className "wrapper"
              :onClick #(when
                          (= (.-target %) (.-currentTarget %))
                          (close-modal))}
        (for [m modals]
          [:div {:className "modal"}
            [:a {:className "fa fa-close"
                  :onClick #(close-modal)}]
            (m)])])))

(defn update-modal [_ _ prev cur]
  nil)

(defn setup-watcher []
  (remove-watch app/app-state :modal-watcher)
  (add-watch app/app-state :modal-watcher update-modal))
