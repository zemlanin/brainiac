(ns ^:figwheel-always brainiac.core
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [brainiac.components.filters :as filters]
              [brainiac.appstate :as app]
              [brainiac.search :as search]
              [brainiac.components.appliedFilters :as appliedFilters]
              [brainiac.components.products :as products]))

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
