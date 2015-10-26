(ns ^:figwheel-always brainiac.appstate)

(defonce app-state (atom {:applied {}
                          :search-result {}
                          :mappings {}}))
