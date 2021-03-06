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

(defn boolean-onchange [n v]
  (let [applied (:applied @app/app-state)
        old-value (n applied)
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
                        [(_ :guard #(-> % js/parseInt js/isNaN not)) nil] {:type :number :value {t (js/parseInt v)}}
                        [(_ :guard #(-> % js/parseInt js/isNaN not)) _] (assoc-in old-value [:value t] (js/parseInt v))
                        ; TODO: save another value when another is removed
                        :else nil))]
      (if (some? new-value)
        (swap! app/app-state assoc-in [:applied n] new-value)
        (swap! app/app-state assoc :applied (dissoc applied n)))
      (go (>! search/req-chan {}))))

(defn float-onchange [n t e]
    (.preventDefault e)
    (let [applied (:applied @app/app-state)
          old-value (n applied)
          v (-> e .-target .-value js-str->clj)
          new-value (if (= t :all)
                      v
                      (match [v old-value]
                        [(_ :guard #(-> % js/parseFloat js/isNaN not)) nil] {:type :number :value {t v}}
                        [(_ :guard #(-> % js/parseFloat js/isNaN not)) _] (assoc-in old-value [:value t] v)
                        ; TODO: save another value when another is removed
                        :else nil))]
      (if (some? new-value)
        (swap! app/app-state assoc-in [:applied n] new-value)
        (swap! app/app-state assoc :applied (dissoc applied n)))
      (go (>! search/req-chan {}))))


(defn string-set-filter [n v]
  (let [applied (:applied @app/app-state)
        old-value (n applied)
        new-value (if (empty? v)
                    nil
                    {:type :string :value v})]
    (if (some? new-value)
      (swap! app/app-state assoc-in [:applied n] new-value)
      (swap! app/app-state assoc :applied (dissoc applied n)))
    (go (>! search/req-chan {}))))


(defn string-onchange [n e]
  (.preventDefault e)
  (string-set-filter n (-> e .-target .-value)))

(defn obj-onchange [n e]
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
        (go (>! search/req-suggestions-chan {:field n})))
      (do
        (swap! app/app-state assoc :applied (dissoc applied n))
        (go (>! search/req-chan {}))))))

(defn obj-suggestion-onclick [n v]
  (let [applied (:applied @app/app-state)
        old-value (n applied)
        new-value {:type :obj :value v :obj-field :id}]
    (if (some? new-value)
      (swap! app/app-state assoc-in [:applied n] new-value)
      (swap! app/app-state assoc :applied (dissoc applied n)))
    (go (>! search/req-chan {}))))

(rum/defc boolean-filter < rum/static [n {v :value}]
  [:fieldset
    [:ul {:class "pure-control-group buttons-group"}
      [:button {:class (str "pure-button negative" (when (= v false) " active"))
                :on-click #(boolean-onchange n false)}
        "-"]
      [:button {:class (str "pure-button secondary" (when (= v nil) " active"))
                :on-click #(boolean-onchange n nil)}
        "\u00A0"]
      [:button {:class (str "pure-button positive" (when (= v true) " active"))
                :on-click #(boolean-onchange n true)}
        "+"]]
    [:label (name n)]])

(rum/defc integer-filter < rum/static [n {{v-min :min v-max :max :as v} :value}]
  [:fieldset
    [:legend
      [:label
        (name n)
        (when v [:a {:class "fa fa-remove"}])
        [:input {:type "checkbox"
                  :style {:display "none"}
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

(rum/defc float-filter < rum/static [n {{v-min :min v-max :max :as v} :value}]
  [:fieldset
    [:legend
      [:label
        (name n)
        (when v [:a {:class "fa fa-remove"}])
        [:input {:type "checkbox"
                  :style {:display "none"}
                  :value "null"
                  :checked v
                  :onChange #(when-not (-> % .-target .-checked) (float-onchange n :all %))}]]]

    [:input {:type :number
              :step :any
              :style {:width "30%"}
              :value v-min
              :onChange #(float-onchange n :min %)}]
    (if (and v-min v-max (> v-min v-max)) " ≥ x ≥ " " ≤ x ≤ ")
    [:input {:type :number
              :style {:width "30%"}
              :value v-max
              :onChange #(float-onchange n :max %)}]])

(rum/defc string-filter < rum/static [n {v :value} suggestions]
  [:fieldset
    [:legend
      [:label
        (name n)
        (when v [:a {:class "fa fa-remove"}]
          [:input {:type "checkbox"
                    :style {:display "none"}
                    :value ""
                    :checked v
                    :onChange #(when-not (-> % .-target .-checked) (string-onchange n %))}])]]
    (if suggestions
      (if v
        [:ul
          [:li [:span v]]]
        [:ul (for [s (take 10 suggestions)]
                [:li [:a
                        {:onClick #(string-set-filter n (:name s))}
                        (:name s) [:sup (:count s)]]])])
      [:input {:style {:width "80%"}
                :value v
                :onChange #(string-onchange n %)}])])

(rum/defc obj-filter < rum/static [n {v :value props :properties} suggestions]
  [:fieldset
    [:legend
      [:label
        (name n) "*"
        (when (:id v) [:a {:class "fa fa-remove"}])
        [:input {:type "checkbox"
                  :style {:display "none"}
                  :value ""
                  :checked (-> v :id)
                  :onChange #(when-not (-> % .-target .-checked) (obj-onchange n %))}]]]

    [:input {:style {:width "80%"}
              :value (:name v)
              :onChange #(obj-onchange n %)}]
    [:ul (for [s (take 10 suggestions)]
            [:li [:a
                    {:onClick #(obj-suggestion-onclick n s)}
                    (:name s) [:sup (:count s)]]])]])

(defn match-filter-type [filter-name filter-data value]
  (let [suggestions (-> @app/app-state :search-result :suggestions filter-name)]
    (match [filter-name filter-data]
      [_ {:type "boolean"}] [:li {:key filter-name} (boolean-filter filter-name value)]
      [_ {:type "integer"}] [:li {:key filter-name} (integer-filter filter-name value)]
      [_ {:type "long"}] [:li {:key filter-name} (integer-filter filter-name value)]
      [_ {:type "string"}] [:li {:key filter-name} (string-filter filter-name value suggestions)]
      [_ {:type "float"}] [:li {:key filter-name} (float-filter filter-name value)]
      [_ {:type "double"}] [:li {:key filter-name} (float-filter filter-name value)]
      [_ {:index "no"}] nil
      [_ {:properties {:id _ :name _}}] [:li {:key filter-name} (obj-filter filter-name value suggestions)]
      [_ {:properties _}] [:li {:key filter-name
                                :style {:color "gray"
                                        :fontSize "0.6em"}} "obj" (str filter-name)]
      :else [:li {:key filter-name
                  :style {:color "gray"
                          :fontSize "0.6em"}} (str filter-data) (str filter-name)])))

(rum/defc filters-component < rum/reactive []
  (let [state (rum/react app/app-state)
        applied (:applied state)
        type-replacements (-> state :endpoint :replace-filter-types)
        filters (-> state :mapping :properties)
        script-filters (-> state :endpoint :script-filters)
        hidden-filters (-> state :endpoint :hidden-filters)]
    [:div
      [:ul (for [[n filter-data] (->> (merge filters script-filters)
                                    (into [])
                                    (remove #(contains? hidden-filters (first %)))
                                    (sort-by first)
                                    (sort-by #(contains? applied (first %)) >)
                                    (map (fn [[n v]]
                                            (if (contains? type-replacements n)
                                              [n (n type-replacements)]
                                              [n v]))))]
              (match-filter-type n filter-data (get applied n)))]]))
