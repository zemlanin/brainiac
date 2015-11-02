(ns ^:figwheel-always brainiac.components.products
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [brainiac.utils :as u]
              [brainiac.ajax :refer [GET]]
              [clojure.string :as str]
              [cljs.core.async :refer [>! chan close!]]
              [cljs.pprint :refer [pprint]]
              [brainiac.appstate :as app]))

(defn pretty-component [{id :id image :image n :name url :url :as data}]
  [:div {:key id
          :className "pure-u-1-3"}
    [:div {:style {:fontFamily :monospace
                    :background :white
                    :boxShadow "0.5em 0.5em 2em 0.1em gray"
                    :margin "1em"
                    :padding "0.5em"}}
      (if url
        [:a {:href url
              :target :_blank}
          id
          [:br]
          [:img {:src image :height 200 :width 200}]]
        [:div
          id
          [:br]
          [:img {:src image}]])]])

(defn es-source-component [{{id :_id} :es :as data}]
  (let [pdata (-> data pprint with-out-str (str/split "\n"))]
    [:div {:key id
            :className "pure-u-1-3"}
      [:div {:style {:fontFamily :monospace
                      :background :white
                      :boxShadow "0.5em 0.5em 2em 0.1em gray"
                      :margin "1em"
                      :padding "0.5em"}}
        (for [line pdata]
          [:span
            (str/replace line #"^\s+" "\u00a0")
            [:br]])]]))

(rum/defc products-component < rum/reactive []
  (let [state (rum/react app/app-state)
        search-result (-> state :search-result)
        instances (-> state :instances)
        total (-> state :search-result :hits :total)
        display-fn ;(case (-> state :display-fn)
                    ;  :source es-source-component
                    ;  es-source-component)
                    (if (and (-> state :display-pretty) (-> state :cloud :instance-mapper :url))
                      pretty-component
                      es-source-component)]
    [:div {:className "pure-g"}
      [:h3 {:className "pure-u-1"} (if total (str "documents / " total) "documents")]
      [:div (map display-fn instances)]]))

(defn instance-mapper [state]
  (let [ch (chan 1)
        hits (-> state :search-result :hits :hits)
        hits-map (into {} (for [h hits] [(-> h :_id js/parseInt) h]))
        ids (keys hits-map)]
    (go
      (if-let [url (-> state :cloud :instance-mapper :url)]
        (>! ch (let [cloud-response (<! (GET (str url "?ids=" (str/join "," ids))))]
                  (for [r (-> cloud-response :instances)]
                    (assoc r :es (get hits-map (:id r))))))
        (>! ch (map #(assoc {} :es %) hits)))
        )
    ch))

(defn instance-mapper-watcher [_ _ prev cur]
  (when-not (u/=in prev cur :search-result :hits)
    (go
      (swap! app/app-state assoc :instances
        (<! (instance-mapper cur))))))

(defn setup-watcher []
  (remove-watch app/app-state :products-watcher)
  (add-watch app/app-state :products-watcher instance-mapper-watcher))
