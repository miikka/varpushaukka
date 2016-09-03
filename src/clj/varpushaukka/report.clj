(ns varpushaukka.report
  (:require [clj-pgp.core :as pgp]
            [clj-time.core :as t]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5]]
            [varpushaukka.core :as core]
            [varpushaukka.clojars :as clojars]
            [clojure.string :as string]
            [clj-uuid :as uuid]))

(def trusted-keys
  {:miikka "0753C3DA748EDA91AAB1E35E8005E0EBBCB7E306"
   :tommi "CE5BB16BACD86273DDCCF4EDF0CF4EB328DB8A2C"
   :dakrone "6CA9E3B29F28FEA86750B6BE9D6465D43ACECAE0"
   :weavejester "BADEB0BCBB5010BB9F4FF46A87FCFC781A1B513D"
   :juho "5DDBC3343CEC9A95AB0272C9094224808950366D"
   :juho-old "90C64334F2837C72E451E774D75ACF21A58EB7F3"
   :john "7A3B4BAFCACB8A4A79C6BF760D6F7DAC0EEE5E66"
   :danlentz "2C3F427B0CA72D7DA254B66A5C1BFB40B110BC3D"})

(def trusted-groups
  {:metosin #{:miikka :tommi :juho :juho-old :john}})

(def packages
  {"clj-http"                  :dakrone
   "hiccup"                    :weavejester
   "danlentz/clj-uuid"         :danlentz
   "mvxcvi/clj-pgp"            :no-key})

(def groups
  {"metosin" {:group :metosin}})

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

(defn check-group
  [group keyspec]
  (for [package (clojars/get-group-artifacts group)]
    (-> package
        (clojars/package->coordinates)
        (check-coordinates keyspec))))

(defn check-watched-group
  [group]
  (check-group group (get groups group)))

(defn check-groups [] (mapcat (partial apply check-group) groups))

(defn check-package
  [package keyspec]
  (check-coordinates (clojars/get-coordinates package) keyspec))

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
  (if-let [signed-by (get package :signed-by)]
    (some->> (get signed-by :user-ids)
             (map strip-email)
             (some #(and (not (string/blank? %)) %))
             (string/trim))
    (when-let [signed-by-id (get package :signed-by-id)]
      (str "key ID " signed-by-id))))

(defn package-table
  [package-status]
  [:table
   (for [package (sort-packages package-status)]
     [:tr {:style (condp contains? (:status package)
                    #{:trusted :no-keys-specified} ""
                    #{:not-signed} "color: darkorange"
                    "color: red")}
      [:td (str (:package package))]
      [:td (:version package)]
      [:td (:status package)]
      [:td (get-user-id package)]])])

(defn pprint-report
  [package-status]
  (html5
   [:head [:title "package signature status report"]]
   [:body
    [:h1 "package signature status report"]
    (package-table package-status)]))

(def feed-id
  "urn:uuid:fd788648-c061-4e26-afb0-3b00279f5a7a")

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
       [:id (str "urn:uuid:" (uuid/v4))]
       [:updated now]
       [:content {:type "xhtml"}
        [:div {:xmlns "http://www.w3.org/1999/xhtml"}
         (package-table package-status)]]
       [:author
        [:name "varpushaukka"]]]])))

(defn -main [atom]
  (println ((if atom pprint-atom pprint-report) (check-all))))
