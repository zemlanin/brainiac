(ns ^:figwheel-always brainiac.components.savedFilters
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [cljs.core.async :refer [>! timeout]]
              [brainiac.appstate :as app]
              [brainiac.search :as search]))

(defn save-current-applied []
  (let [state @app/app-state
        applied (:applied state)
        save-key (str (hash applied))]
    (swap! app/app-state assoc-in [:saved-filters save-key] {:value applied})))

(defn undo-delete-saved-filter [key]
  (let [state @app/app-state
        target (get-in state [:saved-filters key])]

    (swap! app/app-state assoc-in [:saved-filters key] (dissoc target :deleting))))

(defn delete-saved-filter [key]
  (swap! app/app-state assoc-in [:saved-filters key :deleting] true)
  (go
    (let [_ (<! (timeout 3000))
          state @app/app-state
          saved-filters (:saved-filters state)
          target (get saved-filters key)]
      (when (-> target :deleting)
        (swap! app/app-state assoc :saved-filters (dissoc saved-filters key))))))

(defn set-applied [v]
  (swap! app/app-state assoc :applied v)
  (go (>! search/req-chan {})))

(rum/defc savedFilters-component < rum/reactive []
  (let [state (rum/react app/app-state)
        saved-filters (->> state :saved-filters (into []))
        builtin-filters (->> state :endpoint :builtin-filters (into []))
        current-applied-key (-> state :applied hash str)]
    [:div {}
      [:a {:className "save fa fa-save"
            :onClick #(save-current-applied)}]
      [:ul {}
        (for [[k {v :value}] builtin-filters]
          [:li {}
            [:a {:onClick #(set-applied v)} k]])
        (for [[k {v :value d :deleting}] saved-filters]
          (if d
            [:li {}
              [:a {:onClick #(undo-delete-saved-filter k)} "undo"]]
            [:li {:class (when (= k current-applied-key) "active")}
              (when (= k current-applied-key)
                [:a {:className "fa fa-upload"
                      :style {:marginRight "0.5em"
                              :color "#00A500"}
                      :href (.createObjectURL js/URL (js/Blob. #js[(.stringify js/JSON (clj->js v))] #js{:type "text/json"}))
                      :download (str "brainiac_" k ".json")}])
              [:a {:onClick #(set-applied v)} k]
              [:a {:className "fa fa-remove"
                    :style {:marginLeft "0.5em"}
                    :onClick #(delete-saved-filter k)}]]))]]))
