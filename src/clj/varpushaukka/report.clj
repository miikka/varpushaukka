(ns varpushaukka.report
  (:require [clj-pgp.core :as pgp]
            [clj-time.core :as t]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5]]
            [varpushaukka.core :as core]
            [clojure.string :as string]
            [clj-uuid :as uuid]))

(def trusted-keys
  {:miikka "0753C3DA748EDA91AAB1E35E8005E0EBBCB7E306"
   :tommi "CE5BB16BACD86273DDCCF4EDF0CF4EB328DB8A2C"
   :dakrone "6CA9E3B29F28FEA86750B6BE9D6465D43ACECAE0"
   :weavejester "BADEB0BCBB5010BB9F4FF46A87FCFC781A1B513D"
   :juho "5DDBC3343CEC9A95AB0272C9094224808950366D"
   :danlentz "2C3F427B0CA72D7DA254B66A5C1BFB40B110BC3D"})

(def trusted-groups
  {:metosin #{:miikka :tommi :juho}})

(def packages
  {"metosin/kekkonen"          {:group :metosin}
   "metosin/ring-swagger"      {:group :metosin}
   "metosin/scjsv"             {:group :metosin}
   "metosin/vega-tools"        {:group :metosin}
   "metosin/loiste"            {:group :metosin}
   "metosin/potpuri"           {:group :metosin}
   "metosin/schema-tools"      {:group :metosin}
   "metosin/metosin-common"    {:group :metosin}
   "metosin/boot-alt-test"     {:group :metosin}
   "metosin/compojure-api"     {:group :metosin}
   "clj-http"                  :dakrone
   "hiccup"                    :weavejester
   "danlentz/clj-uuid"         :danlentz
   "mvxcvi/clj-pgp"            :no-key})

(defn keys-for-keyspec
  [keyspec]
  (if (map? keyspec)
    (map trusted-keys (get trusted-groups (:group keyspec)))
    (when-let [key (get trusted-keys keyspec)] [key])))

(defn check-packages
  []
  (for [[package keyspec] packages]
    (if-let [keys (keys-for-keyspec keyspec)]
      (let [artifact (core/get-artifact package)
            pub-key (core/check-artifact artifact keys)]
        {:package package
         :version (second (first artifact))
         :status (if pub-key :trusted :untrusted)
         :signed-by (when pub-key (pgp/key-info pub-key))})
      {:package package :status :no-keys-specified})))

(defn strip-email
  [user-id]
  (first (string/split user-id #"<")))

(defn package-table
  [package-status]
  [:table
   (for [package package-status]
     [:tr {:style (if (= :untrusted (:status package)) "color: red" "")}
      [:td (:package package)]
      [:td (:version package)]
      [:td (:status package)]
      [:td (strip-email (get-in package [:signed-by :user-ids 0] "-"))]])])

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

(defn -main [] (println (pprint-atom (check-packages))))
