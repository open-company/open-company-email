(ns oc.email.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

(defn- bool
  "Handle the fact that we may have true/false strings, when we want booleans."
  [val]
  (boolean (Boolean/valueOf val)))

;; ----- System -----

(defonce intro? (bool (or (env :intro ) false)))

;; ----- Auth -----

(defonce passphrase (env :open-company-auth-passphrase))

;; ----- URLS ------

(defonce auth-server-port (Integer/parseInt (or (env :auth-server-port) "3003")))
(defonce auth-server-url (or (env :auth-server-url) (str "http://localhost:" auth-server-port)))
(defonce change-server-port (Integer/parseInt (or (env :change-server-port) "3006")))
(defonce change-server-url (or (env :change-server-url) (str "http://localhost:" change-server-port)))
(defonce storage-server-port (Integer/parseInt (or (env :storage-server-port) "3001")))
(defonce storage-server-url (or (env :storage-server-url) (str "http://localhost:" storage-server-port)))
(defonce web-url (or (env :oc-web-url) "http://localhost:3559"))

;; ----- Logging (see https://github.com/ptaoussanis/timbre) -----

(defonce log-level (if-let [ll (env :log-level)] (keyword ll) :info))

;; ----- Sentry -----

(defonce dsn (or (env :sentry-dsn) (env :open-company-sentry-email) false))
(defonce sentry-release (or (env :release) ""))
(defonce sentry-deploy (or (env :deploy) ""))
(defonce sentry-debug  (boolean (or (bool (env :sentry-debug)) (#{:debug :trace} log-level))))
(defonce sentry-env (or (env :environment) "local"))
(defonce sentry-config {:dsn dsn
                        :release sentry-release
                        :deploy sentry-deploy
                        :debug sentry-debug
                        :environment sentry-env})

;; ----- AWS -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))
(defonce aws-creds {:access-key aws-access-key-id
                    :secret-key aws-secret-access-key})
(defonce aws-endpoint (env :aws-endpoint))

;; ----- AWS SQS -----

(defonce aws-sqs-email-queue (env :aws-sqs-email-queue))

;; ----- Email -----

(defonce email-from-domain (env :email-from-domain))
(defonce email-digest-prefix (env :email-digest-prefix))
(defonce email-images-prefix (env :email-images-prefix))

;; ----- Filestack -----

(defonce filestack-api-key (env :filestack-api-key))

;; ----- Default brand color -----

(defonce default-brand-color {:primary {:rgb {:r (or (env :primary-brand-color-r) 104)
                                              :g (or (env :primary-brand-color-g) 51)
                                              :b (or (env :primary-brand-color-b) 241)}
                                        :hex (or (env :primary-brand-color-hex) "#6833F1")}
                              :secondary {:rgb {:r (or (env :secondary-brand-color-r) 254)
                                                :g (or (env :secondary-brand-color-g) 254)
                                                :b (or (env :secondary-brand-color-b) 254)}
                                          :hex (or (env :secondary-brand-color-hex) "#FFFFFF")}})
