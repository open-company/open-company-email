(ns oc.email.mailer
  (:require [clojure.string :as s]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.walk :refer (keywordize-keys)]
            [taoensso.timbre :as timbre]
            [amazonica.aws.simpleemail :as ses]
            [cheshire.core :as json]
            [oc.email.config :as c]
            [oc.email.content :as content]))

(def size-limit 100000) ; 100KB size limit of HTML content in GMail

(def creds
  {:access-key c/aws-access-key-id
   :secret-key c/aws-secret-access-key
   :endpoint   c/aws-endpoint})

(def default-reply-to (str "hello@" c/email-from-domain))
(def default-from "Carrot")
(def default-source (str default-from " <" default-reply-to ">"))

(def digest-reply-to (str "digest@" c/email-from-domain))
(def digest-source (str default-from " <" digest-reply-to ">"))

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

(defn- email-entry
  "Send emails to all to recipients in parallel."
  [{:keys [to reply-to subject org-slug org-name]} body]
  (doall (pmap #(email {:to %
                        :source (str org-name " <" org-slug "@" c/email-from-domain ">")
                        :reply-to (if (s/blank? reply-to) default-reply-to reply-to)
                        :subject subject}
                  {:text body}) ; TEMP as text only
            to)))

(defn- inline-css [html-file inline-file]
  (shell/sh "juice" 
            "--web-resources-images" "false"
            "--remove-style-tags" "true"
            "--preserve-media-queries" "false"
            "--preserve-font-faces" "false"
            html-file inline-file))

(defn send-entry
  "Create an HTML email for the specified recipients."
  [entry]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")]
    (try
      ;; COMMENTED OUT HTML EMAIL
      ;;(spit html-file (content/share-link-html entry)) ; create the email in a tmp file
      ;;(inline-css html-file inline-file) ; inline the CSS
      ;; Email the recipients
      ;;(email-entry entry (slurp inline-file))
      ;; TEXT EMAIL
      (email-entry entry (content/share-link-text entry))
      (finally
        ;; remove the tmp files
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
                    (assoc :source default-source)
                    (assoc :subject (content/invite-subject message false)))]
    (try
      (spit html-file (content/invite-html invitation)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (email invitation {:text (content/invite-text invitation)
                         :html (slurp inline-file)})
      (finally
        ;; remove the tmp files
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
              (assoc :source default-source)
              (assoc :from default-from)
              (assoc :reply-to default-reply-to)
              (assoc :subject (case token-type
                                  :reset "Carrot Password Reset"
                                  :verify "Carrot Email Verification")))]
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

(defn send-digest
  "Create an HTML digest and email it to the specified recipient."
  [message]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        msg (keywordize-keys message)
        org-name (:org-name msg)
        frequency (if (= (keyword (:digest-frequency msg)) :daily) "Daily" "Weekly")]
    (try
      (spit html-file (content/digest-html msg)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (email {:to (:email msg)
              :source digest-source
              :from default-from
              :reply-to default-reply-to
              :subject (str c/email-digest-prefix org-name " " frequency " Digest")}
             {:html (slurp inline-file)})
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn- send-private-board-notification
  "Creates an html email and sends it to the recipient."
  [msg]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        org-name (:name (:org msg))]
    (try
      (spit html-file (content/board-notification-html msg)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
       ;; Email it to the recipient
      (email {:to (:email (:user msg))
              :source default-source
              :from default-from
              :reply-to default-reply-to
              :subject (str c/email-digest-prefix org-name)}
             {:html (slurp inline-file)})
      (finally
       ;; remove the tmp files
       (io/delete-file html-file true)
       (io/delete-file inline-file true)))))

(defn handle-data-change
  "
  Test to see if message is a board change and that it has notifications.
  If so, check if they are slack users and if not send an email.
  "
  [message]
  (let [msg-parsed (json/parse-string (:Message message) true)]
    (when (and ; update or add on a board
            (or
              (= (:notification-type msg-parsed) "update")
              (= (:notification-type msg-parsed) "add"))
            (= (:resource-type msg-parsed) "board"))
      (let [notifications (-> msg-parsed :content :notifications)
            board (-> msg-parsed :content :new)
            user (:user msg-parsed)]

        (doseq [notify notifications]
          (let [slack-info (first (vals (:slack-users notify)))]
            (when-not slack-info ; Slack users get notified elsewhere via Slack
              (let [board-url (s/join "/" [c/web-url
                                           (:slug (:org msg-parsed))
                                           (:slug board)])
                    message "You have been invited to a private board. "]
                (send-private-board-notification {
                                                  :user notify
                                                  :org (:org msg-parsed)
                                                  :board-url board-url
                                                  :text message})))))))))

(comment

  ;; For REPL testing

  (require '[oc.email.mailer :as mailer] :reload)

  (def share-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/updates/green-labs.json"))))
  (mailer/send-story (merge share-request {
                       :to ["change@me.com"]
                       :reply-to "change@me.com"
                       :subject "Latest GreenLabs Update"
                       :note "Enjoy this groovy update!"}))

  (def digest (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/digest/carrot.json"))))
  (mailer/send-update (merge digest {
                       :to ["change@me.com"]
                       :reply-to "change@me.com"
                       :subject "Latest New.ly Update"
                       :origin "http://localhost:3559"}))

  (def invite (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/carrot-invites/microsoft.json"))))
  (mailer/send-invite (assoc invite :to "change@me.com"))

  (def reset (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/token/apple-password-reset.json"))))
  (mailer/send-token :reset (assoc reset :to "change@me.com")

)