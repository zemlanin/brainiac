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

(defn boolean-onchange [n e]
  (.preventDefault e)
  (let [applied (:applied @app/app-state)
        old-value (n applied)
        v (-> e .-target .-value js-str->clj)
        new-value (if (nil? v) nil {:type :boolean :value v})]
    (if (some? new-value)
      (swap! app/app-state assoc-in [:applied n] new-value)
      (swap! app/app-state assoc :applied (dissoc applied n)))))

(defn integer-onchange [n t e]
    (.preventDefault e)
    (let [applied (:applied @app/app-state)
          old-value (n applied)
          v (-> e .-target .-value js-str->clj)
          new-value (if (= t :all)
                      v
                      (match [v old-value]
                        [(_ :guard #(-> % js/parseInt js/isNaN not)) nil] {:type :integer :value {t (js/parseInt v)}}
                        [(_ :guard #(-> % js/parseInt js/isNaN not)) _] (assoc-in old-value [:value t] (js/parseInt v))
                        ; TODO: save another value when another is removed
                        :else nil))]
      (if (some? new-value)
        (swap! app/app-state assoc-in [:applied n] new-value)
        (swap! app/app-state assoc :applied (dissoc applied n)))))


(defn boolean-filter [n {v :value}]
  [:fieldset
    [:legend
      [:label
        (name n)
        (when (some? v) [:a {:class "fa fa-remove"}])
        [:input {:type :checkbox
                  :style {:display :none}
                  :value "null"
                  :checked (some? v)
                  :onChange #(when-not (-> % .-target .-checked) (boolean-onchange n %))}]]]

    [:ul (for [bool-val [false true]]
            (let [str-val (clj->js-str bool-val)]
              [:li {:key str-val
                    :style {:display "inline"
                            :listStyleType "none"}}
                [:label
                  [:input {:type "radio"
                            :checked (= v bool-val)
                            :onChange #(boolean-onchange n %)
                            :value str-val}
                  str-val]]]))]])

(defn integer-filter [n {{v-min :min v-max :max :as v} :value}]
  [:fieldset
    [:legend
      [:label
        (name n)
        (when v [:a {:class "fa fa-remove"}])
        [:input {:type :checkbox
                  :style {:display :none}
                  :value "null"
                  :checked v
                  :onChange #(when-not (-> % .-target .-checked) (integer-onchange n :all %))}]]]

    [:input {:type :number
              :style {:width "30%"}
              :value v-min
              :onChange #(integer-onchange n :min %)}]
    (if (and v-min v-max (> v-min v-max)) " ≥ x ≥ " " ≤ x ≤ ")
    [:input {:type :number
              :style {:width "30%"}
              :value v-max
              :onChange #(integer-onchange n :max %)}]])

(rum/defc filters-component < rum/reactive []
  (let [state (rum/react app/app-state)
        applied (:applied state)
        doc-type (-> state :endpoint :selected :doc-type keyword)
        filters (when doc-type (-> state :mappings doc-type :properties))]
    [:div
      [:ul (for [[n filter-data] (into [] filters)]
                (match filter-data
                  {:type "boolean"} [:li {:key n} (boolean-filter n (get applied n))]
                  {:type "integer"} [:li {:key n} (integer-filter n (get applied n))]
                  {:type "long"} [:li {:key n} (integer-filter n (get applied n))]
                  {:index "no"} nil
                  {:properties _} [:li {:key n
                                    :style {:color "gray"
                                            :fontSize "0.6em"}} "obj" (str n)]
                  :else [:li {:key n
                              :style {:color "gray"
                                      :fontSize "0.6em"}} (str filter-data) (str n)]))]]))
