(ns oc.email.app
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as jio]
            [oc.email.config :as c]
            [oc.email.async.ses-monitor :as ses-monitor]
            [oc.email.async.sqs-handler :as sqs-handler]
            [oc.email.components :refer (email-system)]))

(defn echo-config []
  (println (str "\n"
    "AWS SQS queue: " c/aws-sqs-email-queue "\n"
    "AWS SQS SES monitor queue: " c/aws-sqs-ses-monitor-queue "\n"
    "Storage URL: " c/storage-server-url "\n"
    "Auth URL: " c/auth-server-url "\n"
    "Web URL: " c/web-url "\n"
    "Dynamo DB: " c/dynamodb-end-point "\n"
    "Table prefix: " c/dynamodb-table-prefix "\n"
    "Email from: " c/email-from-domain "\n"
    "Email digest-prefix: " c/email-digest-prefix "\n"
    "Email images prefix: " c/email-images-prefix "\n"
    "Filestack API Key: " (or c/filestack-api-key "false") "\n"
    "Log level: " c/log-level "\n"
    "Sentry: " c/sentry-config "\n"
    "\n"
    (when c/intro? "Ready to serve...\n"))))

(defn start []

  ;; Log errors to Sentry
  (timbre/merge-config! {:min-level (keyword c/log-level)})

  ;; Echo config information
  (println (str "\n"
    (when c/intro? (str (slurp (jio/resource "oc/assets/ascii_art.txt")) "\n"))
    "OpenCompany Email Service\n"))
  (echo-config)

  ;; Start the system, which will start long polling SQS
  (component/start (email-system {:sentry c/sentry-config
                                  :sqs-queue c/aws-sqs-email-queue
                                  :sqs-msg-handler sqs-handler/handler
                                  :sqs-creds {:access-key c/aws-access-key-id
                                              :secret-key c/aws-secret-access-key}
                                  :ses-monitor-sqs-queue c/aws-sqs-ses-monitor-queue
                                  :ses-monitor-sqs-msg-handler ses-monitor/sqs-handler})))

(defn -main []
  (start))

(comment

  ;; SQS message payload
  (def entries (json/decode (slurp "./opt/samples/updates/green-labs.json")))
  (def message
    {:subject "GreenLabs Update"
     :to ["change@changeme.com" "change2@changeme.com"]
     :note "Howdy folks!"
     :reply-to "hange@changeme.com"
     :org-slug "green-labs"
     :entries entries})

  (require '[amazonica.aws.sqs :as sqs2])

  ;; send a test SQS message
  (sqs2/send-message
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-email-queue
    message)

  ;; send a test message that will cause an exception
  (sqs2/send-message
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-email-queue
    {:test-error true})

  )