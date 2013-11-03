(ns lead-graphite.comparison
  (:require [simple-check.core :as sc]
            [simple-check.generators :as gen]
            [simple-check.properties :as prop]
            [lead.functions :as fns]
            [lead.builtin-functions :as lead]
            [lead-graphite.graphite :as graphite]
            [clojure.pprint :as pp]))

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

(def gen-values (gen/vector gen/int))
(def gen-name gen/string-alpha-numeric)
(def gen-series
  (gen/fmap create-series (gen/tuple gen-name gen/pos-int gen/s-pos-int gen-values)))

; prior to https://github.com/graphite-project/graphite-web/commit/ae9c076ff46c1e716e23585d6e7adc390ad00c58
; graphite had a very inefficient lcm function. testing many non-overlapping series will take forever.
(def gen-independent-serieses-source
  (gen/fmap ->TestSeriesSource (gen/vector gen-series 2 10)))

(def gen-range (gen/fmap (fn [[a b]] [a (+ a b)]) (gen/tuple gen/s-pos-int gen/s-pos-int)))
(def gen-name-and-values (gen/tuple gen-name gen-values))
(def gen-range-and-valueses (gen/tuple gen-range (gen/vector gen-name-and-values 2 10)))

(defn create-overlapping-serieses [[[start end] valueses]]
  (map (fn [[name values]]
         {:name name
          :values values
          :start start
          :end end
          :step (if (seq values)
                  (int (Math/ceil (/ (- end start) (count values))))
                  1)})
       valueses))

(def gen-overlapping-serieses-source
  (gen/fmap (comp ->TestSeriesSource create-overlapping-serieses) gen-range-and-valueses))

; TODO gen/frequency with gen-independent-serieses-source
(def gen-serieses-source gen-overlapping-serieses-source)

(defn close-enough? [serieses-a serieses-b]
  (every? (fn [[series-a series-b]]
            (every? (fn [[a b]] (or (= a b) ; handles nil
                                    (and a b (< (Math/abs (- (double a) (double b))) epsilon))))
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
  (let [[lead-result graphite-result] (run s lead-f graphite-f args)]
    (close-enough? lead-result graphite-result)))

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
    [sum-serieses       sumSeries]
    [map-values-above-to-nil
                        removeAboveValue gen/int]
    [map-values-below-to-nil
                        removeBelowValue gen/int]])

(doseq [[lead-s graphite-s & args] comparisons]
  (println lead-s)
  (if-let [lead-f (ns-resolve 'lead.builtin-functions lead-s)]
    (if-let [graphite-f (ns-resolve 'lead-graphite.graphite graphite-s)]
      (let [resolved-args (map (comp deref resolve) args)
            prop (compare-serieses lead-f graphite-f resolved-args)
            {result :result :as m} (sc/quick-check num-tests prop)]
        (pp/pprint m)
        (if (instance? Throwable result)
          (.printStackTrace result)))
      (println "Graphite function" graphite-s "does not exist"))
    (throw (RuntimeException. (str "lead function " lead-s " does not exist"))))
  (println))

(defn find-untested-lead-fs []
  (let [all-lead-fs (fns/find-fns 'lead.builtin-functions)
        tested-lead-fs (map #(ns-resolve 'lead.builtin-functions (first %)) comparisons)]
    (remove (set tested-lead-fs) all-lead-fs)))

(defn find-unimplemented-graphite-fs []
  (let [all-graphite-fs (fns/find-fns 'lead-graphite.graphite)
        implemented-graphite-fs (map #(ns-resolve 'lead-graphite.graphite (second %)) comparisons)]
    (remove (set implemented-graphite-fs) all-graphite-fs)))

(println "Untested:")
(doseq [f (find-untested-lead-fs)]
  (println (.sym f)))

(println)

(println "Unimplemented:")
(doseq [f (find-unimplemented-graphite-fs)]
  (println (.sym f)))
