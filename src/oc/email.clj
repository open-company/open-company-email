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
  (def jwtoken "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImFsYmVydEBjb21iYXQub3JnIiwiYm90Ijp7ImlkIjoiYWJjIiwidG9rZW4iOiJ4eXoifSwiYWRtaW4iOnRydWUsIm5hbWUiOiJjYW11cyIsIm9yZy1pZCI6InNsYWNrOjk4NzY1IiwidXNlci1pZCI6InNsYWNrOjE5NjAtMDEtMDQiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsIm93bmVyIjp0cnVlLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMifQ.-vPPX8iTI5iNzZIXr9HyVqdox5hWrzVfyh0ODNb-xVk") ; JWToken Camus
  (def snapshot (json/decode (slurp "./resources/snapshots/buffer.json")))
  (def message 
    {:api-token jwtoken
     :subject "Buffer Update"
     :to "change@changeme.com,change2@changeme.com"
     :note "Howdy folks!"
     :snapshot snapshot})

  ;; SQS message payload
  (def jwtoken "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoic2xhY2s6VTA2U0JUWEpSIiwibmFtZSI6IlNlYW4gSm9obnNvbiIsInJlYWwtbmFtZSI6IlNlYW4gSm9obnNvbiIsImF2YXRhciI6Imh0dHBzOlwvXC9zZWN1cmUuZ3JhdmF0YXIuY29tXC9hdmF0YXJcL2Y1YjhmYzFhZmZhMjY2YzgwNzIwNjhmODExZjYzZTA0LmpwZz9zPTE5MiZkPWh0dHBzJTNBJTJGJTJGc2xhY2suZ2xvYmFsLnNzbC5mYXN0bHkubmV0JTJGN2ZhOSUyRmltZyUyRmF2YXRhcnMlMkZhdmFfMDAyMC0xOTIucG5nIiwiZW1haWwiOiJzZWFuQG9wZW5jb21wYW55LmNvbSIsIm93bmVyIjpmYWxzZSwiYWRtaW4iOnRydWUsIm9yZy1pZCI6InNsYWNrOlQwNlNCTUg2MCJ9.9Q8GNBojQ_xXT0lMtKve4fb5Pdh260oc2aUc-wP8dus") ; JWToken Sean
  (def snapshot (json/decode (slurp "./resources/snapshots/open.json")))
  (def message 
    {:api-token jwtoken
     :subject "OpenCompany Update"
     :to "sean@opencompany.com"
     :note "Look at this!"
     :snapshot snapshot})

  (require '[amazonica.aws.sqs :as sqs])
  
  ;; send a test SQS message
  (sqs/send-message
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-queue
    message)

  )