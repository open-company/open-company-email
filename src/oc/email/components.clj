(ns oc.email.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [oc.lib.component.keep-alive :refer (->KeepAlive)]
            [oc.lib.sentry.core :refer (map->SentryCapturer)]
            [oc.lib.sqs :as sqs]
            [oc.email.async.ses-monitor :as ses-monitor]))

(defrecord SESMonitorConsumer []
  component/Lifecycle

  (start [component]
    (timbre/info "[ses-monitor-consumer] starting...")
    (ses-monitor/start)
    (timbre/info "[ses-monitor-consumer] started")
    (assoc component :ses-monitor-consumer true))

  (stop [{:keys [ses-monitor-consumer] :as component}]
    (if ses-monitor-consumer
      (do
        (timbre/info "[ses-monitor-consumer] stopping...")
        (ses-monitor/stop)
        (timbre/info "[ses-monitor-consumer] stopped")
        (assoc component :ses-monitor-consumer nil))
      component)))

(defn email-system [{:keys [sqs-creds sqs-queue sqs-msg-handler sentry ses-monitor-sqs-msg-handler
                            ses-monitor-sqs-queue]}]
    (component/system-map
     :sentry-capturer (map->SentryCapturer sentry)
     :sqs (component/using
           (sqs/sqs-listener sqs-creds sqs-queue sqs-msg-handler)
           [:sentry-capturer])
     :ses-monitor (component/using
                   (->SESMonitorConsumer)
                   [:sentry-capturer])
     :ses-monitor-sqs (component/using
                       (sqs/sqs-listener sqs-creds ses-monitor-sqs-queue ses-monitor-sqs-msg-handler)
                       [:sentry-capturer])
     ;; This must be last and needs all other components as dependency
     :keep-alive (component/using
                  (->KeepAlive)
                  [:sentry-capturer :sqs])))