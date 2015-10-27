(ns ^:figwheel-always brainiac.schema
    (:require [schema.core :as s :include-macros true]
              [brainiac.appstate :as app]))

(def StateSchema {
  :applied {s/Keyword s/Bool}
  :search-result {s/Keyword s/Any
                  :hits {s/Keyword s/Any
                          :total s/Num
                          :hits [s/Any]}}
  :mappings {s/Keyword s/Any}
  :cloud s/Str
  :endpoint {s/Keyword s/Any
              :selected {:index (s/conditional
                                  empty? s/Str
                                  #(contains? (-> @app/app-state :endpoint :indices) (keyword %)) s/Str)
                          :doc-type s/Str
                          :host (s/conditional #(= "localhost:9200" %) s/Str)}
              :indices {s/Keyword [s/Keyword]}}
  :modals [s/Any]
  :settings {(s/optional-key :index) s/Str
              (s/optional-key :host) s/Str
              (s/optional-key :focus) s/Keyword}})

(defn validate-schema []
  (when-let [err (s/check StateSchema @app/app-state)]
    (println err)))

(defn setup-watcher []
  (validate-schema)
  (remove-watch app/app-state :schema-watcher)
  (add-watch app/app-state :schema-watcher validate-schema))
