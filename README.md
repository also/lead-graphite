```clojure
(let [opts {:start 1 :end 1000}
      sin (graphite/sinFunction opts "sin")
      random (graphite/randomWalkFunction opts "random")]
  (prn sin)
  (prn random))
```
