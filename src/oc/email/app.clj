(ns oc.email.app
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as stream]
            [taoensso.timbre :as timbre]
            [oc.email.config :as c]
            [oc.lib.sentry-appender :as sentry]
            [oc.lib.sqs :as sqs]
            [oc.email.mailer :as mailer]))

(defn sqs-handler [msg done-channel]
  (doseq [msg-body (sqs/read-message-body (:body msg))]
    (let [msg-type (or (when (:Message msg-body) :sns) (:type msg-body))]
      (timbre/info "Received message from SQS.")
      (timbre/debug "\nMessage (" msg-type ") from SQS:" msg-body "\n")
      (case (keyword msg-type)
        :reset (mailer/send-token :reset msg-body)
        :verify (mailer/send-token :verify msg-body)
        :invite (mailer/send-invite msg-body)
        :share-entry (mailer/send-entry msg-body)
        :digest (mailer/send-digest msg-body)
        :sns (mailer/handle-data-change msg-body)
        :notify (mailer/send-notification msg-body)
        :reminder-alert (mailer/send-reminder-alert msg-body)
        :reminder-notification (mailer/send-reminder-notification msg-body)
        :bot-removed (mailer/send-bot-removed msg-body)
        (timbre/error "Unrecognized message type" msg-type))))
  (sqs/ack done-channel msg))

(defn system [config-options]
  (let [{:keys [sqs-creds sqs-queue sqs-msg-handler]} config-options]
    (component/system-map
      :sqs (sqs/sqs-listener sqs-creds sqs-queue sqs-msg-handler))))

(defn echo-config []
  (println (str "\n"
    "AWS SQS queue: " c/aws-sqs-email-queue "\n"
    "Storage URL: " c/storage-server-url "\n"
    "Auth URL: " c/auth-server-url "\n"
    "Web URL: " c/web-url "\n"    
    "Email from: " c/email-from-domain "\n"
    "Email digest-prefix: " c/email-digest-prefix "\n"
    "Email images prefix: " c/email-images-prefix "\n"
    "FileStack: " (or c/filestack-api-key "false") "\n"
    "Sentry: " c/dsn "\n"
    "  env: " c/sentry-env "\n"
    (when-not (clojure.string/blank? c/sentry-release)
      (str "  release: " c/sentry-release "\n"))
    "\n"
    (when c/intro? "Ready to serve...\n"))))

(defn start []

  ;; Log errors to Sentry
  (if c/dsn
    (timbre/merge-config!
      {:level (keyword c/log-level)
       :appenders {:sentry (sentry/sentry-appender c/dsn {:environment c/sentry-env
                                                          :release c/sentry-release})}})
    (timbre/merge-config! {:level (keyword c/log-level)}))

  ;; Uncaught exceptions go to Sentry
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex)))))

  ;; Echo config information
  (println (str "\n"
    (when c/intro? (str (slurp (clojure.java.io/resource "oc/assets/ascii_art.txt")) "\n"))
    "OpenCompany Email Service\n"))
  (echo-config)

  ;; Start the system, which will start long polling SQS
  (component/start (system {:sqs-queue c/aws-sqs-email-queue
                            :sqs-msg-handler sqs-handler
                            :sqs-creds {:access-key c/aws-access-key-id
                                        :secret-key c/aws-secret-access-key}}))

  (deref (stream/take! (stream/stream)))) ; block forever

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