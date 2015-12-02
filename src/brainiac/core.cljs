(ns ^:figwheel-always brainiac.core
    (:require [rum.core :as rum]
              [brainiac.appstate :as app]
              [brainiac.search :as search]
              [brainiac.schema :as schema]
              [brainiac.components.modals :as modals]
              [brainiac.components.filters :as filters]
              [brainiac.components.controls :as controls]
              [brainiac.components.appliedFilters :as appliedFilters]
              [brainiac.components.savedFilters :as savedFilters]
              [brainiac.components.products :as products]))

(enable-console-print!)

(modals/setup-watcher)
(search/setup-watcher)
(schema/setup-watcher)
(controls/setup-watcher)
(products/setup-watcher)

(rum/mount
  (modals/modals-component)
  (.getElementById js/document "modals"))

(rum/mount
  (savedFilters/savedFilters-component)
  (.getElementById js/document "savedFilters"))

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
