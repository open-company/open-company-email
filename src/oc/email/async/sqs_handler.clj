(ns oc.email.async.sqs-handler
  (:require [taoensso.timbre :as timbre]
            [oc.lib.sqs :as sqs]
            [oc.lib.sentry.core :as sentry-lib]
            [oc.email.mailer :as mailer]
            [oc.lib.time :as lib-time]
            [clojure.string :as clojure.string]
            [clojure.walk :refer (keywordize-keys)]))
;; {
;;   "from": "iacopo@staging.carrot.io",
;;   "from-avatar": "https://secure.gravatar.com/avatar/866a6350e399e67e749c6f2aef0b96c0.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0020-512.png",
;;   "note": "This is the note we used to show the user hahah",
;;   "reply-to": "team@carrot.io",
;;   "email": "iacopo@carrot.io",
;;   "org-name": "BaGo",
;;   "org-logo-url": "https://open-company-assets.s3.amazonaws.com/bago.png",
;;   "org-logo-width": 300,
;;   "org-logo-height": 96,
;;   "token-link": "http://localhost:3000/invite?token=dd7c0bfe-2068-4de0-aa3c-4913eeeaa360"
;; }
;; Spam email: PlHolPiFI5 @gmail.com

(defn- handle-invite [msg-body]
  (timbre/debug "Handle invite request")
  (timbre/trace msg-body)
  (let [msg (keywordize-keys msg-body)
        to-email (:email msg)
        org-name (:org-name msg)
        org-allowed? (#{"hopper" "carrot" "primary" "stoat labs" "geek.zone"} (clojure.string/lower-case org-name)) ;; Always send invite messages for hopper org]
        email-matches? (re-matches #"[a-zA-Z0-9]{9,11}@gmail.com" to-email)]
    (timbre/debugf "Handle invite parse message for %s from org %s. Org allowed? %s, email-matches? %s" to-email org-name (if org-allowed? "YES" "NO") email-matches?)
    (timbre/trace msg)
    (if org-allowed?
      (mailer/send-invite msg-body)
      (if email-matches?
        (sentry-lib/capture (ex-info "Discard spammy invite message" {:to-email to-email :org-name org-name :at (lib-time/current-timestamp)}))
        ;; Erroring here makes so we don't send the ack message, so this will be retried later on
        (throw (ex-info "Not sending invite for spam suspicious" {:to-email to-email :org-name org-name :at (lib-time/current-timestamp)}))))))

(defn handler [msg done-channel]
  (try
    (doseq [msg-body (sqs/read-message-body (:body msg))]
      (let [msg-type (or (when (:Message msg-body) :sns) (:type msg-body))]
        (timbre/info "Received message from SQS:" (keyword msg-type))
        (timbre/debug "\nMessage (" msg-type ") from SQS:" msg-body "\n")
        (case (keyword msg-type)
          :reset (mailer/send-token :reset msg-body)
          :verify (mailer/send-token :verify msg-body)
          :invite (handle-invite msg-body)
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