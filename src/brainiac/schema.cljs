(ns ^:figwheel-always brainiac.schema
    (:require [schema.core :as s :include-macros true]
              [brainiac.appstate :as app]))

(def StateSchema {
  :applied {s/Keyword s/Bool}
  :search-result {s/Keyword s/Any
                  :hits {s/Keyword s/Any
                          :total s/Num
                          :hits [s/Any]}}
  :mappings s/Any
  :cloud s/Str
  :endpoint {s/Keyword s/Any}
  :modals [s/Any]})

(defn validate-schema []
  (when-let [err (s/check StateSchema @app/app-state)]
    (println err)))

(defn setup-watcher []
  (validate-schema)
  (remove-watch app/app-state :schema-watcher)
  (add-watch app/app-state :schema-watcher validate-schema))
