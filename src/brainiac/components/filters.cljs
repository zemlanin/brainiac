(ns ^:figwheel-always brainiac.components.filters
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [brainiac.search :as search]
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
      (swap! app/app-state assoc :applied (dissoc applied n)))
    (go (>! search/req-chan {}))))

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
        (swap! app/app-state assoc :applied (dissoc applied n)))
        (go (>! search/req-chan {}))))

(defn string-onchange [n e]
  (.preventDefault e)
  (let [applied (:applied @app/app-state)
        old-value (n applied)
        v (-> e .-target .-value)
        new-value (if (empty? v)
                    nil
                    {:type :string :value v})]
    (if (some? new-value)
      (swap! app/app-state assoc-in [:applied n] new-value)
      (swap! app/app-state assoc :applied (dissoc applied n)))
    (go (>! search/req-chan {}))))

(defn suggester-onchange [n e]
  (.preventDefault e)
  (let [applied (:applied @app/app-state)
        old-value (n applied)
        v (-> e .-target .-value)
        new-value (if (empty? v)
                    nil
                    {:type :obj :value {:name v} :obj-field :id})]
    (if (some? new-value)
      (do
        (swap! app/app-state assoc-in [:applied n] new-value)
        (go (>! search/cats-chan {:field n})))
      (do
        (swap! app/app-state assoc :applied (dissoc applied n))
        (go (>! search/req-chan {}))))))

(defn suggester-suggestion-onclick [n v]
  (let [applied (:applied @app/app-state)
        old-value (n applied)
        new-value {:type :obj :value v :obj-field :id}]
    (if (some? new-value)
      (swap! app/app-state assoc-in [:applied n] new-value)
      (swap! app/app-state assoc :applied (dissoc applied n)))
    (go (>! search/req-chan {}))))

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

(defn string-filter [n {v :value}]
  [:fieldset
    [:legend
      [:label
        (name n)
        (when v [:a {:class "fa fa-remove"}])
        [:input {:type :checkbox
                  :style {:display :none}
                  :value ""
                  :checked v
                  :onChange #(when-not (-> % .-target .-checked) (string-onchange n %))}]]]

    [:input {:style {:width "80%"}
              :value v
              :onChange #(string-onchange n %)}]])

(defn suggester-filter [n {v :value}]
  (let [state @app/app-state
        suggestions (-> state :search-result :suggestions n)
        field-settings (-> state :cloud :suggesters n)
        checked (or (:checked field-settings) identity)]

    [:fieldset
      [:legend
        [:label
          (name n) "*"
          (when (:id v) [:a {:class "fa fa-remove"}])
          [:input {:type :checkbox
                    :style {:display :none}
                    :value ""
                    :checked (-> v checked)
                    :onChange #(when-not (-> % .-target .-checked) (suggester-onchange n %))}]]]

      [:input {:style {:width "80%"}
                :value (:name v)
                :onChange #(suggester-onchange n %)}]
      [:ul (for [s (take 10 suggestions)]
              [:li [:a
                      {:onClick #(suggester-suggestion-onclick n s)}
                      (:name s) [:sup (:count s)]]])]]))

(defn match-filter-type [filter-name filter-data value]
  (match [filter-name filter-data]
    [_ :guard #(contains? (-> @app/app-state :cloud :suggesters) %) _] [:li {:key filter-name} (suggester-filter filter-name value)]
    [_ {:type "boolean"}] [:li {:key filter-name} (boolean-filter filter-name value)]
    [_ {:type "integer"}] [:li {:key filter-name} (integer-filter filter-name value)]
    [_ {:type "long"}] [:li {:key filter-name} (integer-filter filter-name value)]
    [_ {:type "string"}] [:li {:key filter-name} (string-filter filter-name value)]
    [_ {:index "no"}] nil
    [_ {:properties _}] [:li {:key filter-name
                              :style {:color "gray"
                                      :fontSize "0.6em"}} "obj" (str filter-name)]
    :else [:li {:key filter-name
                :style {:color "gray"
                        :fontSize "0.6em"}} (str filter-data) (str filter-name)]))

(rum/defc filters-component < rum/reactive []
  (let [state (rum/react app/app-state)
        applied (:applied state)
        doc-type (-> state :endpoint :selected :doc-type keyword)
        filters (when doc-type (-> state :mappings doc-type :properties))]
    [:div
      [:ul (for [[n filter-data] (into [] filters)]
              (match-filter-type n filter-data (get applied n)))]
  ]))
