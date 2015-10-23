(ns ^:figwheel-always sho.core
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [sho.components.filters :as filters]
              [sho.appstate :as app]
              [sho.search :as search]
              [sho.components.appliedFilters :as appliedFilters]
              [sho.components.products :as products]))

(enable-console-print!)

(search/setup-watcher)

(om/root
  filters/filters-component
  app/app-state
  {:target (. js/document (getElementById "filters"))})

(om/root
  appliedFilters/appliedFilters-component
  app/app-state
  {:target (. js/document (getElementById "appliedFilters"))})

(om/root
  products/products-component
  app/app-state
  {:target (. js/document (getElementById "products"))})

(defn on-js-reload [])
