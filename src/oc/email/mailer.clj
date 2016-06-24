(ns oc.email.mailer
  (require [clojure.java.shell :as shell]
           [clojure.java.io :as io]
           [environ.core :as e]
           [taoensso.timbre :as timbre]
           [amazonica.aws.simpleemail :as ses]
           [oc.email.content :as content]))

(def creds
  {:access-key (e/env :aws-access-key-id)
   :secret-key (e/env :aws-secret-access-key)
   :endpoint   (e/env :end-point)})

(defn- send-email [to subject body]
  (timbre/info "Sending email.")
  (ses/send-email creds
                  :destination {:to-addresses [to]}
                  :source (str "snapshot@" (e/env :email-from-domain))
                  :message {:subject subject
                            :body {:html body}}))

(defn send-snapshot [{:keys [snapshot to subject note]}]
  (let [uuid-fragment (subs (str (java.util.UUID/randomUUID)) 0 4)
        html-file (str uuid-fragment ".html")
        inline-file (str uuid-fragment ".inline.html")]
    (try
      (spit html-file (content/html (assoc snapshot :note note))) ; create the email in a tmp file
      (shell/sh "juice" html-file inline-file) ; inline the CSS
      (send-email to subject (slurp inline-file)) ; email it to the recipients
      (finally
        ; remove the tmp files
        (io/delete-file html-file true)
        (io/delete-file inline-file true)))))

(comment

  (require '[oc.email.mailer :as mailer] :reload)

  (def snapshot (json/decode (slurp "./resources/snapshots/buffer.json")))
  (mailer/send-snapshot {:to "change@me.com"
                         :subject "[Buffer] Latest Update"
                         :note "Enjoy!"
                         :snapshot snapshot})

  (def snapshot (json/decode (slurp "./resources/snapshots/open.json")))
  (mailer/send-snapshot {:to "change@me.com"
                         :subject "[OpenCompany] Check it"
                         :note "Hot diggity!"
                         :snapshot snapshot})

  )