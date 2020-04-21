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

;; ----- Logging -----

(defonce log-level (or (env :log-level) :info))

;; ----- Sentry -----

(defonce dsn (or (env :sentry-dsn) false))

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