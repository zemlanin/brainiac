(ns ^:figwheel-always brainiac.components.products
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [brainiac.utils :as u]
              [brainiac.ajax :refer [GET]]
              [clojure.string :as str]
              [cljs.core.async :refer [>! chan close!]]
              [cljs.pprint :refer [pprint]]
              [brainiac.appstate :as app]))

(defn pretty-component [{id :id image :image n :name url :url images :images :as data}]
  [:div {:key id
          :className "pure-u-1-3"}
    [:div {:style {:fontFamily :monospace
                    :background :white
                    :boxShadow "0.5em 0.5em 2em 0.1em gray"
                    :margin "1em"
                    :padding "0.5em"}}
      [:div {:className "pure-g"}
        [:div {:className "pure-u-1"}
          (if url
            [:a {:href url :target :_blank} [:b n]]
            [:b n])
          [:i " (" id ")"]]
        [:div {:className "pure-u-1-3"
                :style {:textAlign :center}}
          (if url
            [:a {:href url :target :_blank}
              [:img {:src image
                      :style {:maxHeight 200 :maxWidth "100%"}}]]
            [:img {:src image
                      :style {:maxHeight 200 :maxWidth "100%"}}])
          (when (< 1 (count images))
            [:ul {:className "thumbs-list"} (for [i images]
              [:li [:img {:src i}]])])]
        [:div {:className "pure-u-1-24"}]
        [:div {:className "pure-u-15-24"
                :style {
                  :maxHeight 200
                  :overflow :auto
                  :WebkitTransform "translateZ(0)"}}
          (:description data)]]]])

(defn es-source-component [{{id :_id} :es :as data}]
  (let [pdata (-> data pprint with-out-str (str/split "\n"))]
    [:div {:key id
            :className "pure-u-1-3"}
      [:div {:style {:fontFamily :monospace
                      :background :white
                      :boxShadow "0.5em 0.5em 2em 0.1em gray"
                      :margin "1em"
                      :overflow :auto
                      :WebkitTransform "translateZ(0)"
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
                    (if (and (-> state :display-source) (-> state :cloud :instance-mapper))
                      es-source-component
                      pretty-component)]
    [:div {:className "pure-g"}
      [:h3 {:className "pure-u-1"} (if total (str "documents / " total) "documents")]
      [:div (map display-fn instances)]]))

(defn instance-mapper [state]
  (let [ch (chan 1)
        hits (-> state :search-result :hits :hits)
        hits-map (into {} (for [h hits] [(-> h :_id js/parseInt) h]))
        ids (keys hits-map)]
    (go
      (if-let [url (-> state :cloud :instance-mapper)]
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
