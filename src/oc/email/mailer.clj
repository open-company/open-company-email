(ns oc.email.mailer
  (:require [clojure.string :as s]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.walk :refer (keywordize-keys)]
            [oc.email.config :as c]
            [taoensso.timbre :as timbre]
            [amazonica.aws.simpleemail :as ses]
            [oc.email.content :as content]
            [oc.email.invite :as invite]))

(def creds
  {:access-key c/aws-access-key-id
   :secret-key c/aws-secret-access-key
   :endpoint   c/aws-endpoint})

(def default-reply-to (str "hello@" c/email-from-domain))
(def default-inviter "OpenCompany")

(defn- email-snapshot
  "Send emails to all to recipients in parallel."
  [{to :to reply-to :reply-to subject :subject snap :snapshot} body]
  (let [snapshot (keywordize-keys snap)
        company-slug (:company-slug snapshot)
        company-name (:name snapshot)]
    (doall (pmap 
      #(do 
        (timbre/info "Sending email: " %)
        (ses/send-email creds
          :destination {:to-addresses [%]}
          :source (str company-name "<" company-slug "@" c/email-from-domain ">")
          :reply-to-addresses [(if (s/blank? reply-to) default-reply-to reply-to)]
          :message {:subject subject
                    :body {:html body}}))
        to))))

(defn send-snapshot [{note :note snapshot :snapshot :as msg}]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")]
    (try
      (spit html-file (content/html (assoc snapshot :note note))) ; create the email in a tmp file
      (shell/sh "juice" html-file inline-file) ; inline the CSS
      (email-snapshot msg (slurp inline-file)) ; email it to the recipients
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(defn- email-invite
  [{:keys [to reply-to subject company-name]} body]
  (timbre/info "Sending email: " to)
  (ses/send-email creds
    :destination {:to-addresses [to]}
    :source (str default-inviter " <" default-reply-to ">")
    :reply-to-addresses [(if (s/blank? reply-to) default-reply-to reply-to)]
    :message {:subject subject
              :body {:text (:text body)}}))

(defn send-invite [message]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")
        msg (keywordize-keys message)
        company-name (:company-name msg)
        from (:from msg)
        prefix (if (s/blank? from) "You've been invited" (str from " invites you"))
        company (if (s/blank? company-name) "" (str company-name " on "))
        subject (str prefix " to join " company "OpenCompany")
        invitation (assoc msg :subject subject)]
    (try
      (spit html-file (invite/html invitation)) ; create the email in a tmp file
      (shell/sh "juice" html-file inline-file) ; inline the CSS
      (email-invite invitation {:text (invite/text invitation)
                                :html (slurp inline-file)}) ; email it to the recipients
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(comment

  ;; For REPL testing

  (require '[oc.email.mailer :as mailer] :reload)

  (def snapshot (json/decode (slurp "./opt/samples/updates/green-labs.json")))
  (mailer/send-snapshot {:to ["change@me.com"]
                         :reply-to "change@me.com"
                         :subject "Latest GreenLabs Update"
                         :note "Enjoy this groovy update!"
                         :snapshot (assoc snapshot :company-slug "green-labs")})

  (def invite (json/decode (slurp "./opt/samples/invites/apple.json")))
  (mailer/send-invite (assoc invite :to "change@me.com"))

)