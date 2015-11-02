(ns ^:figwheel-always brainiac.schema
    (:require [schema.core :as s :include-macros true]
              [brainiac.appstate :as app]))

(def CloudEndpointSchema {:index  s/Str
                          :doc-type s/Str
                          ; TODO: check for running ES instance
                          :host s/Str})

(def StateSchema {
  :applied {s/Keyword {:type s/Keyword
                        :value s/Any}}
  :search-result {s/Keyword s/Any
                  :hits {s/Keyword s/Any
                          :total s/Num
                          :hits [s/Any]}}
  :mappings {s/Keyword s/Any}
  :cloud s/Str
  :endpoint {:selected {:index (s/conditional
                                  empty? s/Str
                                  #(contains? (-> @app/app-state :endpoint :indices) (keyword %)) s/Str)
                          :doc-type s/Str
                          :docTypes {s/Keyword s/Any}
                          ; TODO: check for running ES instance
                          :host (s/conditional #(.endsWith % ":9200") s/Str)}
              :indices {s/Keyword [s/Keyword]}}
  :modals [s/Any]
  :instances [{s/Keyword s/Any}]
  :settings {(s/optional-key :fields) (s/maybe {(s/optional-key :cloud) s/Str
                                                (s/optional-key :index) s/Str
                                                (s/optional-key :host) s/Str})}})

(defn validate-schema []
  (when-let [err (s/check StateSchema @app/app-state)]
    (println err)))

(defn setup-watcher []
  (validate-schema)
  (remove-watch app/app-state :schema-watcher)
  (add-watch app/app-state :schema-watcher validate-schema))
