(ns varpushaukka.stats
  "Check the signature status for a large number of packages at once.

  Usage:
  (def res (check-libs [\"org.clojure/clojure\" \"metosin/kekkonen\"]))
  (summarize res)
  (report \"results.txt\" res)"
  (:require [clojure.string :as string]
            [clj-pgp.core :as pgp]
            [varpushaukka.core :as core]
            [com.rpl.specter :refer [transform MAP-VALS]]))

(defn load-list [path]
  (-> (slurp path)
      (string/split-lines)))

(defn save-list [path lines]
  (->> (string/join lines "\n")
       (spit path)))

(defn to-symbol [x]
  (let [[a b] (string/split x #"/" 2)]
    (if b
      (symbol a b)
      (symbol a))))

(defn libs->coordinates [libs]
  (for [lib libs] [(to-symbol lib) "LATEST"]))

(defn check-libs [libs]
  (let [coords (libs->coordinates libs)]
    (for [coord coords]
      (merge (core/check-package coord #{}) {:package (first coord)}))))

(defn check-libs-from-file
  [path]
  (check-libs (load-list path)))

(defn report [path results]
  "Export a CSV file that contains the results."
  (save-list path
             (for [result results]
               (format "%s,%s,%s,%s\n"
                       (:package result)
                       (:status resultset-seq)
                       (str (some-> (:pub-key result) (pgp/hex-fingerprint)))
                       (str (:key-id result))))))

(defn summarize [results]
  {:counts (transform MAP-VALS count (group-by :status results))})

(defn unknown-keys [results]
  (set (keep :key-id results)))
