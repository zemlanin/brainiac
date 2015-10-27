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

(defn change-doc-type [e]
  (when-let [new-doc-type (-> e
                              .-target
                              .-value
                              (#(if (empty? %) nil %)))]
      (swap! app/app-state assoc-in [:endpoint :selected :doc-type] (name new-doc-type))
      (search/get-mapping)))

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

(defn check-field-input [e field-state settings-state]
  (let [new-value (-> e .-target .-value)]
    (if-not (s/check (get-in schema/StateSchema settings-state) new-value)
      (do
        (swap! app/app-state assoc-in settings-state new-value)
        (swap! app/app-state update-in (butlast field-state) dissoc (last field-state))
        (load-indices))
      (swap! app/app-state assoc-in field-state new-value))))

(defn settings-modal  []
  (let [state @app/app-state
        {endpoint :endpoint
          mappings :mappings
          settings :settings} state]
    [:form {:className "pure-form pure-form-stacked"
            :style {:width "60vw"}
            :action "#"}

      [:div {:className "pure-g"}
        [:button {:className "pure-button pure-u-1-24"
                  :onClick cloud-import}
          [:div {:className "fa fa-download"}]]
        [:div {:className "pure-u-1-24"}]

        [:label {:className "pure-u-7-24"} "host"
          [:input {:className "pure-u-23-24"
                    :type "text"
                    :value (or (-> settings :host) (-> endpoint :selected :host))
                    :style {:borderColor (if (:host settings) "red" "green")}
                    :onChange #(check-field-input % '(:settings :host) '(:endpoint :selected :host))}]]

        [:label {:className "pure-u-1-3"} "index"
          [:input {:className "pure-u-23-24"
                    :type "text"
                    :style {:borderColor (if (:index settings) "red" "green")}
                    :value (or (-> settings :index) (-> endpoint :selected :index))
                    :onChange #(check-field-input % '(:settings :index) '(:endpoint :selected :index))}]]

        [:label {:className "pure-u-7-24"} "doc_type"
          [:select {:className "pure-u-23-24"
                    :value (-> endpoint :selected :doc-type)
                    :onChange change-doc-type}
              [:option]
              (when (-> endpoint :selected :index)
                (for [doc-type (-> endpoint
                                    :indices
                                    ((-> endpoint :selected :index keyword)))]
                  [:option doc-type]))]]

        [:div {:className "pure-u-1"}
          [:label {:className "pure-u-1"} "state"
            [:textarea {:rows 6
                        :className "pure-u-23-24"
                        :value (str state)}]]]]]))

(defn display-settings []
  (let [modals (:modals @app/app-state)]
    (if (zero? (count modals)) (swap! app/app-state assoc :modals [#'settings-modal]))))

(defn update-controls [_ _ prev cur]
  (when-not (= (:cloud prev) (:cloud cur))
    (go
      (swap! app/app-state assoc :endpoint
        (<! (GET (:cloud cur)))))))

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
