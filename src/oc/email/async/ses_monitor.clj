(ns oc.email.async.ses-monitor
  "
  Async consumption of SES bounce and reputation receipts. In case of a hard bounce message
  we ought to flag the user and stop sending emails all together.
  The bounce rate is calculated on the hard bounce repetition, if we keep sending emails to
  addresses that don't exists we are labeled as spammer and Amazon will block our verified 
  domain/email addresses to avoid getting banned from the mail servers of the community.
  "
  (:require [clojure.core.async :as async :refer (<! >!!)]
            [cheshire.core :as json]
            [clojure.string :as cstr]
            [taoensso.timbre :as timbre]
            [oc.lib.sqs :as sqs]
            [oc.email.config :as c]
            [oc.lib.slack :as slack-lib]
            [oc.lib.sentry.core :as sentry]
            [oc.lib.email.resources.bounced-email :as bounced-email]))

;; ----- Utilities -----

(defn- get-headers [msg]
  (as-> msg hs
    (:mail hs)
    (:headers hs)
    (zipmap (map (comp keyword cstr/lower-case :name) hs) hs)))

(defn- get-header [msg header-name]
  {:pre [(map? msg)
         (keyword? header-name)]}
  (some-> msg
          :parsed-headers
          header-name
          :value))

;; ----- core.async -----

(defonce ses-monitor-chan (async/chan 10000)) ; buffered channel

(defonce ses-monitor-go (atom true))

;; ----- Event handling -----

(defn- first-bounce-info-msg [email]
  (format "Email %s got hard bounced!" email))

(defn- bounce-info-msg [email-rec]
  (format "Bounced email %s already present! Current counter is %d (resource-type %s, updated-at %s)"
          (:email email-rec) (:bounce-count email-rec) (:resource-type email-rec) (:updated-at email-rec)))

(defn- bounce-warn-msg [email-rec]
  (format "<!here> Multiple hard bounce for email %s! Current bounce %d (resource-type %s, last bounce: %s)"
          (:email email-rec) (:bounce-count email-rec) (:resource-type email-rec) (:updated-at email-rec)))

(defn- repeated-bounce? [email-rec]
  (some-> email-rec :bounce-count (> 1)))

(defn- bounce-msg [email email-rec]
  (let [msg-type (if (repeated-bounce? email-rec) :info :warn)]
    (cond (some-> email-rec :bounce-count (> 0))
          (bounce-warn-msg email-rec)

          (map? email-rec)
          (bounce-info-msg email-rec)

          :else
          (first-bounce-info-msg email))))

(defn- flag-recipients!
  [recipients]
  (timbre/debug "About to flag for hard bounce:" recipients)
  (doseq [recipient recipients]
    (timbre/debugf "Lookup user with email %s" recipient)
    (let [email-rec (bounced-email/retrieve-email recipient)
          msg (bounce-msg recipient email-rec)]
      (when (bounced-email/store-hard-bounce! recipient)
        (if (repeated-bounce? email-rec)
          (timbre/info msg)
          (timbre/warn msg))
        (slack-lib/slack-report msg)))))

