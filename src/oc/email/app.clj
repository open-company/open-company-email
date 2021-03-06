(ns oc.email.app
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as jio]
            [oc.lib.component.keep-alive :refer (->KeepAlive)]
            [oc.lib.sentry.core :as sentry-lib :refer (map->SentryCapturer)]
            [oc.email.config :as c]
            [oc.lib.sqs :as sqs]
            [oc.email.mailer :as mailer]))

(defn sqs-handler [msg done-channel]
  (try
    (doseq [msg-body (sqs/read-message-body (:body msg))]
      (let [msg-type (or (when (:Message msg-body) :sns) (:type msg-body))]
        (timbre/info "Received message from SQS:" (keyword msg-type))
        (timbre/debug "\nMessage (" msg-type ") from SQS:" msg-body "\n")
        (case (keyword msg-type)
          :reset (mailer/send-token :reset msg-body)
          :verify (mailer/send-token :verify msg-body)
          :invite (mailer/send-invite msg-body)
          :share-entry (mailer/send-entry msg-body)
          :digest (mailer/send-digest msg-body)
          :sns (mailer/handle-data-change msg-body)
          :notify (mailer/send-entry-notification msg-body)
          :team (when (-> msg-body :notification :team-action keyword (= :team-add))
                  (mailer/send-team-notification msg-body))
          :reminder-alert (mailer/send-reminder-alert msg-body)
          :reminder-notification (mailer/send-reminder-notification msg-body)
          :bot-removed (mailer/send-bot-removed msg-body)
          (timbre/warn "Unrecognized message type" msg-type))))
    (sqs/ack done-channel msg)
    (catch Exception e
      (timbre/warn e)
      (sentry-lib/capture e)
      (throw e))))

(defn system [config-options]
  (let [{:keys [sqs-creds sqs-queue sqs-msg-handler sentry]} config-options]
    (component/system-map
     :sentry-capturer (map->SentryCapturer sentry)
     :sqs (component/using
           (sqs/sqs-listener sqs-creds sqs-queue sqs-msg-handler)
           [:sentry-capturer])
     ;; This must be last and needs all other components as dependency
     :keep-alive (component/using
                  (->KeepAlive)
                  [:sentry-capturer :sqs]))))

(defn echo-config []
  (println (str "\n"
    "AWS SQS queue: " c/aws-sqs-email-queue "\n"
    "Storage URL: " c/storage-server-url "\n"
    "Auth URL: " c/auth-server-url "\n"
    "Web URL: " c/web-url "\n"
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
  (component/start (system {:sentry c/sentry-config
                            :sqs-queue c/aws-sqs-email-queue
                            :sqs-msg-handler sqs-handler
                            :sqs-creds {:access-key c/aws-access-key-id
                                        :secret-key c/aws-secret-access-key}})))

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