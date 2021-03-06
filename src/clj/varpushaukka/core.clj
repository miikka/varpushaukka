(ns varpushaukka.core
  (:require
   [cemerick.pomegranate.aether :as aether]
   [clojure.string :as string]
   [clojure.spec.alpha :as s]
   [clojure.java.shell :as shell]
   [clojure.java.io :as io]
   [clj-pgp.signature :as pgp-sig]
   [clj-pgp.keyring :as keyring]
   [clj-pgp.core :as pgp])
  (:import
   org.bouncycastle.openpgp.PGPPublicKey
   org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider))

(def ^:dynamic *local-repo* "m2")
(def ^:dynamic *keyring-path* (str (System/getProperty "user.home") "/.gnupg/pubring.gpg"))

(def ^:dynamic *repositories*
  {"central" "https://repo1.maven.org/maven2/"
   "clojars" "https://clojars.org/repo/"})

(s/def ::coordinate (s/tuple symbol? string?))
(s/def ::pgp-key #(instance? PGPPublicKey %))

(s/fdef get-artifact
        :args (s/cat :coords ::coordinate))
(defn get-artifact
  [coords]
  (let [coords-with-asc [coords (concat coords [:extension "jar.asc"])]]
    (try
      (aether/resolve-artifacts :coordinates coords-with-asc
                                :local-repo *local-repo*
                                :repositories *repositories*)
      ;; XXX(miikka) Need to figure out the specific exception type here.
      (catch Exception e
        nil))))

;; XXX(miikka) If I understand this correctly, this is comparing 64-bit key IDs,
;; which is not secure.
(s/fdef key= :args (s/cat :key1 ::pgp-key :key2 ::pgp-key) :ret boolean?)
(defn key= [key1 key2] (= (pgp/key-id key1) (pgp/key-id key2)))

(s/fdef is-sub-key-of?
        :args (s/cat :sub-key ::pgp-key :master-key ::pgp-key)
        :ret boolean?)
(defn is-subkey-of?
  [sub-key master-key]
  (some #(key= % master-key) (iterator-seq (.getSignatures sub-key))))

(defn verify-master-key
  [signature masterkey pubkey]
  (.init signature (BcPGPContentVerifierBuilderProvider.) masterkey)
  (.verifyCertification signature masterkey pubkey))

(defn find-master
  [keyring key]
  (if (:master-key? (pgp/key-info key))
    key
    (some (fn [signature]
            (let [key2 (keyring/get-public-key keyring (pgp/key-id signature))]
              (when (and key2 (verify-master-key signature key2 key))
                key2)))
          (iterator-seq (.getSignatures key)))))

(defn load-keyring []
  (keyring/load-public-keyring (io/file *keyring-path*)))

(defn revoked? [key] (:revoked? (pgp/key-info key)))

(defn check-artifact
  "Check whether the given artifact is signed by a trusted key."
  [artifacts keys]
  {:pre [(seq artifacts)]}
  (let [[jar-file asc-file] (map (comp :file meta) artifacts)
        keyring (load-keyring)
        key-set (set keys)]
    (if-let [signature (first (pgp/decode-signatures (io/file asc-file)))]
      (if-let [pub-key (keyring/get-public-key keyring (pgp/key-id signature))]
        (let [master-key (find-master keyring pub-key)
              trusted (and master-key
                           (contains? key-set (pgp/hex-fingerprint master-key))
                           (not (revoked? master-key))
                           (pgp-sig/verify (io/file jar-file) signature pub-key))]
          {:status (cond
                     (revoked? master-key) :revoked
                     trusted :trusted
                     :else :untrusted)
           :pub-key master-key})
        {:status :unknown-key :key-id (pgp/hex-id signature)})
      {:status :broken-signature})))

(s/def ::status #{:unsigned :untrusted :revoked :trusted :broken-signature})
(s/def ::keyspec (s/coll-of string?))
(s/def ::pub-key #(instance? PGPPublicKey %))
(s/def ::key-id string?)
(s/def ::package-status (s/keys :req-un [::status] :opt-un [::pub-key ::key-id]))

(s/fdef check-package
        :args (s/cat :package ::coordinate :keyspec ::keyspec)
        :ret ::package-status)
(defn check-package
  [package keyspec]
  (if-let [artifact (get-artifact package)]
    (check-artifact artifact keyspec)
    {:status :unsigned}))
