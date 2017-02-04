(ns user
  (:require [reloaded.repl :refer [system init start stop go reset reset-all]]
            [varpushaukka.server :refer [new-web-server]]))

(reloaded.repl/set-init! #(new-web-server))
