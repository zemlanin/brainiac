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

(defn set-applied-value [n v]
  (let [applied (:applied @app/app-state)
        new-applied (if (nil? v)
                        (dissoc applied n)
                        (assoc applied n v))]
    (swap! app/app-state assoc :applied new-applied)))

(defn radio-onchange [n e]
  (.preventDefault e)
  (set-applied-value n (-> e .-target .-value js-str->clj)))

(defn boolean-filter [n v]
  [:fieldset
    [:legend
      [:label
        (name n)
        (when (some? v) [:a {:class "fa fa-remove"}])
        [:input {:type :checkbox
                  :style {:display :none}
                  :value "null"
                  :checked (some? v)
                  :onChange #(when-not (-> % .-target .-checked) (radio-onchange n %))}]]]

    [:ul (for [bool-val [false true]]
            (let [str-val (clj->js-str bool-val)]
              [:li {:key str-val
                    :style {:display "inline"
                            :listStyleType "none"}}
                [:label
                  [:input {:type "radio"
                            :checked (= v bool-val)
                            :onChange #(radio-onchange n %)
                            :value str-val}
                  str-val]]]))]])

(rum/defc filters-component < rum/reactive []
  (let [state (rum/react app/app-state)
        applied (:applied state)
        doc-type (-> state :endpoint :selected :doc-type keyword)
        filters (when doc-type (-> state :mappings doc-type :properties))]
    [:div
      [:ul (for [[n filter-data] (into [] filters)]
                (match filter-data
                  {:type "boolean"} [:li {:key n} (boolean-filter n (get applied n))]
                  :else [:li {:key n
                              :style {:color "gray"
                                      :fontSize "0.6em"}} (:type filter-data) (str n)]))]]))
