(ns ^:figwheel-always brainiac.components.controls
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
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
      (swap! app/app-state assoc-in [:endpoint :doc-type] (keyword new-doc-type))))

(defn settings-modal []
  (let [{endpoint :endpoint mappings :mappings :as state} @app/app-state]
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
                    :value (first (:hosts endpoint))}]]

        [:label {:className "pure-u-1-3"} "index"
          [:input {:className "pure-u-23-24"
                    :type "text"
                    :value (:index endpoint)}]]

        [:label {:className "pure-u-7-24"} "doc_type"
          [:select {:className "pure-u-23-24"
                    :value ""
                    :onChange change-doc-type}
              (when-not (:doc-type endpoint) [:option])
            (for [[doc-type type-data] (into [] (:doc_types endpoint))]
              [:option doc-type])]]

        [:div {:className "pure-u-1"}
          [:label {:className "pure-u-1"} "state"
            [:textarea {:type "text"
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
  (remove-watch app/app-state :controls-watcher)
  (add-watch app/app-state :controls-watcher update-controls))
