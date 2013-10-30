```clojure
(let [request {"startTime" (graphite/datetime 1), "endTime" (graphite/datetime 1000)}
      sin (graphite/sinFunction request "sin")
      random (graphite/randomWalkFunction request "random")]
  (prn request)
  (prn sin)
  (prn random))
```
