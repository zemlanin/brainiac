(ns ^:figwheel-always brainiac.components.products
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]))

(defn product-component [{_id :_id {n :name} :_source :as data}]
  [:div {:key _id
          :className "pure-u-1-3"}
    _id " / " n])

(rum/defc products-component < rum/reactive []
  (let [search-result (:search-result (rum/react app/app-state))
        {{total :total products :hits :as hits} :hits} search-result]
    [:div {:className "pure-g"}
      [:h3 {:className "pure-u-1"} (if total (str "products / " total) "products")]
      [:div (map product-component products)]]))
