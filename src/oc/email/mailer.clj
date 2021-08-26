(ns oc.email.mailer
  (:require [defun.core :refer (defun-)]
            [clojure.string :as s]
            [clojure.set :as clj-set]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.walk :refer (keywordize-keys)]
            [oc.lib.email.resources.bounced-email :as bounced-email]
            [taoensso.timbre :as timbre]
            [amazonica.aws.simpleemail :as ses]
            [cheshire.core :as json]
            [jsoup.soup :as soup]
            [oc.email.config :as c]
            [oc.lib.slack :as slack-lib]
            [oc.email.content :as content]
            [oc.lib.storage :as storage]))

(def creds
  {:access-key c/aws-access-key-id
   :secret-key c/aws-secret-access-key
   :endpoint   c/aws-endpoint})

(defn- env-origination []
  (if (= c/sentry-env "production")
    ""
    (str "+" c/sentry-env)))

(defn- default-reply-to []
  (format "Carrot.No.Reply%s@%s" (env-origination) c/email-from-domain))

(def default-from "Carrot")

(defn- default-source []
  (str default-from " <" (default-reply-to) ">"))

(defun- filter-bounced-recipients
  ([to-addresses :guard sequential?]
   (filter filter-bounced-recipients to-addresses))
  ([to-address :guard string?]
   (-> c/dynamodb-opts
       (bounced-email/retrieve-email to-address)
       map?
       not)))

(defn- email
  "Send an email."
  [{:keys [to source reply-to subject]} body]
  (let [text (:text body)
        text-body (if text {:text text} {})
        html (:html body)
        html-body (if html (assoc text-body :html html) text-body)
        fixed-reply-to (or reply-to (default-reply-to))
        fixed-to (if (sequential? to) to [to])
        filtered-to (filter-bounced-recipients fixed-to)
        skip-to (clj-set/difference (set fixed-to) (set filtered-to))
        error-msg (when (seq skip-to)
                    (format "Skipping %d recipients because of hard bounce: %s" (count skip-to) (s/join ", " skip-to)))]
    (when (seq filtered-to)
      (timbre/info "Sending email: " to)
      (ses/send-email creds
        :destination {:to-addresses filtered-to}
        :source source
        :reply-to-addresses [fixed-reply-to]
        :message {:subject subject
                  :body html-body}))
    (when error-msg
      (timbre/warn error-msg)
      (slack-lib/slack-report error-msg))))

(defn- email-entry
  "Send emails to all to recipients in parallel."
  [{:keys [to reply-to subject org-slug org-name]} body]
  (doall (pmap #(email {:to %
                        :source (str org-name " <" org-slug "@" c/email-from-domain ">")
                        :reply-to (if (s/blank? reply-to) (default-reply-to) reply-to)
                        :subject subject}
                  {:html body})
            to)))

(defn- inline-css [html-file inline-file]
  (shell/sh "juice" 
            "--web-resources-images" "false"
            "--remove-style-tags" "true"
            "--preserve-media-queries" "true"
            "--preserve-font-faces" "false"
            html-file inline-file))

