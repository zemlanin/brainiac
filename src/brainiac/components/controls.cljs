(ns ^:figwheel-always brainiac.components.controls
    (:require-macros [cljs.core.async.macros :refer [go go-loop]])
    (:require [rum.core :as rum]
              [brainiac.utils :as u]
              [brainiac.appstate :as app]
              [brainiac.search :as search]
              [cljs.reader :refer [read-string]]
              [brainiac.ajax :refer [GET POST]]
              [cljs.core.async :as async :refer [<!]]))

(defn select-endpoint [endpoint]
  (swap! app/app-state assoc :endpoint endpoint)
  (go
    (>! search/req-chan {})))

(defn settings-modal []
  (let [state @app/app-state
        show-state (-> state :settings :show-state)
        notifications (-> state :notifications)]

    [:div {:className "pure-g"}
      [:div {:className "pure-u-1-3"}
          [:ul nil (for [endpoint (-> state :config :endpoint-shortcuts)]
                      [:li
                        [:a {:onClick #(select-endpoint endpoint)} (:name endpoint)]])]]

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
        [:a {:on-click #(swap! app/app-state assoc-in [:settings :show-state] (not show-state))}
          "state"]
        (when show-state
           [:pre {:style {:height "20em"
                          :font-family :monospace}}
              (with-out-str (cljs.pprint/pprint state))])]]))

(defonce notify-chan (async/chan))

(defn display-settings []
  (let [modals (:modals @app/app-state)]
    (when (zero? (count modals))
      (go
        (let [cs-config (<! (GET "/edn/config.edn" {:response-format :edn}))]
          (swap! app/app-state assoc :config cs-config)
          (when-let [notifications-url (-> cs-config :notifications :url)]
            (async/>! notify-chan {:mark-as-read true}))))
      (swap! app/app-state assoc :modals [#'settings-modal]))))

(defn toggle-source [e]
  (swap! app/app-state assoc :display-source (-> @app/app-state :display-source not)))

(rum/defc controls-component < rum/reactive []
  (let [state (rum/react app/app-state)
        loading (-> state :loading)
        instance-mapper (-> state :endpoint :instance-mapper)
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
    (when-not (u/=in prev cur :endpoint)
      (search/get-mapping))))

(defn setup-watcher []
  (display-settings)
  (go-loop []
    (<! (async/timeout 600000))
    (>! notify-chan {:mark-as-read false})
    (recur))
  (go-loop []
    (when-let [{mark-as-read :mark-as-read} (<! notify-chan)]
      (when-let [notifications-url (-> @app/app-state :config :notifications :url)]
        (handle-notifications (<! (GET notifications-url {:response-format :edn})) mark-as-read))
      (recur)))
  (add-watch app/app-state :controls-watcher update-controls))
