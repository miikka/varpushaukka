(ns varpushaukka.test-util
  (:require [clojure.test :refer [use-fixtures]]
            [clojure.spec.test :as stest]))

(defn instrument-ns [a-ns f]
  (stest/instrument)
  (f)
  (stest/unstrument))

(defmacro instrument-this-ns! []
  `(use-fixtures :once (partial instrument-ns *ns*)))
