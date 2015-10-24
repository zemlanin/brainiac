(ns ^:figwheel-always brainiac.components.appliedFilters
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]))

(defn appliedFilters-component [{applied :applied :as data}]
  (om/component
    (html
      [:div
        [:h3 "appliedFilters"]
        [:ul
          (map (fn [[k x]] (when x [:li {:key k} k])) applied)]])))
