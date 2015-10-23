(ns ^:figwheel-always sho.search
    (:require [om.core :as om :include-macros true]
              [sho.appstate :as app]))

(defn setup-watcher []
  (remove-watch app/app-state :search-watcher)
  (add-watch app/app-state :search-watcher (fn [_ _ _ _] (.log js/console "searching"))))
