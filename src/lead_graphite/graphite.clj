(ns lead-graphite.graphite
  (:require [lead.functions :as fns]
            [lead.parser :as parser])
  (:refer-clojure :exclude [time identity alias])
  (:import org.python.util.PythonInterpreter
           [org.python.core PyFunction Py PyList PyInteger PyDictionary PyUnicode PyObject imp PyException PyFile]
           (java.io PrintWriter ByteArrayOutputStream)))

(defn call [function & args]
  (try
    (.__call__ function (Py/javas2pys (to-array args)))
    (catch PyException e
      (throw (ex-info "Error calling Python function"
                      {:python-function   (str function)
                       :python-stacktrace (let [out (ByteArrayOutputStream.)]
                                            (Py/displayException (.type e) (.value e) (.traceback e) (PyFile. out))
                                            (str out))}
                      e)))))

(def interp (PythonInterpreter.))
(def datetime-module (imp/importName "datetime" true))
(def datetime-class (.__getattr__ datetime-module "datetime"))
(defn datetime [timestamp] (-> datetime-class (.__getattr__ "fromtimestamp") (call timestamp)))
(def getargspec (partial call (-> (imp/importName "inspect" true) (.__getattr__ "getargspec"))))

(.exec interp (slurp (clojure.java.io/resource "patch_graphite.py")))

(def functions-module (.get interp "functions"))

(defn tojava [o attr cls]
  (if-let [v (try
                (.__getattr__ o attr)
                (catch Exception e nil))]
     (Py/tojava v cls)))

(defn TimeSeries->series [t]
  {:name   (tojava t "name" String)
   :path-expression (tojava t "pathExpression" String)
   :values t
   :start  (tojava t "start" Number)
   :end    (tojava t "end" Number)
   :step (tojava t "step" Number)
   :consolidation-fn (tojava t "consolidationFunc" String)
   :values-per-point (tojava t "valuesPerPoint" Number)})

(def TimeSeries (.__getattr__ functions-module "TimeSeries"))

(defn series->TimeSeries [t]
  (let [result (apply call TimeSeries ((juxt :name
                                             #(PyInteger. (:start %))
                                             #(PyInteger. (:end %))
                                             #(PyInteger. (:step %))
                                             :values
                                             #(if-let [cf (:consolidation-fn %)] cf "average"))
                                       t))]
    (.__setattr__ result "pathExpression" (PyUnicode. (or (:path-expression t) (:name t))))
    result))

(def evaluateTarget
  (proxy [PyObject] []
    (__call__ [thread-state context name]
      (let [target (Py/tojava name String)
            opts {:start (/ (.getTime (get context "startTime")) 1000)
                  :end (/ (.getTime (get context "endTime")) 1000)}
            serieses (fns/run (parser/parse target) opts)]
        (PyList. (map series->TimeSeries serieses))))))

(.__setattr__ functions-module "evaluateTarget" evaluateTarget)

(def py-functions (.__getattr__ functions-module "SeriesFunctions"))

(defn load-args [opts args]
  (map (fn [arg]
         (if (fns/lead-callable? arg)
           (PyList. (map series->TimeSeries (fns/call arg opts)))
           arg))
       args))

(defn opts->requestContext [opts]
  (PyDictionary. {(PyUnicode. "startTime") (datetime (:start opts))
                  (PyUnicode. "endTime") (datetime (:end opts))}))

(doseq [[function-name function] py-functions]
  (intern *ns* (with-meta (symbol function-name) {:args "???" :complicated true})
          (fn [opts & args]
            (let [loaded-args (load-args opts args)
                  request-context (opts->requestContext opts)
                  graphite-time-serieses (apply call function request-context loaded-args)]
              (map TimeSeries->series graphite-time-serieses)))))
