(ns ^:figwheel-always brainiac.components.controls
    (:require-macros [cljs.core.async.macros :refer [go]]
                     [brainiac.macros :refer [<?]])
    (:require [rum.core :as rum]
              [schema.core :as s :include-macros true]
              [brainiac.utils :as u]
              [brainiac.appstate :as app]
              [brainiac.schema :as schema]
              [brainiac.search :as search]
              [cljs.reader :refer [read-string]]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :refer [<!]]))

(def ENTER 13)

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
  (-> k
      clj->js
      (#(re-matches #"^\." %))))

(defn value-has-mappings [[k v]]
  (not (empty? (-> v :mappings))))

(defn load-indices []
  (let [state @app/app-state
        endpoint (str "http://" (-> state :endpoint :selected :host) "/_mapping")]
      (go
        (let [indices (->> (<! (GET endpoint))
                          (filter (complement key-starts-with-dot))
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

(defn cloud-import [v]
  (swap! app/app-state assoc-in [:cloud] (select-keys v [:instance-mapper :suggesters]))
  ;(swap! app/app-state assoc-in [:cloud :field-mappers] (-> raw :docType :fieldMappers))
  (swap! app/app-state assoc-in [:endpoint :selected] (select-keys v [:host :index :doc-type]))
  (go
    (>! search/req-chan {:size 0})
    (>! search/req-chan {})))


(defn check-and-save-field-input [e field-state settings-state]
  (let [new-value (-> e .-target .-value)]
    (if-not (s/check (get-in schema/StateSchema settings-state) new-value)
      (write-new-field-input new-value field-state settings-state)
      (swap! app/app-state assoc-in field-state new-value))))

(defn check-field-input [e field-state settings-state]
  (let [new-value (-> e .-target .-value)]
    (swap! app/app-state assoc-in field-state new-value)))

(defn settings-modal []
  (let [state @app/app-state]
    [:div
      [:form {:className "pure-form pure-form-stacked"
              :style {:width "60vw"}
              :action "#"}

        [:div {:className "pure-g"}
          (let [field-path '(:settings :fields :host)
                saved-path '(:endpoint :selected :host)
                field-val (get-in state field-path)
                saved-val (get-in state saved-path)]
            [:label {:className "pure-u-1-3"} "host"
              [:input {:className "pure-u-23-24"
                        :type "text"
                        :value (or field-val saved-val)
                        :style {:borderColor (if field-val "red" (when saved-val "green"))}
                        :onChange #(check-and-save-field-input % field-path saved-path)}]])

          (let [field-path '(:settings :fields :index)
                saved-path '(:endpoint :selected :index)
                field-val (get-in state field-path)
                saved-val (get-in state saved-path)

                suggestions (when (or field-val (empty? saved-val))
                              (->> state
                                  :endpoint
                                  :indices
                                  keys
                                  (map name)
                                  (filter #(if field-val (.startsWith % field-val) true))
                                  (take 3)))]
            [:label {:className "pure-u-1-3"} "index"
              [:input {:className "pure-u-23-24"
                        :type "text"
                        :style {:borderColor (if field-val "red" (when saved-val "green"))}
                        :value (or field-val saved-val)
                        :onChange #(check-and-save-field-input % field-path saved-path)
                        :onKeyDown #(when (= ENTER (-> % .-keyCode))
                                      (do
                                        (.preventDefault %)
                                        (when-let [f-suggestion (first suggestions)]
                                          (write-new-field-input f-suggestion field-path saved-path))))}]
              (for [i suggestions]
                  [:a {:style {:marginRight "1em"
                                :textDecoration "underline"}
                        :onClick #(write-new-field-input i field-path saved-path)}
                    i])])

          (let [field-path '(:settings :fields :doc-type)
                saved-path '(:endpoint :selected :doc-type)
                field-val (get-in state field-path)
                saved-val (get-in state saved-path)

                selected-index (-> state :endpoint :selected :index keyword)
                doc-types (if selected-index (-> state :endpoint :indices selected-index) [])]
            [:label {:className "pure-u-1-3"} "doc_type"
              [:select {:className "pure-u-1"
                        :style {:borderColor (when saved-val "green")}
                        :value saved-val
                        :onChange change-doc-type}
                  [:option]
                  (for [doc-type doc-types]
                    [:option {:value doc-type} (name doc-type)])]])

         [:div {:className "pure-g"}
             [:div {:className "pure-u-1"}
               [:ul nil (for [sh (-> state :cs-config :endpoint-shortcuts)]
                         [:li
                           [:a {:onClick #(cloud-import sh)} (:name sh)]])]]]


         [:div {:className "pure-u-1"}
           [:label {:className "pure-u-1"} "state"
             [:textarea {:rows 6
                         :className "pure-u-1"
                         :value (str state)}]]]]]]))

(defn display-settings []
  (let [modals (:modals @app/app-state)]
    (when (zero? (count modals))
      (go (->> (<! (GET "/edn/config.edn" {:response-format :edn}))
              (swap! app/app-state assoc :cs-config)))
      (swap! app/app-state assoc :modals [#'settings-modal]))))

(defn toggle-source [e]
  (swap! app/app-state assoc :display-source (-> @app/app-state :display-source not)))

(rum/defc controls-component < rum/reactive []
  (let [state (rum/react app/app-state)]
    [:div
      [:a {:className (str "action fa fa-refresh" (when (:loading state) " rotating"))
            :onClick (when-not (:loading state) #(go (>! search/req-chan {})))}]
      [:a {:className "action fa fa-newspaper-o"
            :style (when-not (-> state :cloud :instance-mapper) {:color :gray
                                                                  :cursor :auto})
            :onClick #(when (-> state :cloud :instance-mapper) (toggle-source %))}]
      [:a {:className "action fa fa-gear"
            :onClick display-settings}]]))

(defn update-controls [_ _ prev cur]
  (go
    (when-not (u/=in prev cur :endpoint :selected)
      (do
        (search/get-mapping)
        (when-not (u/=in prev cur :endpoint :selected :host) (load-indices))))))

(defn setup-watcher []
  (when-not (:endpoint @app/app-state) (display-settings))
  (remove-watch app/app-state :controls-watcher)
  (add-watch app/app-state :controls-watcher update-controls))
