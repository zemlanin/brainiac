(ns ^:figwheel-always brainiac.components.filters
    (:require [om.core :as om :include-macros true]
              [brainiac.appstate :as app]
              [sablono.core :as html :refer-macros [html]]
              [cljs.core.async :refer [>! <! put! chan]]))

(defn checkbox-onclick [n e]
  (.preventDefault e)
  (let [checked (boolean (get-in @app/app-state [:applied n]))]
    (swap! app/app-state assoc-in [:applied] {n (not checked)})))

(defn filters-component [{applied :applied :as data}]
  (om/component
    (html
      [:div
        [:h3 "filters"]
        [:label "has_upper_case"
          [:input {:type "checkbox"
                    :onClick #(checkbox-onclick "has_upper_case" %)
                    :checked (get applied "has_upper_case")}]]])))
