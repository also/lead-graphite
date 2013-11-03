(ns lead-graphite.comparison
  (:require [simple-check.core :as sc]
            [simple-check.generators :as gen]
            [simple-check.properties :as prop]
            [lead.functions :as fns]
            [lead.builtin-functions :as lead]
            [lead-graphite.graphite :as graphite]))

(defrecord TestSeriesSource [serieses]
  fns/SeriesSource
  (load-serieses [this opts] serieses))

(defn create-source [[name start step values]]
  (TestSeriesSource. [{:name name
                       :values values
                       :start start
                       :step step
                       :end (+ start (* step (count values)))}]))

(def gen-series-source
  (gen/fmap create-source (gen/tuple gen/string-alpha-numeric gen/pos-int gen/pos-int (gen/vector gen/int))))

(defn close-enough? [a b]
  (every? (fn [[a b]] (= (double a) (double b))) (map vector a b)))

(defn comparison-prop [lead-f graphite-f & args]
  (prop/for-all [s gen-series-source]
    (try
      (let [args (conj args s)
            opts {:start (-> s :serieses first :start) :end (-> s :serieses first :end)}
            lead-source (fns/function->source "test" lead-f args)
            graphite-source (fns/function->source "test" graphite-f args)
            lead-result (fns/load-serieses lead-source opts)
            graphite-result (fns/load-serieses graphite-source opts)]
        (close-enough? (-> lead-result first :values) (-> graphite-result first :values)))
      (catch Exception e
        (.printStackTrace e)
        false))))

(prn (sc/quick-check 1000 (comparison-prop #'lead/scale-serieses #'graphite/scale 2)))
