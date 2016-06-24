(ns oc.email
  (:require [com.stuartsierra.component :as component]
            [environ.core :as e]
            [manifold.stream :as stream]
            [taoensso.timbre :as timbre]
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
    (timbre/infof "Received message from SQS: %s\n" msg-body)
    (mailer/send msg)))

(defn -main []
  (let [dsn (e/env :sentry-dsn)]
    (if dsn
      (timbre/merge-config!
        {:level     :info
         :appenders {:sentry (sentry/sentry-appender (e/env :sentry-dsn))}})
      (timbre/merge-config! {:level :debug})))

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex)))))

  (component/start (system {:sqs-queue (e/env :aws-sqs-email-queue)
                            :sqs-msg-handler sqs-handler}))

  (deref (stream/take! (stream/stream)))) ; block forever