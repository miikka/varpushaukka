(ns varpushaukka.report
  (:require [clj-pgp.core :as pgp]
            [hiccup.core :as hiccup]
            [varpushaukka.core :as core]
            [clojure.string :as string]))

(def trusted-keys
  {:miikka "0753C3DA748EDA91AAB1E35E8005E0EBBCB7E306"
   :tommi "CE5BB16BACD86273DDCCF4EDF0CF4EB328DB8A2C"
   :dakrone "6CA9E3B29F28FEA86750B6BE9D6465D43ACECAE0"
   :weavejester "BADEB0BCBB5010BB9F4FF46A87FCFC781A1B513D"
   :juho "5DDBC3343CEC9A95AB0272C9094224808950366D"})

(def trusted-groups
  {:metosin #{:miikka :tommi :juho}})

(def packages
  {"metosin/kekkonen"          {:group :metosin}
   "metosin/ring-swagger"      {:group :metosin}
   "metosin/scjsv"             {:group :metosin}
   "metosin/vega-tools"        {:group :metosin}
   "metosin/loiste"            {:group :metosin}
   "clj-http"                  :dakrone
   "hiccup"                    :weavejester
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

(defn pprint-report
  [package-status]
  (hiccup/html
   [:html
    [:head [:title "package signature status report"]]
    [:body
     [:h1 "package signature status report"]
     [:table
      (for [package package-status]
        [:tr
         [:td (:package package)]
         [:td (:version package)]
         [:td (:status package)]
         [:td (strip-email (get-in package [:signed-by :user-ids 0] "-"))]])]]]))

(defn -main [] (println (pprint-report (check-packages))))
