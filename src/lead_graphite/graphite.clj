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

(.exec interp
"
import sys
import imp
import os
sys.path.append('graphite-web/webapp')

def make_module(name, **kwargs):
  mod = imp.new_module(name)
  mod.__dict__.update(kwargs)
  sys.modules[name] = mod

import graphite
make_module('django')
make_module('django.conf', settings={'TIME_ZONE': 'UTC'})
make_module('graphite.logger', log=lambda *x: None)
make_module('graphite.events', models=None)
make_module('graphite.render.glyph', format_units=None)
make_module('graphite.storage', STORE=None)
make_module('graphite.readers', FetchInProgress=None)

#from django.core.exceptions import ObjectDoesNotExist
make_module('django.core')
make_module('django.core.exceptions', ObjectDoesNotExist=None)

#from django.contrib.auth.models import User
make_module('django.contrib')
make_module('django.contrib.auth')
make_module('django.contrib.auth.models', User=None)

#from graphite.account.models import Profile
make_module('graphite.account')
make_module('graphite.account.models', Profile=None)
# from graphite.render.evaluator import evaluateTarget
make_module('graphite.render.evaluator', evaluateTarget=lambda *x: None)
make_module('pyparsing')
make_module('pytz')
os.environ['READTHEDOCS'] = 'true'
from graphite.util import timestamp
del os.environ['READTHEDOCS']
from graphite.render import functions
")

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
