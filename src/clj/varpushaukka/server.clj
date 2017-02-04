(ns varpushaukka.server
  (:require [com.stuartsierra.component :as component]
            [ring.component.jetty :refer [jetty-server]]
            [varpushaukka.report :refer [db-report]]))

(defn handler [request]
  {:status 200
   :headers {"Content-type" "text/html"}
   :body (db-report)})

(def app {:handler handler})

(defn new-web-server []
  (jetty-server {:app app :port 3000}))
