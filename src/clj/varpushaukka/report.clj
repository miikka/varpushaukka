(ns varpushaukka.report
  (:require [clj-pgp.core :as pgp]
            [clj-time.core :as t]
            [clojure.spec :as s]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5]]
            [varpushaukka.core :as core]
            [varpushaukka.clojars :as clojars]
            [varpushaukka.observation-store :as store]
            [clojure.string :as string])
  (:import java.util.UUID))

(def trusted-keys
  {:miikka "0753C3DA748EDA91AAB1E35E8005E0EBBCB7E306"
   :tommi "CE5BB16BACD86273DDCCF4EDF0CF4EB328DB8A2C"
   :dakrone "6CA9E3B29F28FEA86750B6BE9D6465D43ACECAE0"
   :weavejester "BADEB0BCBB5010BB9F4FF46A87FCFC781A1B513D"
   :juho "5DDBC3343CEC9A95AB0272C9094224808950366D"
   :juho-old "90C64334F2837C72E451E774D75ACF21A58EB7F3"
   :john "7A3B4BAFCACB8A4A79C6BF760D6F7DAC0EEE5E66"
   :ztellman "38F1E8B7E2E292D778EF599336A89F3F7E4C9221"
   :michaelblume "63C3CECD25B41844B2DA62269A5C13A73D896FB6"
   :tuukka "CB9A22BD6ACE5AB93B7ED258CF9EAFB495E8CD12"
   :jarppe "F85B2B2447359A9518AF49FF4606566DB6C65805"})

(def trusted-groups
  {:metosin #{:miikka :tommi :juho :juho-old :john :tuukka :jarppe}
   :juho #{:juho :juho-old}})

;; This needs some kind of TOFU mode
(def packages
  {"clj-http"          :dakrone
   "hiccup"            :weavejester
   "mvxcvi/clj-pgp"    :no-key
   "primitive-math"    :ztellman
   "byte-streams"      :ztellman
   "clj-tuple"         :ztellman
   "potemkin"          :ztellman
   "honeysql"          :michaelblume})

(def groups
  {"metosin" {:group :metosin}
   "deraen"  {:group :juho}
   "miikka"  :miikka})

(defn keys-for-keyspec
  [keyspec]
  (if (map? keyspec)
    (map trusted-keys (get trusted-groups (:group keyspec)))
    (when-let [key (get trusted-keys keyspec)] [key])))

(defn check-coordinates
  [coordinates keyspec]
  (let [package (first coordinates)]
    (if-let [keys (keys-for-keyspec keyspec)]
      (if-let [artifact (core/get-artifact coordinates)]
        (let [{:keys [status pub-key key-id]} (core/check-artifact artifact keys)]
          (merge
           {:package package
            :version (second (first artifact))
            :status status}
           (when pub-key {:signed-by (pgp/key-info pub-key)})
           (when key-id {:signed-by-id key-id})))
        {:package package :status :not-signed})
      {:package package :status :no-keys-specified})))

(defn check-and-record-coordinates
  [coordinates keyspec]
  (doto (check-coordinates coordinates keyspec)
    (store/record)))

(defn check-group
  [group keyspec]
  (for [package (clojars/get-group-artifacts group)]
    (-> package
        (clojars/package->coordinates)
        (check-and-record-coordinates keyspec))))

(defn check-watched-group
  [group]
  (check-group group (get groups group)))

(defn check-groups [] (mapcat (partial apply check-group) groups))

(defn check-package
  [package keyspec]
  (check-and-record-coordinates (clojars/get-coordinates package) keyspec))

(defn check-packages [] (map (partial apply check-package) packages))

(defn check-all
  []
  (concat
   (check-packages)
   (check-groups)))

(defn strip-email
  [user-id]
  (first (string/split user-id #"<")))

(defn sort-packages [package-status] (sort-by :package package-status))

(defn get-user-id
  [package]
  (when-let [signed-by (get package :signed-by)]
    (some->> (get signed-by :user-ids)
             (map strip-email)
             (some #(and (not (string/blank? %)) %))
             (string/trim))))

(defn get-key-id [package]
  (some #(get-in package %) [[:signed-by :key-id] [:signed-by-id]]))

(def +key-search-url+ "https://sks-keyservers.net/pks/lookup?op=vindex&search=0x")

(defn package-table
  [package-status]
  [:table
   (for [package (sort-packages package-status)
         :let [key-id (get-key-id package)]]
     [:tr {:style (condp contains? (:status package)
                    #{:trusted :no-keys-specified} ""
                    #{:not-signed} "color: darkorange"
                    "color: red")}
      [:td (str (:package package))]
      [:td (:version package)]
      [:td (:status package)]
      [:td (get-user-id package)]
      [:td (when key-id [:a {:href (str +key-search-url+ key-id)} key-id])]])])

(defn pprint-report
  [package-status]
  (html5
   [:head [:title "package signature status report"]]
   [:body
    [:h1 "package signature status report"]
    (package-table package-status)]))

(defn db-report
  []
  (pprint-report (store/get-package-status)))

(def feed-id
  "urn:uuid:fd788648-c061-4e26-afb0-3b00279f5a7a")

(defn- uuid-v4 []
  (UUID/randomUUID))

(defn pprint-atom
  [package-status]
  (let [now (str (t/now))]
    (hiccup/html
     {:mode :xml}
     [:feed {:xmlns "http://www.w3.org/2005/Atom"}
      [:title "package signature status report"]
      [:updated now]
      [:id feed-id]
      [:entry
       [:title (str "report for " now)]
       [:id (str "urn:uuid:" (uuid-v4))]
       [:updated now]
       [:content {:type "xhtml"}
        [:div {:xmlns "http://www.w3.org/1999/xhtml"}
         (package-table package-status)]]
       [:author
        [:name "varpushaukka"]]]])))

(defn -main [atom]
  (store/initialize-db)
  (println ((if atom pprint-atom pprint-report) (check-all))))
