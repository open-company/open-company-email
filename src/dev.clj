(ns dev
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as ctnrepl]
            [oc.email.config :as c]
            [oc.email.app :as app]
            [oc.email.async.ses-monitor :as ses-monitor]
            [oc.email.async.sqs-handler :as sqs-handler]
            [oc.email.components :as email-comp]))

(def system nil)

(defn init [] (alter-var-root #'system (constantly (email-comp/system {:sqs-queue c/aws-sqs-email-queue
                                                                       :sqs-msg-handler sqs-handler/handler
                                                                       :sqs-creds {:access-key c/aws-access-key-id
                                                                                   :secret-key c/aws-secret-access-key}
                                                                       :ses-monitor-sqs-queue c/aws-sqs-ses-monitor-queue
                                                                       :ses-monitor-sqs-msg-handler ses-monitor/sqs-handler}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start)
  (app/echo-config)
  (println (str "Now serving email requests from the REPL.\n"
                "When you're ready to stop the system, just type: (stop)\n"))
  :ok)

(defn reset []
  (stop)
  (ctnrepl/refresh :after 'user/go))