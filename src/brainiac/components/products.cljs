(ns ^:figwheel-always brainiac.components.products
    (:require-macros [cljs.core.async.macros :refer [go]]
                     [brainiac.macros :refer [<?]])
    (:require [rum.core :as rum]
              [brainiac.utils :as u]
              [brainiac.ajax :refer [GET]]
              [brainiac.search :as search]
              [clojure.string :as str]
              [cljs.core.async :refer [>! chan close! to-chan]]
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
                :style {:maxHeight 200
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

(defn stats-modal []
  (let [state @app/app-state
        field (:displayed-stats state)
        {buckets :buckets others :sum_other_doc_count error-bound :doc_count_error_upper_bound} (-> state :search-result :facet-counters field)
        max-count (-> buckets first :doc_count)]
    [:div {}
      [:h3 {:style {:margin 0}} (name field) " stats"]
      [:table {}
        [:tbody {}
          (for [{key :key count :doc_count} buckets]
            [:tr {}
              [:td {:style {:paddingTop "0.5em"}} key]
              [:td {:style {:paddingLeft "1em"
                            :paddingTop "0.5em"}}
                [:div {:style {:color :white
                                :background-color "#00A500"}} count]]
              [:td {:style {:paddingTop "0.5em"
                            :width "40vw"}}
                ; count
                [:div {:style {:width (str (* 100 (/ count max-count)) "%")
                                :color "#00A500"
                                :background-color "#00A500"}} "."]]])]]

      (when-not (zero? others)
        [:div {:style {:paddingTop "0.5em"}}
          [:b {}
            "+" others
            (when-not (zero? error-bound)
              [:sup {} (str " ±" error-bound)])
            " others"]])]))

(defn display-stats [field]
  (let [modals (:modals @app/app-state)]
    (when (zero? (count modals))
      (swap! app/app-state assoc :displayed-stats field)
      (swap! app/app-state assoc :modals [#'stats-modal]))))

(rum/defc products-component < rum/reactive []
  (let [state (rum/react app/app-state)
        search-result (-> state :search-result)
        instances (-> state :instances)
        loading (-> state :loading)
        total (-> state :search-result :hits :total)
        facet-counters (-> state :search-result :facet-counters)
        size (count instances)
        display-fn (if (-> state :display-source)
                      es-source-component
                      #(if (:mapped %) (pretty-component %) (es-source-component %)))]
    [:div {:className "pure-g"}
      [:h3 {:className "pure-u-1"
            :style {:marginTop 0}}
        (if total (str "total: " total) "documents")]
      (when-not (empty? facet-counters)
        [:h4 {:className "pure-u-1"
              :style {:marginTop 0}}
          (for [[n {buckets :buckets others :sum_other_doc_count error-bound :doc_count_error_upper_bound}] facet-counters]
            [ (if (> (count buckets) 1) :a :span)
              {:onClick (when (> (count buckets) 1) #(display-stats n))}
              (name n) ": "
              (+ others (count buckets))
              (when-not (zero? error-bound)
                [:sup {} (str " ±" error-bound)])])])
      [:div (map display-fn instances)]
      (when
        (and
          size
          (> size 0)
          (> total size))
        [:a {:className "pure-u-1 fa"
              :onClick (when-not loading #(go (>! search/req-chan {:size (+ size 24)})))
              :style {:textAlign :center
                      :fontSize "3em"}}
          (if loading
            [:span
              {:className "fa fa-refresh rotating"}]
            [:span
              {:className "fa fa-ellipsis-v"}])])]))

(defn wrap-es [v]
  {:es v})

(defn extend-with-instances [hits instances]
  (map
    #(if-let [instance (get instances (js/parseInt (:_id %)))]
        (merge instance {:es % :mapped true})
        {:es %})
    hits))

(defn assoc-es [hits-map]
  (fn [{id :id :as v}] (assoc v :es (get hits-map id))))

(defn instance-mapper [state]
  (let [hits (-> state :search-result :hits :hits)]
    (if (= 0 (count hits))
      (to-chan [[]])
      (let [ch (chan 1)
            hits-map (into {} (for [h hits] [(-> h :_id js/parseInt) h]))
            ids (keys hits-map)]
        (go
          (if-let [url (-> state :cloud :instance-mapper)]
            (let [{instances :instances} (try (<? (GET (str url "?ids=" (str/join "," ids))))
                                          (catch js/Error e
                                            nil))]
              (>! ch
                (if instances
                  (->> instances
                    (map (juxt :id identity))
                    (into (sorted-map))
                    (extend-with-instances hits))
                  :error)))
            (>! ch (map wrap-es hits))))

        ch))))

(defn instance-mapper-watcher [_ _ prev cur]
  (when-not (u/=in prev cur :search-result :hits)
    (go
      (swap! app/app-state assoc :loading true)
      (swap! app/app-state assoc :instances (->> cur :search-result :hits :hits (map wrap-es)))
      (let [instances (<! (instance-mapper cur))]
        (when-not (= :error instances)
          (swap! app/app-state assoc :instances instances))
        (swap! app/app-state dissoc :loading)))))


(defn setup-watcher []
  (remove-watch app/app-state :products-watcher)
  (add-watch app/app-state :products-watcher instance-mapper-watcher))
