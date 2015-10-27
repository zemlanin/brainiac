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
  (let [applied (:applied @app/app-state)
        checked (-> (.-target e) .-value js-str->clj)
        new-applied (if (nil? checked)
                        (dissoc applied n)
                        (assoc applied n checked))]
    (swap! app/app-state assoc :applied new-applied)))

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
  (let [state (rum/react app/app-state)
        applied (:applied state)
        doc-type (or (-> state :endpoint :selected :doc-type keyword) :product)
        filters (-> state :mappings doc-type :properties)]
    [:div
      [:ul (for [[n filter-data] (into [] filters)]
                (match filter-data
                  {:type "boolean"} [:li {:key n} (boolean-filter n (get applied n))]
                  :else [:li {:key n
                              :style {:color "gray"
                                      :fontSize "0.6em"}} (:type filter-data) (str n)]))]]))
