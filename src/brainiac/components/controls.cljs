(ns ^:figwheel-always brainiac.components.controls
    (:require-macros [cljs.core.async.macros :refer [go go-loop]])
    (:require [rum.core :as rum]
              [brainiac.utils :as u]
              [brainiac.appstate :as app]
              [brainiac.search :as search]
              [cljs.reader :refer [read-string]]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :as async :refer [<!]]))

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

(defn cloud-import [v]
  (swap! app/app-state assoc :cloud
    (select-keys v [:instance-mapper :suggesters :replace-filter-types :facet-counters :builtin-filters :script-filters :hidden-filters :ignore-filters]))
  ;(swap! app/app-state assoc-in [:cloud :field-mappers] (-> raw :docType :fieldMappers))
  (swap! app/app-state assoc-in [:endpoint :selected] (select-keys v [:host :index :doc-type]))
  (go
    (>! search/req-chan {:size 0})
    (>! search/req-chan {})))

(defn settings-modal []
  (let [state @app/app-state
        show-state (-> state :settings :show-state)
        notifications (-> state :notifications)]

    [:div {:className "pure-g"}
      [:div {:className "pure-u-1-3"}
          [:ul nil (for [sh (-> state :cs-config :endpoint-shortcuts)]
                      [:li
                        [:a {:onClick #(cloud-import sh)} (:name sh)]])]]

      [:div {:className "pure-u-2-3 notifications"}
          [:b {:class "title"} "notifications"]
          [:ul nil
            (for [n (->> notifications :unread (sort-by :id))]
              [:li
                [:b nil (:text n)]])
            (for [n (->> notifications :read (sort-by :id >) (take 5))]
              [:li
                [:span nil (:text n)]])]]

      [:div {:className "pure-u-1"}
        [:label {:className "pure-u-1"}
          [:a {:on-click #(swap! app/app-state assoc-in [:settings :show-state] (not show-state))} "state"]
          (when show-state
             [:textarea {:rows 6
                         :className "pure-u-1"
                         :value (str state)}])]]]))

(defonce notify-chan (async/chan))

(defn display-settings []
  (let [modals (:modals @app/app-state)]
    (when (zero? (count modals))
      (go
        (let [cs-config (<! (GET "/edn/config.edn" {:response-format :edn}))]
          (swap! app/app-state assoc :cs-config cs-config)
          (when-let [notifications-url (-> cs-config :notifications :url)]
            (async/>! notify-chan {:mark-as-read true}))))
      (swap! app/app-state assoc :modals [#'settings-modal]))))

(defn toggle-source [e]
  (swap! app/app-state assoc :display-source (-> @app/app-state :display-source not)))

(rum/defc controls-component < rum/reactive []
  (let [state (rum/react app/app-state)
        loading (-> state :loading)
        instance-mapper (-> state :cloud :instance-mapper)
        unread (-> state :notifications :unread count)
        display-source (-> state :display-source)]
    [:div
      [:a {:className (str "action fa fa-refresh" (when loading " rotating"))
            :onClick (when-not loading #(go (>! search/req-chan {})))}]
      [:a {:className "action fa fa-gear"
            :onClick display-settings}
        (when (pos? unread) [:span {:class "notify-dot"} unread])]
      [:a {:className (str "action fa " (if display-source "fa-code" "fa-newspaper-o"))
            :style (when-not instance-mapper {:color :gray
                                              :cursor :auto})
            :onClick #(when instance-mapper (toggle-source %))}]]))

(defn handle-notifications [notifications mark-as-read]
  (let [state @app/app-state
        read (if mark-as-read notifications (-> state :notifications :read))
        unread (filter #(not (some (partial = %) read)) notifications)]
    (swap! app/app-state assoc :notifications {:unread unread :read read})))

(defn update-controls [_ _ prev cur]
  (go
    (when-not (u/=in prev cur :endpoint :selected)
      (search/get-mapping)
      (when-not (u/=in prev cur :endpoint :selected :host) (load-indices)))))

(defn setup-watcher []
  (display-settings)
  (go-loop []
    (<! (async/timeout 600000))
    (>! notify-chan {:mark-as-read false})
    (recur))
  (go-loop []
    (when-let [{mark-as-read :mark-as-read} (<! notify-chan)]
      (when-let [notifications-url (-> @app/app-state :cs-config :notifications :url)]
        (handle-notifications (<! (GET notifications-url {:response-format :edn})) mark-as-read))
      (recur)))
  (add-watch app/app-state :controls-watcher update-controls))
