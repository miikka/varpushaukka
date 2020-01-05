(set-env!
 :resource-paths #{"src/clj" "test/clj"}
 :dependencies '[[org.clojure/clojure "1.10.1" :scope "provided"]

                 [clj-http "3.10.0"]
                 [clj-time "0.15.2"]
                 [com.cemerick/pomegranate "1.1.0"]
                 [com.rpl/specter "1.1.3"]
                 [com.stuartsierra/component "0.4.0"]
                 [compojure "1.6.1"]
                 [funcool/clojure.jdbc "0.9.0"]
                 [hiccup "1.0.5"]
                 [honeysql "0.9.0"]
                 [mvxcvi/clj-pgp "0.10.1"]
                 [org.slf4j/slf4j-simple "1.7.30"]
                 [org.xerial/sqlite-jdbc "3.28.0"]
                 [reloaded.repl "0.2.4"]
                 [ring "1.8.0"]
                 [ring-jetty-component "0.3.1"]

                 [metosin/boot-alt-test "0.3.2" :scope "test"]])

(require '[metosin.boot-alt-test :refer [alt-test]])

(deftask dev []
  (comp
   (watch)
   (alt-test)))

(deftask run
  [a atom bool "Produce Atom instead of HTML."]
  (with-pass-thru _
    (require 'varpushaukka.report)
    ((resolve 'varpushaukka.report/-main) atom)))
