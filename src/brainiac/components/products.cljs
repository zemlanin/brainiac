(ns ^:figwheel-always brainiac.components.products
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [rum.core :as rum]
              [brainiac.utils :as u]
              [clojure.string :as str]
              [cljs.core.async :refer [>! chan close!]]
              [cljs.pprint :refer [pprint]]
              [brainiac.appstate :as app]))

(defn product-component [{id :id n :name :as data}]
  [:div {:key id
          :className "pure-u-1-3"}
    id " / " n])

(defn es-source-component [{id :id :as data}]
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
        display-fn (case (-> state :display-fn)
                      :source es-source-component
                      es-source-component)]
    [:div {:className "pure-g"}
      [:h3 {:className "pure-u-1"} (if total (str "documents / " total) "documents")]
      [:div (map display-fn instances)]]))

(defn instance-mapper [state]
  (let [ch (chan 1)
        hits (-> state :search-result :hits :hits)]
    (go
      (>! ch
        (map
          #(assoc (:_source %) :id (:_id %))
          hits)))
    ch))

(defn instance-mapper-watcher [_ _ prev cur]
  (when-not (u/=in prev cur :search-result :hits)
    (go
      (swap! app/app-state assoc :instances
        (<! (instance-mapper cur))))))

(defn setup-watcher []
  (remove-watch app/app-state :products-watcher)
  (add-watch app/app-state :products-watcher instance-mapper-watcher))
