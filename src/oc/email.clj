(ns oc.email
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as stream]
            [taoensso.timbre :as timbre]
            [oc.email.config :as c]
            [oc.lib.sentry-appender :as sentry]
            [oc.lib.sqs :as sqs]
            [oc.email.mailer :as mailer])
  (:gen-class))

(defn system [config-options]
  (let [{:keys [sqs-queue sqs-msg-handler]} config-options]
    (component/system-map
      :sqs (sqs/sqs-listener sqs-queue sqs-msg-handler))))

(defn sqs-handler [sys msg]
  (let [msg-body (read-string (:body msg))]
    (timbre/info "Received message from SQS.")
    (timbre/tracef "\nMessage from SQS: %s\n" msg-body)
    (mailer/send-snapshot msg-body)
    msg))

(defn -main []
  (if c/dsn
    (timbre/merge-config!
      {:level     :info
       :appenders {:sentry (sentry/sentry-appender c/dsn)}})
    (timbre/merge-config! {:level :debug})))

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex)))))

  (println (str "\n" (slurp (clojure.java.io/resource "oc/assets/ascii_art.txt")) "\n"
    "OpenCompany Email Service\n\n"
    "AWS SQS queue: " c/aws-sqs-queue "\n"
    "Sentry: " c/dsn "\n\n"
    "Ready to serve...\n"))

  (component/start (system {:sqs-queue c/aws-sqs-queue
                            :sqs-msg-handler sqs-handler}))

  (deref (stream/take! (stream/stream)))) ; block forever


(comment


  ;; SQS message payload
  (def snapshot (json/decode (slurp "./resources/snapshots/buffer.json")))
  (def message 
    {:subject "Buffer Update"
     :to "change@changeme.com,change2@changeme.com"
     :note "Howdy folks!"
     :reply-to "hange@changeme.com"
     :company-slug "buffer"
     :snapshot snapshot})

  ;; SQS message payload
  (def snapshot (json/decode (slurp "./resources/snapshots/open.json")))
  (def message 
    {:subject "OpenCompany Update"
     :to "change@changeme.com"
     :note "Look at this!"
     :reply-to "change@changeme.com"
     :company-slug "open"
     :snapshot snapshot})

  (require '[amazonica.aws.sqs :as sqs])
  
  ;; send a test SQS message
  (sqs/send-message
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-email-queue
    message)

  )