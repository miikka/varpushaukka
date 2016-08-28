(set-env!
 :resource-paths #{"src/clj"}
 :dependencies '[[org.clojure/clojure "1.8.0" :scope "provided"]
                 [clj-http "2.2.0"]
                 [clj-time "0.12.0"]
                 [com.cemerick/pomegranate "0.3.1"]
                 [mvxcvi/clj-pgp "0.8.3"]
                 [hiccup "1.0.5"]
                 [danlentz/clj-uuid "0.1.6"]])

(require 'varpushaukka.report)
(deftask run [] (with-pass-thru _ (varpushaukka.report/-main)))
