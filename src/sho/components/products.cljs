(ns ^:figwheel-always sho.components.products
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]))

(defn products-component [{products :products :as data}]
  (om/component
    (html [:h3 "products"
        [:ul (map (fn [[k x]] (when x [:li k])) products)]])))
