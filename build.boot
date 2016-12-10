(set-env!
 :resource-paths #{"src/clj" "test/clj"}
 :dependencies '[[org.clojure/clojure "1.9.0-alpha14" :scope "provided"]
                 [clj-http "2.2.0"]
                 [clj-time "0.12.0"]
                 [com.cemerick/pomegranate "0.3.1"]
                 [com.rpl/specter "0.13.0"]
                 [mvxcvi/clj-pgp "0.8.3"]
                 [hiccup "1.0.5"]
                 [funcool/clojure.jdbc "0.9.0"]
                 [honeysql "0.8.1"]
                 [org.xerial/sqlite-jdbc "3.15.1"]
                 [org.clojars.miikka/clj-uuid "0.1.7-SNAPSHOT"]
                 [miikka/pinkeys "0.1.0" :scope "test"]
                 [metosin/boot-alt-test "0.2.1" :scope "test"]])

(require '[metosin.boot-alt-test :refer [alt-test]]
         '[miikka.boot-pinkeys :refer [pinkeys]])

(deftask dev []
  (comp
   (watch)
   (alt-test)))

(require 'varpushaukka.report)
(deftask run
  [a atom bool "Produce Atom instead of HTML."]
  (with-pass-thru _ (varpushaukka.report/-main atom)))
