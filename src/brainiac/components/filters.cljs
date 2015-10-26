(ns ^:figwheel-always brainiac.components.filters
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [cljs.core.async :refer [>! <! put! chan]]
              [cljs.core.match :refer-macros [match]]))

(defn js-str->clj [v]
  (case v
    "null" nil
    "false" false
    "true" true
    v))

(defn clj->js-str [v]
  (case v
    nil "null"
    false "false"
    true "true"
    v))

(defn radio-onchange [n e]
  (.preventDefault e)
  (let [checked (-> (.-target e) .-value js-str->clj)
        applied (:applied @app/app-state)]
    (swap! app/app-state assoc :applied (assoc applied n checked))))

(defn boolean-filter [n v]
  [:fieldset
    [:legend (name n)]
    [:ul (for [bool-val [nil false true]]
            (let [str-val (clj->js-str bool-val)]
              [:li {:key str-val
                    :style {:display "inline"
                            :listStyleType "none"}}
                [:input {:type "radio"
                          :checked (= v bool-val)
                          :onChange #(radio-onchange n %)
                          :value str-val}
                  str-val]]))]])

(rum/defc filters-component < rum/reactive []
  (let [applied (:applied (rum/react app/app-state))
        filters (:mappings (rum/react app/app-state))]
    [:div
      [:h3 "filters"]
      [:ul (for [[n filter-data] (into [] filters)]
                (match filter-data
                  {:type "boolean"} [:li {:key n} (boolean-filter n (get applied n))]
                  :else nil))]]))
