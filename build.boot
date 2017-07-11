(set-env!
 :resource-paths #{"src/clj" "test/clj"}
 :dependencies '[[org.clojure/clojure "1.9.0-alpha17" :scope "provided"]

                 [clj-http "2.2.0"]
                 [clj-time "0.13.0"]
                 [com.cemerick/pomegranate "0.3.1"]
                 [com.rpl/specter "1.0.2"]
                 [com.stuartsierra/component "0.3.2"]
                 [compojure "1.6.0"]
                 [funcool/clojure.jdbc "0.9.0"]
                 [hiccup "1.0.5"]
                 [honeysql "0.9.0"]
                 [mvxcvi/clj-pgp "0.8.3"]
                 [org.xerial/sqlite-jdbc "3.19.3"]
                 [reloaded.repl "0.2.3"]
                 [ring "1.6.1"]
                 [ring-jetty-component "0.3.1"]

                 [miikka/pinkeys "0.1.1" :scope "test"]
                 [metosin/boot-alt-test "0.3.2" :scope "test"]])

(require '[metosin.boot-alt-test :refer [alt-test]]
         '[miikka.boot-pinkeys :refer [pinkeys]])

(deftask dev []
  (comp
   (watch)
   (alt-test)))

(deftask run
  [a atom bool "Produce Atom instead of HTML."]
  (with-pass-thru _
    (require 'varpushaukka.report)
    ((resolve 'varpushaukka.report/-main) atom)))
