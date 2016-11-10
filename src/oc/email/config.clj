(ns oc.email.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

(defn- bool
  "Handle the fact that we may have true/false strings, when we want booleans."
  [val]
  (boolean (Boolean/valueOf val)))

(defonce intro? (bool (or (env :intro ) false)))

;; ----- Logging -----

(defonce log-level (or (env :log-level) :info))

;; ----- Sentry -----

(defonce dsn (or (env :sentry-dsn) false))

;; ------ OC Web -----

(defonce web-url (or (env :oc-web-url) "http://localhost:3559"))

;; ----- AWS -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))
(defonce aws-creds {:access-key aws-access-key-id
                    :secret-key aws-secret-access-key})
(defonce aws-endpoint (env :aws-endpoint))

;; ----- AWS SQS -----

(defonce aws-sqs-email-queue (env :aws-sqs-email-queue))

;; ----- AWS S3 -----

(defonce aws-s3-chart-bucket (env :aws-s3-chart-bucket))

;; ----- Email -----

(defonce email-from-domain (env :email-from-domain))