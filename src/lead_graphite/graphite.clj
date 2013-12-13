(ns lead-graphite.graphite
  (:require [lead.functions :as fns]
            [lead.parser :as parser])
  (:import org.python.util.PythonInterpreter
           [org.python.core PyFunction Py PyList PyInteger PyDictionary PyUnicode PyObject imp]))

(defn call [function & args]
  (.__call__ function (Py/javas2pys (to-array args))))

(def interp (PythonInterpreter.))
(def datetime-module (imp/importName "datetime" true))
(def datetime-class (.__getattr__ datetime-module "datetime"))
(defn datetime [timestamp] (-> datetime-class (.__getattr__ "fromtimestamp") (call timestamp)))
(def getargspec (partial call (-> (imp/importName "inspect" true) (.__getattr__ "getargspec"))))

(.exec interp (slurp (clojure.java.io/resource "patch_graphite.py")))

(def functions-module (.get interp "functions"))

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

(def TimeSeries (.__getattr__ functions-module "TimeSeries"))

(defn series->TimeSeries [t]
  (let [result (apply call TimeSeries ((juxt :name #(PyInteger. (:start %)) #(PyInteger. (:end %)) #(PyInteger. (:step %)) :values #(if-let [cf (:consolidation-fn %)] cf "average")) t))]
    (.__setattr__ result "pathExpression" (PyUnicode. (:name t)))
    result))

(def evaluateTarget
  (proxy [PyObject] []
    (__call__ [thread-state context name]
      (prn context name)
      (let [target (Py/tojava name String)
            opts {:start (/ (.getTime (get context "startTime")) 1000)
                  :end (/ (.getTime (get context "endTime")) 1000)}
            serieses (fns/run (parser/parse target) opts)]
        (prn serieses)
        (PyList. (map series->TimeSeries serieses))))))

(.__setattr__ functions-module "evaluateTarget" evaluateTarget)

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
