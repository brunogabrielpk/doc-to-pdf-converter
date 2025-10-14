(ns doc-converter.core
  "Main entry point for the document converter application"
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [doc-converter.handler :refer [app]]
            [doc-converter.converter :as converter]
            [doc-converter.db :as db])
  (:gen-class))

(defn -main
  "Starts the web server on the specified port.

   The -main function is special - it's the entry point when running
   the application as a standalone program."
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]

    ;; Initialize the database
    (db/init-db!)

    ;; Start the document converter service
    (converter/start-office-manager!)

    ;; Add shutdown hook to clean up resources
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (println "Shutting down...")
                                 (converter/stop-office-manager!))))

    (println (format "Starting server on port %d..." port))
    (println "Visit http://localhost:3000 to use the application")

    ;; Start the web server
    (run-jetty app {:port port :join? true})))
(ns doc-converter.core)
