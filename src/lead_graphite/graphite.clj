(ns lead-graphite.graphite
  (:require [lead.functions :as fns])
  (:import org.python.util.PythonInterpreter
           [org.python.core PyFunction Py PyList PyInteger PyDictionary PyUnicode imp]))

(defn call [function & args]
  (.__call__ function (Py/javas2pys (to-array args))))

(def interp (PythonInterpreter.))
(def datetime-module (imp/importName "datetime" true))
(def datetime-class (.__getattr__ datetime-module "datetime"))
(defn datetime [timestamp] (-> datetime-class (.__getattr__ "fromtimestamp") (call timestamp)))
(def getargspec (partial call (-> (imp/importName "inspect" true) (.__getattr__ "getargspec"))))

(defn tojava [o attr cls]
  (if-let [v (.__getattr__ o attr)]
    (Py/tojava v cls)))

(defn TimeSeries->series [t]
  {:name   (tojava t "name" String)
   :values t
   :start  (tojava t "start" Number)
   :end    (tojava t "end" Number)
   :step (tojava t "step" Number)
   :consolidation-fn (tojava t "consolidationFunc" String)
   :values-per-point (tojava t "valuesPerPoint" Number)})

(.exec interp (slurp (clojure.java.io/resource "patch_graphite.py")))

(def functions-module (.get interp "functions"))

(def TimeSeries (.__getattr__ functions-module "TimeSeries"))

(defn series->TimeSeries [t]
  (let [result (apply call TimeSeries ((juxt :name #(PyInteger. (:start %)) #(PyInteger. (:end %)) #(PyInteger. (:step %)) :values #(if-let [cf (:consolidation-fn %)] cf "average")) t))]
    (.__setattr__ result "pathExpression" (PyUnicode. (:name t)))
    result))

(def py-functions (.__getattr__ functions-module "SeriesFunctions"))

(defn load-args [opts args]
  (map (fn [arg]
         (if (fns/series-source? arg)
           (PyList. (map series->TimeSeries (fns/load-serieses arg opts)))
           arg))
       args))

(defn opts->requestContext [opts]
  (PyDictionary. {(PyUnicode. "startTime") (datetime (:start opts)) (PyUnicode. "endTime") (datetime (:end opts))}))

(doseq [[function-name function] py-functions]
  (intern *ns* (with-meta (symbol function-name) {:args "???" :complicated true})
          (fn [opts & args]
            (map TimeSeries->series
                 (apply call function (opts->requestContext opts) (load-args opts args))))))
