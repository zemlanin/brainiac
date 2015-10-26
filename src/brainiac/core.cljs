(ns ^:figwheel-always brainiac.core
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [brainiac.search :as search]
              [brainiac.components.filters :as filters]
              [brainiac.components.controls :as controls]
              [brainiac.components.appliedFilters :as appliedFilters]
              [brainiac.components.products :as products]))

(enable-console-print!)

(search/setup-watcher)
(controls/setup-watcher)

(rum/mount
  (filters/filters-component)
  (.getElementById js/document "filters"))

(rum/mount
  (appliedFilters/appliedFilters-component)
  (.getElementById js/document "appliedFilters"))

(rum/mount
  (products/products-component)
  (.getElementById js/document "products"))

(rum/mount
  (controls/controls-component)
  (.getElementById js/document "controls"))

(defn on-js-reload [])
