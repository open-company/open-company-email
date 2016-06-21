(defproject open-company-email "0.0.1-SNAPSHOT"
  :description "OpenCompany Email Service"
  :url "https://opencompany.com/"
  :license {
    :name "Mozilla Public License v2.0"
    :url "http://www.mozilla.org/MPL/2.0/"
  }

  ;; All profile dependencies
  :dependencies [
    [org.clojure/clojure "1.9.0-alpha7"] ; Lisp on the JVM http://clojure.org/documentation
    [environ "1.0.3"] ; Environment settings from different sources https://github.com/weavejester/environ
    [com.taoensso/timbre "4.5.0-RC2"] ; Logging https://github.com/ptaoussanis/timbre
    [raven-clj "1.4.2"] ; Interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    [com.stuartsierra/component "0.3.1"] ; Component Lifecycle
    [amazonica "0.3.61"] ;; A comprehensive Clojure client for the entire Amazon AWS api https://github.com/mcohen01/amazonica
  ]

  ;; All profile plugins
  :plugins [
    [lein-environ "1.0.3"] ; Get environment settings from different sources https://github.com/weavejester/environ
  ]
)
