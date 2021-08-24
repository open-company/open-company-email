(ns oc.email.async.sqs-handler
  (:require [taoensso.timbre :as timbre]
            [oc.lib.sqs :as sqs]
            [oc.lib.sentry.core :as sentry-lib]
            [oc.email.mailer :as mailer]))

(defn handler [msg done-channel]
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