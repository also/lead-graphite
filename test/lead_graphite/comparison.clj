(ns lead-graphite.comparison
  (:require [simple-check.core :as sc]
            [simple-check.generators :as gen]
            [simple-check.properties :as prop]
            [simple-check.clojure-test :as ct :refer (defspec)]
            [lead.functions :as fns]
            [lead.builtin-functions :as lead]
            [lead-graphite.graphite :as graphite]))

(def num-tests 100)
(def epsilon 10e-6)

(defrecord TestSeriesSource [serieses]
  fns/SeriesSource
  (load-serieses [this opts] serieses))

(defn create-series [[name start step values]]
  {:name name
   :values values
   :start start
   :step step
   :end (+ start (* step (count values)))})

(def gen-series
  (gen/fmap create-series (gen/tuple gen/string-alpha-numeric gen/pos-int gen/s-pos-int (gen/vector gen/int))))

(def gen-serieses-source
  (gen/fmap ->TestSeriesSource (gen/vector gen-series 2 10)))

(defn close-enough? [serieses-a serieses-b]
  (every? (fn [[series-a series-b]]
            (every? (fn [[a b]] (or (= a b) ; handles nil
                                    (< (Math/abs (- (double a) (double b))) epsilon)))
                    (map vector (:values series-a) (:values series-b))))
          (map vector serieses-a serieses-b)))

(defn run [s lead-f graphite-f args]
  (let [args (cons s args)
        opts {:start (-> s :serieses first :start) :end (-> s :serieses first :end)}
        lead-source (fns/function->source "test" lead-f args)
        graphite-source (fns/function->source "test" graphite-f args)
        lead-result (fns/load-serieses lead-source opts)
        graphite-result (fns/load-serieses graphite-source opts)]
    [lead-result graphite-result]))

(defn compare-source [s lead-f graphite-f args]
  (try
    (let [[lead-result graphite-result] (run s lead-f graphite-f args)]
      (close-enough? lead-result graphite-result))
    (catch Exception e
      (.printStackTrace e)
      false)))

(defn compare-serieses [lead-f graphite-f args]
  (prop/for-all [s gen-serieses-source
                 args (apply gen/tuple args)]
    (compare-source s lead-f graphite-f args)))

(def comparisons
  '[[scale-serieses     scale gen/int]
    [increment-serieses offset gen/int]
    [avg-serieses       avg]
    [min-serieses       minSeries]
    [max-serieses       maxSeries]
    [sum-serieses       sumSeries]])

(doseq [[lead-f graphite-f & args] comparisons]
  (println graphite-f)
  (prn (sc/quick-check num-tests
    (compare-serieses
      (ns-resolve 'lead.builtin-functions lead-f)
      (ns-resolve 'lead-graphite.graphite graphite-f)
      (map (comp deref resolve) args))))
  (println))
