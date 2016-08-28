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

(def trusted-keys
  {:miikka "0753C3DA748EDA91AAB1E35E8005E0EBBCB7E306"})

(def local-repo "m2")

(def repositories
  {"central" "http://repo1.maven.org/maven2/"
   "clojars" "https://clojars.org/repo/"})

(def keyring-path "/Users/miikka/.gnupg/pubring.gpg")

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

(defn is-subkey-of?
  [sub-key master-key]
  (let [master-id (pgp/key-id master-key)]
    (some #(= (pgp/key-id %) master-id) (iterator-seq (.getSignatures sub-key)))))

(defn key= [key1 key2] (= (pgp/key-id key1) (pgp/key-id key2)))

(defn find-master
  [keyring pub-key keys]
  (some #(let [master-key (keyring/get-public-key keyring %)]
           (and (or (key= pub-key master-key) (is-subkey-of? pub-key master-key))
                master-key))
        keys))

(defn load-keyring []
  (keyring/load-public-keyring (io/file keyring-path)))

(defn check-artifact
  [artifacts keys]
  (let [[jar-file asc-file] (map (comp :file meta) artifacts)
        keyring (load-keyring)
        signature (first (pgp/decode-signatures (io/file asc-file)))
        pub-key (keyring/get-public-key keyring (pgp/key-id signature))
        master-key (find-master keyring pub-key keys)]
    (and master-key
         (pgp-sig/verify (io/file jar-file) signature pub-key)
         master-key)))

(defn check-package
  [package keyspec]
  (-> (get-artifact package)
      (check-artifact keyspec)))
