(ns lead-graphite.core
  (:require [lead-graphite.graphite :as graphite]
            [lead.functions :as fns]
            [lead.jetty-api :as server]))

(defn -main
  [& args]
    (fns/register-fns-from-namespace 'lead-graphite.graphite)
    (server/run))
