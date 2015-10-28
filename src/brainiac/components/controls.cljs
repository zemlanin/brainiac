(ns ^:figwheel-always brainiac.components.controls
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [schema.core :as s :include-macros true]
              [brainiac.appstate :as app]
              [brainiac.schema :as schema]
              [brainiac.search :as search]
              [cljs.reader :refer [read-string]]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :refer [<!]]))

(defn export-state []
  (when-let [new-state (js/prompt "Copy or paste state" (dissoc @app/app-state :search-result))]
    (reset! app/app-state (read-string new-state))))

(defn cloud-import []
  (let [cloud (:cloud @app/app-state)
        new-cloud (js/prompt "Paste brainiac endpoint" (or cloud ""))]
    (when new-cloud (swap! app/app-state assoc :cloud new-cloud))))

(defn set-doc-type [v]
  (swap! app/app-state assoc-in [:endpoint :selected :doc-type] v)
  (search/get-mapping))

(defn change-doc-type [e]
  (when-let [new-doc-type (-> e
                              .-target
                              .-value
                              (#(if (empty? %) nil (name %))))]
      (set-doc-type new-doc-type)))

(defn key-starts-with-dot [[k v]]
  (.startsWith (name k) "."))

(defn value-has-mappings [[k v]]
  (not (empty? (-> v :mappings))))

(defn tap-n-print [v] (println v) v)

(defn load-indices []
  (let [state @app/app-state
        endpoint (str "http://" (-> state :endpoint :selected :host) "/_mapping")]
      (go
        (let [indices (->> (<! (GET endpoint))
                          (filter #(not (key-starts-with-dot %)))
                          (filter value-has-mappings))
              stripped-indices (->> indices
                                    (map #(vector (first %) (-> % second :mappings keys)))
                                    (into {}))]
          (swap! app/app-state assoc-in [:endpoint :indices] stripped-indices)
          (when (= 1 (count indices))
              (swap! app/app-state assoc-in [:endpoint :selected :index] (name (ffirst indices))))))))

(defn write-new-field-input [new-value field-state settings-state]
  (swap! app/app-state assoc-in settings-state new-value)
  (swap! app/app-state update-in (butlast field-state) dissoc (last field-state)))

(defn check-field-input [e field-state settings-state]
  (let [new-value (-> e .-target .-value)]
    (if-not (s/check (get-in schema/StateSchema settings-state) new-value)
      (write-new-field-input new-value field-state settings-state)
      (swap! app/app-state assoc-in field-state new-value))))

(defn settings-modal  []
  (let [state @app/app-state]
    [:form {:className "pure-form pure-form-stacked"
            :style {:width "60vw"}
            :action "#"}

      [:div {:className "pure-g"}
        [:button {:className "pure-button pure-u-1-24"
                  :onClick cloud-import}
          [:div {:className "fa fa-download"}]]
        [:div {:className "pure-u-1-24"}]

        (let [field-path '(:settings :fields :host)
              saved-path '(:endpoint :selected :host)
              field-val (get-in state field-path)
              saved-val (get-in state saved-path)]
          [:label {:className "pure-u-7-24"} "host"
            [:input {:className "pure-u-23-24"
                      :type "text"
                      :value (or field-val saved-val)
                      :style {:borderColor (if field-val "red" "green")}
                      :onChange #(check-field-input % field-path saved-path)}]])

        (let [field-path '(:settings :fields :index)
              saved-path '(:endpoint :selected :index)
              field-val (get-in state field-path)
              saved-val (get-in state saved-path)]
          [:label {:className "pure-u-1-3"} "index"
            [:input {:className "pure-u-23-24"
                      :type "text"
                      :style {:borderColor (if field-val "red" "green")}
                      :value (or field-val saved-val)
                      :onChange #(check-field-input % field-path saved-path)}]
            (when (or field-val (empty? saved-val))
              (for [i (->> state
                            :endpoint
                            :indices
                            keys
                            (map name)
                            (filter #(if field-val (.startsWith % field-val) true))
                            (take 3))]
                  [:a {:style {:marginRight "1em"
                                :textDecoration "underline"}
                        :onClick #(write-new-field-input i field-path saved-path)}
                    i]))])

        (let [field-path '(:settings :fields :doc-type)
              saved-path '(:endpoint :selected :doc-type)
              field-val (get-in state field-path)
              saved-val (get-in state saved-path)

              selected-index (-> state :endpoint :selected :index keyword)
              doc-types (if selected-index (-> state :endpoint :indices selected-index) [])]
          [:label {:className "pure-u-7-24"} "doc_type"
            [:select {:className "pure-u-23-24"
                      :value saved-val
                      :onChange change-doc-type}
                [:option]
                (for [doc-type doc-types]
                  [:option doc-type])]])

        [:div {:className "pure-u-1"}
          [:label {:className "pure-u-1"} "state"
            [:textarea {:rows 6
                        :className "pure-u-23-24"
                        :value (str state)}]]]]]))

(defn display-settings []
  (let [modals (:modals @app/app-state)]
    (if (zero? (count modals)) (swap! app/app-state assoc :modals [#'settings-modal]))))

(defn update-controls [_ _ prev cur]
  (go
    (when-not (= (:cloud prev) (:cloud cur))
      (swap! app/app-state assoc :endpoint
        (<! (GET (:cloud cur)))))
    ; TODO: optimize requests
    (when-not (= (-> prev :endpoint :selected) (-> cur :endpoint :selected))
      (do
        (load-indices)
        (search/get-mapping)))))

(rum/defc controls-component []
  [:div
    [:a {:className "action fa fa-gear"
          :onClick display-settings}]
    [:a {:className "action fa fa-upload"
          :onClick export-state}]])

(defn setup-watcher []
  (when-not (:endpoint @app/app-state) (display-settings))
  (remove-watch app/app-state :controls-watcher)
  (add-watch app/app-state :controls-watcher update-controls))
