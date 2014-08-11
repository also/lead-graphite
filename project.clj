(defproject com.ryanberdeen/lead-graphite "0.1.0-SNAPSHOT"
  :dependencies [
                 [com.ryanberdeen/lead "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.5.1"]
                 [org.python/jython-standalone "2.7-b2"]
                 [reiddraper/simple-check "0.5.2" :scope "test"]]
  :aliases {"graphite" ["with-profile" "graphite" "test"]}
  :profiles {:graphite {:jvm-opts ["-Dpython.path=graphite-web/webapp"]}})
