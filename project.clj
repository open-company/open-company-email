(defproject open-company-email "0.2.0-SNAPSHOT"
  :description "OpenCompany Email Service"
  :url "https://github.com/open-company/open-company-email"
  :license {
    :name "GNU Affero General Public License Version 3"
    :url "https://www.gnu.org/licenses/agpl-3.0.en.html"
  }

  :min-lein-version "2.7.1"

  ;; JVM memory
  :jvm-opts ^:replace ["-Xms512m" "-Xmx3072m" "-server"]

  ;; All profile dependencies
  :dependencies [
    ;; Lisp on the JVM http://clojure.org/documentation
    [org.clojure/clojure "1.10.0"]
    ;; HTML rendering https://github.com/weavejester/hiccup
    [hiccup "2.0.0-alpha2"]
    ;; Async programming tools https://github.com/ztellman/manifold
    [manifold "0.1.9-alpha3"]
    ;; Namespace management https://github.com/clojure/tools.namespace
    ;; NB: org.clojure/tools.reader is pulled in by oc.lib
    [org.clojure/tools.namespace "0.3.0-alpha4" :exclusions [org.clojure/tools.reader]] 

    ;; Library for OC projects https://github.com/open-company/open-company-lib
    [open-company/lib "0.16.38-alpha"]
    ;; In addition to common functions, brings in the following common dependencies used by this project:
    ;; Component - Component Lifecycle https://github.com/stuartsierra/component
    ;; Schema - Data validation https://github.com/Prismatic/schema
    ;; Timbre - Pure Clojure/Script logging library https://github.com/ptaoussanis/timbre
    ;; Amazonica - A comprehensive Clojure client for the AWS API https://github.com/mcohen01/amazonica
    ;; Raven - Interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    ;; Cheshire - JSON encoding / decoding https://github.com/dakrone/cheshire
    ;; clj-time - Date and time lib https://github.com/clj-time/clj-time
    ;; Environ - Get environment settings from different sources https://github.com/weavejester/environ
    ;; soup - Clojure wrapper for jsoup HTML parser https://github.com/mfornos/clojure-soup
    ;; clj-http - Clojure HTTP client https://github.com/dakrone/clj-http
  ]

  ;; All profile plugins
  :plugins [
    [lein-environ "1.1.0"] ; Get environment settings from different sources https://github.com/weavejester/environ
  ]

  :profiles {

    ;; QA environment and dependencies
    :qa {
      :env {
        :open-company-auth-passphrase "this_is_a_qa_secret" ; JWT secret
      }
      :plugins [
        ;; Linter https://github.com/jonase/eastwood
        [jonase/eastwood "0.3.5"]
        ;; Static code search for non-idiomatic code https://github.com/jonase/kibit
        [lein-kibit "0.1.6" :exclusions [org.clojure/clojure]]
      ]
    }

    ;; Dev environment and dependencies
    :dev [:qa {
      :env ^:replace {
        :open-company-auth-passphrase "this_is_a_dev_secret" ; JWT secret
        :aws-access-key-id "CHANGE-ME"
        :aws-secret-access-key "CHANGE-ME"
        :aws-endpoint "us-east-1"
        :aws-sqs-email-queue "https://sqs.REGION.amazonaws.com/CHANGE/ME"
        :email-from-domain "change.me"
        :email-digest-prefix "[Localhost] "
        :email-images-prefix "https://open-company-assets-non-prod.s3.amazonaws.com"
        :intro "true"
        :log-level "debug"
      }
      :dependencies [
        [hickory "0.7.1"] ; HTML as data https://github.com/davidsantiago/hickory
      ]
      :plugins [
        ;; Check for code smells https://github.com/dakrone/lein-bikeshed
        ;; NB: org.clojure/tools.cli is pulled in by lein-kibit
        [lein-bikeshed "0.5.1" :exclusions [org.clojure/tools.cli]] 
        ;; Runs bikeshed, kibit and eastwood https://github.com/itang/lein-checkall
        [lein-checkall "0.1.1"]
        ;; pretty-print the lein project map https://github.com/technomancy/leiningen/tree/master/lein-pprint
        [lein-pprint "1.2.0"]
        ;; Check for outdated dependencies https://github.com/xsc/lein-ancient
        [lein-ancient "0.6.15"]
        ;; Catch spelling mistakes in docs and docstrings https://github.com/cldwalker/lein-spell
        [lein-spell "0.1.0"]
        ;; Dead code finder https://github.com/venantius/yagni
        [venantius/yagni "0.1.7" :exclusions [org.clojure/clojure]]
        ;; Autotest https://github.com/jakemcc/lein-test-refresh
        [com.jakemccrary/lein-test-refresh "0.23.0"]
      ]  
    }]

    :repl-config [:dev {
      :dependencies [
        [org.clojure/tools.nrepl "0.2.13"] ; Network REPL https://github.com/clojure/tools.nrepl
        [aprint "0.1.3"] ; Pretty printing in the REPL (aprint ...) https://github.com/razum2um/aprint
      ]
      ;; REPL injections
      :injections [
        (require '[aprint.core :refer (aprint ap)]
                 '[clojure.stacktrace :refer (print-stack-trace)]
                 '[clojure.string :as s]
                 '[clj-time.core :as t]
                 '[clj-time.format :as f]
                 '[cheshire.core :as json]
                 '[hiccup.core :as h]
                 '[hickory.core :as hickory]
                 '[oc.email.config :as c])
      ]
    }]

    ;; Production environment
    :prod {}

    :uberjar {
      :aot :all
    }
  }

  :repl-options {
    :welcome (println (str "\n" (slurp (clojure.java.io/resource "oc/assets/ascii_art.txt")) "\n"
                      "OpenCompany Email Service REPL\n"))
    :init-ns dev
  }

  :aliases {
    "build" ["with-profile" "prod" "do" "clean," "uberjar"] ; clean and build code
    "repl" ["with-profile" "+repl-config" "repl"]
    "start" ["run" "-m" "oc.email.app"] ; start a development server
    "start!" ["with-profile" "prod" "do" "start"] ; start a server in production
    "spell!" ["spell" "-n"] ; check spelling in docs and docstrings
    "bikeshed!" ["bikeshed" "-v" "-m" "120"] ; code check with max line length warning of 120 characters
    "ancient" ["ancient" ":all" ":allow-qualified"] ; check for out of date dependencies
  }

  ;; ----- Code check configuration -----

  :eastwood {
    ;; Disable some linters that are enabled by default
    ;; contant-test - just seems mostly ill-advised, logical constants are useful in something like a `->cond` 
    ;; wrong-arity - unfortunate, but it's failing on 3/arity of sqs/send-message
    ;; implicit-dependencies - uhh, just seems dumb
    :exclude-linters [:constant-test :wrong-arity :implicit-dependencies]
    ;; Enable some linters that are disabled by default
    :add-linters [:unused-namespaces :unused-private-vars] ; :unused-locals]

    ;; Exclude testing namespaces
    :tests-paths ["test"]
    :exclude-namespaces [:test-paths]
  }

  :main oc.email.app
)