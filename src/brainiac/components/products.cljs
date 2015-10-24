(ns ^:figwheel-always brainiac.components.products
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]))

(defn product-component [data]
  [:div {:key (:_id data)} (:_id data)])

(defn products-component [{{{total :total products :hits :as hits} :hits} :products :as data}]
  (om/component
      (html [:div
              [:h3 "products / " total]
              [:div (map product-component products)]])))