(defn send-entry
  "Create an HTML email for the specified recipients."
  [entry]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")]
    (try
      (spit html-file (content/share-link-html entry)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email the recipients
      (email-entry entry (slurp inline-file))
      ;; TEXT EMAIL
      ;; (email-entry entry (content/share-link-text entry))
      (finally
        ;; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn send-reminder-notification
  "Create an HTML notification of a created reminder and email it to the specified recipient."
  [message]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        reminder (-> message 
                  (keywordize-keys)
                  (assoc :source (default-source))
                  (assoc :from default-from)
                  (assoc :reply-to (default-reply-to))
                  (assoc :subject (str "🔔 " (content/reminder-notification-headline message))))]
    (try
      (spit html-file (content/reminder-notification-html reminder)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (email reminder {:html (slurp inline-file)})
      (finally
        ;; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn send-reminder-alert
  "Create an HTML email to alert of an upcoming reminder and send it to the specified recipient"
  [message]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        reminder (-> message 
                  (keywordize-keys)
                  (assoc :source (default-source))
                  (assoc :from default-from)
                  (assoc :reply-to (default-reply-to))
                  (assoc :subject (str "🔔 Reminder about your " (:name (:org message)) " post")))]
    (try
      (spit html-file (content/reminder-alert-html reminder)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (email reminder {:html (slurp inline-file)})
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
                    (assoc :source (default-source))
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
              (assoc :source (default-source))
              (assoc :from default-from)
              (assoc :reply-to (default-reply-to))
              (assoc :subject (case token-type
                                  :reset "Reset your password"
                                  :verify "Please verify your email")))]
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
        digest-email-subject (or (:digest-subject msg) (content/digest-title org-name))]
    (try
      (spit html-file (content/digest-html msg)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
      ;; Email it to the recipient
      (email {:to (:email msg)
              :source (default-source)
              :from default-from
              :reply-to (default-reply-to)
              :subject digest-email-subject}
             {:html (slurp inline-file)})
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn send-private-board-notification
  "Creates an HTML private board invite email and sends it to the recipient."
  [msg]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        subject "You’ve been invited to a private section on Carrot"]
    (try
      (spit html-file (content/board-notification-html msg)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
       ;; Email it to the recipient
      (email {:to (-> msg :user :email)
              :source (default-source)
              :from default-from
              :reply-to (default-reply-to)
              :subject (str c/email-digest-prefix subject)}
             {:html (slurp inline-file)})
      (finally
       ;; remove the tmp files
       (io/delete-file html-file true)
       (io/delete-file inline-file true)))))

(defn- post-data-from-msg [msg]
  (let [notification (:notification msg)
        config {:storage-server-url c/storage-server-url
                :auth-server-url c/auth-server-url
                :passphrase c/passphrase
                :service-name "Email"}
        user-data {:user-id (:user-id notification)}]
    (storage/post-data-for config user-data (:slug (:org msg)) (:board-id notification) (:entry-id notification))))

(defn send-entry-notification
  "Creates an HTML email notifying user of being mentioned or replied to and sends it to the recipient."
  [msg]
  (timbre/info "Sending entry notification for:" msg)
  (if-let [post-data (post-data-from-msg msg)]
    (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
          html-file (str uuid-fragment ".html")
          inline-file (str uuid-fragment ".inline.html")
          title (:headline post-data)
          parsed-title (if title (.text (soup/parse title)) "")
          updated-post-data (assoc post-data :parsed-headline parsed-title)
          msg-title (assoc-in msg [:notification :entry-data] updated-post-data)
          pre-subject (content/notify-entry-intro msg)
          subject-length 65
          subject (str pre-subject ": " (subs parsed-title 0 (min (count parsed-title) (- subject-length (count pre-subject)))))
          final-subject (if (= (count subject) subject-length)
                          (str (subs subject 0 (- (count subject) 3)) "...")
                          subject)]
      (try
        (spit html-file (content/notify-entry-html msg-title)) ; create the email in a tmp file
        (inline-css html-file inline-file) ; inline the CSS
        ;; Email it to the recipient
        (email {:to (:to msg)
                :source (default-source)
                :from default-from
                :reply-to (default-reply-to)
                :subject final-subject}
               {:html (slurp inline-file)})
        (finally
        ;; remove the tmp files
          (io/delete-file html-file true)
          (io/delete-file inline-file true))))
    (timbre/warn (str "Could not get JWT for user: " (-> msg :notification :user-id) "."))))

(defn send-team-notification
  "Creates an HTML email notifying user of being added or removed from a team"
  [msg]
  (timbre/info "Sending team notification for:" msg)
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        subject (content/notify-team-intro msg)]
    (try
      (spit html-file (content/notify-team-html msg)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
        ;; Email it to the recipient
      (email {:to (:to msg)
              :source (default-source)
              :from default-from
              :reply-to (default-reply-to)
              :subject subject}
             {:html (slurp inline-file)})
      (finally
        ;; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn send-bot-removed
  "Creates an HTML email notifying user of being mentioned or replied to and sends it to the recipient."
  [msg]
  (timbre/info "Sending Bot removed notification for:" msg)
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        subject content/bot-removed-subject]
    (try
      (spit html-file (content/bot-removed-html msg)) ; create the email in a tmp file
      (inline-css html-file inline-file) ; inline the CSS
       ;; Email it to the recipient
      (email {:to (:to msg)
              :source (default-source)
              :from default-from
              :reply-to (default-reply-to)
              :subject subject}
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
            user (:user msg-parsed)
            note (:note msg-parsed)]

        (doseq [notify notifications]
          (let [slack-info (first (vals (:slack-users notify)))]
            (when-not slack-info ; Slack users get notified elsewhere via Slack
              (let [board-url (s/join "/" [c/web-url
                                           (:slug (:org msg-parsed))
                                           (:slug board)])]
                (send-private-board-notification {:user notify
                                                  :inviter user
                                                  :note note
                                                  :org (:org msg-parsed)
                                                  :board board
                                                  :board-url board-url})))))))))

(comment

  ;; For REPL testing

  (require '[oc.email.mailer :as mailer] :reload)

  ;; To quickly send an email from a local file to check it on email client
  (defn send-email-from-file
    [email-setup html-file-name]
    (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
          inline-html-file (str (subs html-file-name 0 (- (count html-file-name) 4)) "inline.html")]
      (mailer/inline-css html-file-name inline-html-file)
      (mailer/email email-setup {:text "Alternative text for send-test-email."
                                 :html (slurp inline-html-file)})))
  (send-email-from-file email-setup "./hiccup.html")

  (def digest-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/digests/apple.json"))))
  (mailer/send-digest (assoc digest-request :email "change@me.com"))

  (def notification-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/notifications/post-mention.json"))))
  (mailer/send-entry-notification (assoc notification-request :to "change@me.com"))

  (def notification-comment-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/notifications/comment.json"))))
  (mailer/send-entry-notification (assoc notification-comment-request :to "change@me.com"))

  (def notification-comment-mention-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/notifications/comment-mention.json"))))
  (mailer/send-entry-notification (assoc notification-comment-mention-request :to "change@me.com"))

  (def notification-team-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/notifications/team-invite.json"))))
  (mailer/send-tem-notification (assoc notification-request :to "change@me.com"))

  (def share-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/share/bago.json"))))
  (mailer/send-entry (merge share-request {
                       :to ["change@me.com"]
                       :reply-to "change@me.com"
                       :subject "Latest Update"
                       :note "Enjoy this groovy update!"}))

  (def carrot-invite (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/carrot-invites/microsoft.json"))))
  (mailer/send-invite (assoc carrot-invite :to "change@me.com"))

  (def board-invite (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/board-invites/investors.json"))))
  (mailer/send-private-board-notification (assoc board-invite :user {:email "change@me.com"}))

  (def token-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/token/apple.json"))))
  (mailer/send-token :reset (assoc token-request :to "change@me.com"))
  (mailer/send-token :verify (assoc token-request :to "change@me.com"))

  (def reminder-notification-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/reminders/notification.json"))))
  (mailer/send-reminder-notification (assoc reminder-notification-request :to "change@me.com"))

  (def reminder-alert-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/reminders/alert.json"))))
  (mailer/send-reminder-alert (assoc reminder-alert-request :to "change@me.com"))

  (def bot-removed-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/bot-removed/carrot.json"))))
  (mailer/send-bot-removed (assoc bot-removed :to ["admin1@example.com" "admin2@example.com"]))

  ;; Test hard bounce of email address: just send a random email to 
  (def notification-team-request (clojure.walk/keywordize-keys (json/decode (slurp "./opt/samples/notifications/team-invite.json"))))
  (mailer/send-bot-removed (assoc notification-team-request :to "bounce@simulator.amazonses.com")))