;; Do not use the :to header, it couldcontain multiple emails but not all were bounced
;; (defn- get-receipt-to [msg]
;;   (let [recipients-from-headers (-> msg
;;                                     (get-header :to)
;;                                     (cstr/split #"\s*,\s*"))]
;;     (timbre/debug "Retrieved to header:" recipients-from-headers)
;;     recipients-from-headers))

(defn- get-receipt-recipients [msg]
  (timbre/debug "get-receipt-recipients")
  (let [dest (-> msg :mail :destination)]
    (timbre/debugf "Destination from receipt: %s" (cstr/join ", " dest))
    dest))

(defn- is-hard-bounce-receipt? [msg]
  (let [bounce? (-> msg :notificationType cstr/lower-case (= "bounce"))
        bounce-data (when bounce? (:bounce msg))]
    (and (-> bounce-data :bounceType cstr/lower-case (= "permanent"))
         (map? (:bounce msg)))))

(defn- get-origination [msg]
  (-> msg                       ;; {:parsed-headers {:reply-to {:value "Carrot.No.Reply+staging@staging.carrot.io"}}}
      :parsed-headers           ;; {:reply-to {:value "Carrot.No.Reply+staging@staging.carrot.io"}}
      :reply-to                 ;; {:value "Carrot.No.Reply+staging@staging.carrot.io"}
      :value                    ;; "Carrot.No.Reply+staging@staging.carrot.io"
      (cstr/split #"@")         ;; ["Carrot.No.Reply+staging" "staging.carrot.io"]
      first                     ;; "Carrot.No.Reply+staging"
      (cstr/split #"\+")         ;; ["Carrot.No.Reply" "staging"]
      (get 1 "production")))    ;; "staging"

(defn- environment-matches? [msg]
  (= (get-origination msg) c/sentry-env))

(defn- handle-ses-monitor-message
  [msg]
  (timbre/debug "New message in SES monitor" msg)
  (if (environment-matches? msg)
    (if (is-hard-bounce-receipt? msg)
      (let [recipients (get-receipt-recipients msg)]
        (flag-recipients! recipients))
      (timbre/debugf "Discarding receipt, no hard bounce.\nNotification type: %s\nBounce type: %s\nBounce sub-type: %s"
                     (:notificationType msg)
                     (-> msg :bounce :bounceType)
                     (-> msg :bounce :bounceSubType)))
    (timbre/debugf "Discarding receipt from env %s (current: %s)" (get-origination msg) c/sentry-env)))

;; ----- SQS handling -----

(defn- read-message-body
  "
  Try to parse as json, otherwise use read-string.
  "
  [msg]
  (try
    (json/parse-string msg true)
    (catch Exception e
      (timbre/warn "Failed parsing SQS message:" e)
      (timbre/debug "Will try with read-string...")
      (read-string msg))))

(defn- parse-message [msg]
  (try
    (let [parsed-msg (read-message-body msg)]
      (assoc parsed-msg :parsed-headers (get-headers parsed-msg)))
    (catch Exception e
      (timbre/error "Failed parsing receipt message" e)
      false)))

(defn sqs-handler
  "Handle an incoming SQS message from the SES sending monitor to our Email service."
  [msg done-channel]
  (try
    (let [msg-body (parse-message (:body msg))
          _error (if (:test-error msg-body) (/ 1 0) false)] ; a message testing Sentry error reporting
      (timbre/info "Received message from SQS")
      (timbre/debugf "Received message from SQS: %s\n" msg-body)
      (>!! ses-monitor-chan msg-body))
    (sqs/ack done-channel msg)
    (catch Throwable e
      (timbre/debug "Error in sqs-handler")
      (timbre/error e))))


;; ----- Event loop -----

(defn- ses-monitor-loop
  []
  (reset! ses-monitor-go true)
  (async/go
    (while @ses-monitor-go
      (timbre/debug "SES monitor consumer waiting...")
      (let [msg (<! ses-monitor-chan)]
        (timbre/debug "Processing message on SES monitor channel...")
        (if (:stop msg)
          (do (reset! ses-monitor-go false) (timbre/info "SES monitor consumer stopped."))
          (try
            (handle-ses-monitor-message msg)
            (timbre/debug "Processing complete.")
            (catch Throwable e
              (timbre/debug "Error processing message in ses-monitor:")
              (timbre/warn e)
              (sentry/capture e))))))))

;; ----- Component start/stop -----

(defn start
  "Start the core.async event loop."
  []
  (timbre/info "Starting SES Monitor consumer...")
  (ses-monitor-loop))

(defn stop
  "Stop the the core.async event loop."
  []
  (when @ses-monitor-go
    (timbre/info "Stopping SES Monitor consumer...")
    (>!! ses-monitor-chan {:stop true})))

(comment
  (require '[oc.email.async.ses-monitor :as ses-monitor] :reload)
  ;; (require '[cheshire.core :as json])
  ;; (require '[oc.email.config :as c])
  (require '[taoensso.timbre :as timbre])
  (require '[clojure.core.async :as async])
  
  (timbre/merge-config! {:min-level :trace})

  ;; Test with fake payload
  ;; (def json-str "{\"notificationType\":\"Bounce\",\"bounce\":{\"feedbackId\":\"0100017b6d8e8cb3-b3aa7b49-0d23-4597-95ba-dd39bcfde2ed-000000\",\"bounceType\":\"Permanent\",\"bounceSubType\":\"General\",\"bouncedRecipients\":[{\"emailAddress\":\"n.zilberstien@wolt.com\",\"action\":\"failed\",\"status\":\"5.1.1\",\"diagnosticCode\":\"smtp; 550-5.1.1 The email account that you tried to reach does not exist. Please try 550-5.1.1 double-checking the recipient's email address for typos or 550-5.1.1 unnecessary spaces. Learn more at\n550 5.1.1  https://support.google.com/mail/?p=NoSuchUser 4si7603502qvt.8 - gsmtp\"}],\"timestamp\":\"2021-08-22T11:11:03.000Z\",\"remoteMtaIp\":\"173.194.66.27\",\"reportingMTA\":\"dns; a48-122.smtp-out.amazonses.com\"},\"mail\":{\"timestamp\":\"2021-08-22T11:11:03.333Z\",\"source\":\"Carrot <Carrot.No.Reply@carrot.io>\",\"sourceArn\":\"arn:aws:ses:us-east-1:892554801312:identity/carrot.io\",\"sourceIp\":\"34.226.29.126\",\"sendingAccountId\":\"892554801312\",\"messageId\":\"0100017b6d8e8aa5-66c4691e-25f5-4413-ac6a-2656c55c339a-000000\",\"destination\":[\"n.zilberstien@wolt.com\"],\"headersTruncated\":false,\"headers\":[{\"name\":\"From\",\"value\":\"Carrot <Carrot.No.Reply@carrot.io>\"},{\"name\":\"Reply-To\",\"value\":\"Carrot.No.Reply@carrot.io\"},{\"name\":\"To\",\"value\":\"n.zilberstien@wolt.com\"},{\"name\":\"Subject\",\"value\":\"Please verify your email\"},{\"name\":\"MIME-Version\",\"value\":\"1.0\"},{\"name\":\"Content-Type\",\"value\":\"multipart/alternative;  boundary=\\\"----=_Part_1154894_751570646.1629630663336\\\"\"}],\"commonHeaders\":{\"from\":[\"Carrot <Carrot.No.Reply@carrot.io>\"],\"replyTo\":[\"Carrot.No.Reply@carrot.io\"],\"to\":[\"n.zilberstien@wolt.com\"],\"subject\":\"Please verify your email\"}}}")
  ;; (def json-obj (json/parse-string json-str true))

  ;; (println "Loaded obj:" json-obj)

  ;; (ses-monitor/handle-ses-monitor-message json-obj)
  ;; (println "After ses-monitor handler")
  (def json-str "{\"notificationType\":\"Bounce\",\"bounce\":{\"feedbackId\":\"0100017b6d8e8cb3-b3aa7b49-0d23-4597-95ba-dd39bcfde2ed-000000\",\"bounceType\":\"Permanent\",\"bounceSubType\":\"General\",\"bouncedRecipients\":[{\"emailAddress\":\"n.zilberstien@wolt.com\",\"action\":\"failed\",\"status\":\"5.1.1\",\"diagnosticCode\":\"smtp; 550-5.1.1 The email account that you tried to reach does not exist. Please try 550-5.1.1 double-checking the recipient's email address for typos or 550-5.1.1 unnecessary spaces. Learn more at\n550 5.1.1  https://support.google.com/mail/?p=NoSuchUser 4si7603502qvt.8 - gsmtp\"}],\"timestamp\":\"2021-08-22T11:11:03.000Z\",\"remoteMtaIp\":\"173.194.66.27\",\"reportingMTA\":\"dns; a48-122.smtp-out.amazonses.com\"},\"mail\":{\"timestamp\":\"2021-08-22T11:11:03.333Z\",\"source\":\"Carrot <Carrot.No.Reply@carrot.io>\",\"sourceArn\":\"arn:aws:ses:us-east-1:892554801312:identity/carrot.io\",\"sourceIp\":\"34.226.29.126\",\"sendingAccountId\":\"892554801312\",\"messageId\":\"0100017b6d8e8aa5-66c4691e-25f5-4413-ac6a-2656c55c339a-000000\",\"destination\":[\"n.zilberstien@wolt.com\"],\"headersTruncated\":false,\"headers\":[{\"tags\":{\"origination-env\":\"localhost\"},\"name\":\"From\",\"value\":\"Carrot <Carrot.No.Reply@carrot.io>\"},{\"name\":\"Reply-To\",\"value\":\"Carrot.No.Reply@carrot.io\"},{\"name\":\"To\",\"value\":\"n.zilberstien@wolt.com\"},{\"name\":\"Subject\",\"value\":\"Please verify your email\"},{\"name\":\"MIME-Version\",\"value\":\"1.0\"},{\"name\":\"Content-Type\",\"value\":\"multipart/alternative;  boundary=\\\"----=_Part_1154894_751570646.1629630663336\\\"\"}],\"commonHeaders\":{\"tags\": {\"origination-env\":\"localhost\"},\"from\":[\"Carrot <Carrot.No.Reply@carrot.io>\"],\"replyTo\":[\"Carrot.No.Reply@carrot.io\"],\"to\":[\"n.zilberstien@wolt.com\"],\"subject\":\"Please verify your email\"}}}")
  (-> json-str (json/parse-string true) :mail :destination)
  (ses-monitor/sqs-handler {:body json-str} (async/chan))
)