(ns varpushaukka.core
  (:require
   [cemerick.pomegranate.aether :as aether]
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.java.shell :as shell]
   [clojure.java.io :as io]
   [clj-pgp.signature :as pgp-sig]
   [clj-pgp.keyring :as keyring]
   [clj-pgp.core :as pgp]))

(def local-repo "m2")
(def keyring-path (str (System/getProperty "user.home") "/.gnupg/pubring.gpg"))

(def repositories
  {"central" "https://repo1.maven.org/maven2/"
   "clojars" "https://clojars.org/repo/"})

(defn get-package-info
  [package]
  (let [description (-> (str "https://clojars.org/api/artifacts/" package)
                        (client/get {:accept :edn :as :clojure})
                        (:body))]
    description))

(defn package->coordinates
  [description]
  [(symbol (:group_name description) (:jar_name description))
   (:latest_release description)])

(defn get-artifact
  [package]
  (let [coords (-> package (get-package-info) (package->coordinates))
        coords-with-asc [coords (concat coords [:extension "jar.asc"])]]
    (aether/resolve-artifacts :coordinates coords-with-asc
                              :local-repo local-repo
                              :repositories repositories)))

;; XXX(miikka) If I understand this correctly, this is comparing 64-bit key IDs,
;; which is not secure.
(defn key= [key1 key2] (= (pgp/key-id key1) (pgp/key-id key2)))

(defn is-subkey-of?
  [sub-key master-key]
  (some #(key= % master-key) (iterator-seq (.getSignatures sub-key))))

(defn find-master
  [keyring pub-key keys]
  (some #(let [master-key (keyring/get-public-key keyring %)]
           (and master-key
                (or (key= pub-key master-key) (is-subkey-of? pub-key master-key))
                master-key))
        keys))

(defn load-keyring []
  (keyring/load-public-keyring (io/file keyring-path)))

(defn check-artifact
  [artifacts keys]
  (let [[jar-file asc-file] (map (comp :file meta) artifacts)
        keyring (load-keyring)
        signature (first (pgp/decode-signatures (io/file asc-file)))]
    (when-let [pub-key (keyring/get-public-key keyring (pgp/key-id signature))]
      (let [master-key (find-master keyring pub-key keys)]
        (and master-key
             (pgp-sig/verify (io/file jar-file) signature pub-key)
             master-key)))))

(defn check-package
  [package keyspec]
  (-> (get-artifact package)
      (check-artifact keyspec)))
