(ns varpushaukka.clojars
  (:require [clj-http.client :as client]))

(defn- http-get [url]
  (-> url
      (client/get {:accept :edn :as :clojure})
      (:body)))

(defn get-package-info
  [package]
  (http-get (str "https://clojars.org/api/artifacts/" package)))


(defn get-group-artifacts
  [group]
  (http-get (str "https://clojars.org/api/groups/" group)))

(defn package->coordinates
  [description]
  [(symbol (:group_name description) (:jar_name description))
   (:latest_release description)])

(defn get-coordinates
  [package]
  (-> package (get-package-info) (package->coordinates)))
