(ns ^:figwheel-always brainiac.components.products
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]))

(defn product-component [data]
  [:div {:key (:_id data)} (:_id data)])

(rum/defc products-component < rum/reactive []
  (let [search-result (:search-result (rum/react app/app-state))
        {{total :total products :hits :as hits} :hits} search-result]
    [:div
      [:h3 "products / " total]
      [:div (map product-component products)]]))
