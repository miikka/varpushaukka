(ns varpushaukka.observation-store
  "Keeping historical records for the package status measurements."
  (:require
   [clj-pgp.core :as pgp]
   [clojure.spec :as s]
   [jdbc.core :as jdbc]
   [honeysql.core :as sql])
  (:import
   org.sqlite.SQLiteException))

(def ^:dynamic *dbspec*
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "varpushaukka.db"})

(defmacro with-conn [sym & body]
  `(with-open [~sym (jdbc/connection *dbspec*)] ~@body))

(defn initialize-db []
  (try
    (with-conn conn
      (jdbc/execute conn [(str "CREATE TABLE observations ("
                               "id INTEGER PRIMARY KEY,"
                               "timestamp INTEGER NOT NULL,"
                               "package TEXT NOT NULL,"
                               "version TEXT,"
                               "status TEXT NOT NULL,"
                               "pubkey TEXT"
                               ")")]))
    (catch SQLiteException _)))

(defn record
  [status]
  (let [pub-key (or (get status :signed-by-id)
                    (get-in status [:signed-by :key-id]))]
    (with-conn conn
      (->> (sql/build :insert-into :observations
                      :values
                      [{:timestamp (sql/call :strftime "%s" "now")
                        :package   (str (get status :package))
                        :version   (get status :version)
                        :status    (str (get status :status))
                        :pubkey    pub-key}])
           (sql/format)
           (jdbc/execute conn)))))
