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

(defn- email-update
  "Send emails to all to recipients in parallel."
  [{:keys [to reply-to subject org-slug org-name entries] :as update} body]
  (doall (pmap #(email {:to %
                        :source (str org-name " <" org-slug "@" c/email-from-domain ">")
                        :reply-to (if (s/blank? reply-to) default-reply-to reply-to)
                        :subject subject}
                  {:html body})
            to)))

(defn- inline-css [html-file inline-file]
  (shell/sh "juice" 
            "--web-resources-images" "false"
            "--remove-style-tags" "true"
            "--preserve-media-queries" "false"
            "--preserve-font-faces" "false"
            html-file inline-file))

(defn send-update
  "Create an HTML update and email it to the specified recipients."
  [update]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")]
    (try
      (spit html-file (content/update-html update)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      (let [file-size (.length (io/file inline-file))]
        (when (>= file-size size-limit)
          ;; Send a Sentry notification
          (when c/dsn ; Sentry is configured
            (sentry/capture c/dsn {:message "Rendered update email is over size limit"
                                   :extra {
                                      :org-slug (:org-slug update)
                                      :slug (:slug update)
                                      :human-size (str (int (Math/ceil (/ file-size 1000))) "KB")
                                      :size file-size
                                      :entry-count (count (:entries update))}}))
          ;; Render an alternative, smaller email
          (spit html-file (content/update-link-html update)) ; create the email in a tmp file
          (inline-css html-file inline-file))) ; inline the CSS
      ; Email the recipients
      (email-update update (slurp inline-file))
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
      (email invitation {:text (content/invite-text invitation)
                         :html (slurp inline-file)})
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn send-token
  "Create an HTML and text one-time-token email and email it to the specified recipient."
  [token-type message]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        msg (-> message 
              (keywordize-keys)
              (assoc :source (str default-from " <" default-reply-to ">"))
              (assoc :from default-from)
              (assoc :reply-to default-reply-to)
              (assoc :subject (case token-type
                                  :reset "OpenCompany Password Reset"
                                  :verify "OpenCompany Email Verification")))]
    (try
      (spit html-file (content/token-html token-type msg)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (email msg {:text (content/token-text token-type msg)
                  :html (slurp inline-file)})
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(comment

  ;; For REPL testing

  (require '[oc.email.mailer :as mailer] :reload)

  (def update (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/updates/green-labs.json"))))
  (mailer/send-update (merge update {
                       :to ["change@me.com"]
                       :reply-to "change@me.com"
                       :subject "Latest GreenLabs Update"
                       :note "Enjoy this groovy update!"
                       :origin "http://localhost:3559"}))

  (def update (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/updates/buff.json"))))
  (mailer/send-update (merge update {
                       :to ["change@me.com"]
                       :reply-to "change@me.com"
                       :subject "Latest GreenLabs Update"
                       :note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week."
                       :origin "http://localhost:3559"}))

  (def invite (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/invites/microsoft.json"))))
  (mailer/send-invite (assoc invite :to "change@me.com"))

  (mailer/send-token :reset {:to "change@me.com"
                             :token-link "http://localhost:3000/invite?token=dd7c0bfe-2068-4de0-aa3c-4913eeeaa360"})

)