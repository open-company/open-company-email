(ns oc.email.mailer
  (:require [clojure.string :as s]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.walk :refer (keywordize-keys)]
            [taoensso.timbre :as timbre]
            [amazonica.aws.simpleemail :as ses]
            [raven-clj.core :as sentry]
            [oc.email.config :as c]
            [oc.email.content :as content]))

(def size-limit 100000) ; 100KB size limit of HTML content in GMail

(def creds
  {:access-key c/aws-access-key-id
   :secret-key c/aws-secret-access-key
   :endpoint   c/aws-endpoint})

(def default-reply-to (str "hello@" c/email-from-domain))
(def default-from "OpenCompany")

(defn- email
  "Send an email."
  [{:keys [to source reply-to subject]} body]
  (timbre/info "Sending email: " to)
  (let [text (:text body)
        text-body (if text {:text text} {})
        html (:html body)
        html-body (if html (assoc text-body :html html) text-body)]
    (ses/send-email creds
      :destination {:to-addresses [to]}
      :source source
      :reply-to-addresses [reply-to]
      :message {:subject subject
                :body html-body})))

(defn- email-snapshots
  "Send emails to all to recipients in parallel."
  [{:keys [to reply-to subject snapshot]} body]
  (let [snapshot (keywordize-keys snapshot)
        company-slug (:company-slug snapshot)
        company-name (:name snapshot)]
    (doall (pmap #(email {:to %
                          :source (str company-name "<" company-slug "@" c/email-from-domain ">")
                          :reply-to (if (s/blank? reply-to) default-reply-to reply-to)
                          :subject subject} 
                    {:html body})
              to))))

(defn- inline-css [html-file inline-file]
  (shell/sh "juice" 
            "--web-resources-images" "false"
            "--remove-style-tags" "true"
            "--preserve-media-queries" "false"
            "--preserve-font-faces" "false"
            html-file inline-file))

(defn send-snapshot
  "Create an HTML snapshot and email it to the specified recipients."
  [{note :note snapshot :snapshot origin-url :origin-url :as msg}]
  (let [full-snapshot (-> snapshot 
                        (assoc :note note)
                        (assoc :origin-url origin-url))
        uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")]
    (try
      (spit html-file (content/snapshot-html full-snapshot)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      (let [file-size (.length (io/file inline-file))]
        (when (>= file-size size-limit)
          ;; Send a Sentry notification
          (when c/dsn ; Sentry is configured
            (sentry/capture c/dsn {:message "Rendered update email is over size limit"
                                   :extra {
                                      :company-slug (:company-slug snapshot)
                                      :update-slug (:slug snapshot)
                                      :human-size (str (int (Math/ceil (/ file-size 1000))) "KB")
                                      :size file-size
                                      :topic-count (count (:sections snapshot))}}))
          ;; Render an alternative, smaller email
          (spit html-file (content/snapshot-link-html full-snapshot)) ; create the email in a tmp file
          (inline-css html-file inline-file))) ; inline the CSS
      ; Email the recipients
      (email-snapshots msg (slurp inline-file))
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn send-invite
  "Create an HTML and text invite and email it to the specified recipient."
  [message]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        invitation (-> message 
                    (keywordize-keys)
                    (assoc :source (str default-from " <" default-reply-to ">"))
                    (assoc :subject (content/invite-subject message)))]
    (try
      (spit html-file (content/invite-html invitation)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (println invitation)
      (email invitation {:text (content/invite-text invitation)
                         :html (slurp inline-file)})
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn send-reset
  "Create an HTML and text reset password email and email it to the specified recipient."
  [message]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        reset (-> message 
                (keywordize-keys)
                (assoc :source (str default-from " <" default-reply-to ">"))
                (assoc :from default-from)
                (assoc :reply-to default-reply-to)
                (assoc :subject "OpenCompany Password Reset"))]
    (try
      (spit html-file (content/reset-html reset)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (println reset)
      (email reset {:text (content/reset-text reset)
                    :html (slurp inline-file)})
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(comment

  ;; For REPL testing

  (require '[oc.email.mailer :as mailer] :reload)

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/green-labs.json")))
  (mailer/send-snapshot {:to ["change@me.com"]
                         :reply-to "change@me.com"
                         :subject "Latest GreenLabs Update"
                         :note "Enjoy this groovy update!"
                         :origin "http://localhost:3559"
                         :snapshot (assoc snapshot :company-slug "green-labs")})

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/buff.json")))
  (mailer/send-snapshot {:to ["change@me.com"]
                         :reply-to "change@me.com"
                         :subject "Latest Buffer Update"
                         :note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week."
                         :origin "http://localhost:3559"
                         :snapshot (assoc snapshot :company-slug "buff")})

  (def invite (json/decode (slurp "./opt/samples/invites/microsoft.json")))
  (mailer/send-invite (assoc invite :to "change@me.com"))

  (mailer/send-reset {:to "change@me.com"
                      :token-link "http://localhost:3000/invite?token=dd7c0bfe-2068-4de0-aa3c-4913eeeaa360"})

)