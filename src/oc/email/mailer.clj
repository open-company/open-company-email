(ns oc.email.mailer
  (require [clojure.string :as s]
           [clojure.java.shell :as shell]
           [clojure.java.io :as io]
           [oc.email.config :as c]
           [taoensso.timbre :as timbre]
           [amazonica.aws.simpleemail :as ses]
           [oc.email.content :as content]))

(def creds
  {:access-key c/aws-access-key-id
   :secret-key c/aws-secret-access-key
   :endpoint   c/aws-endpoint})

(defn- send-email [to subject body]
  "Send emails to all to recipients in parallel."
  (doall (pmap 
    #(do 
      (timbre/info "Sending email: " %)
      (ses/send-email creds
        :destination {:to-addresses [%]}
        :source (str "snapshot@" c/email-from-domain)
        :message {:subject subject
                  :body {:html body}}))
      (s/split to #","))))

(defn send-snapshot [{api-token :api-token snapshot :snapshot to :to subject :subject note :note :as msg}]
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

  ;; For REPL testing

  (require '[oc.email.mailer :as mailer] :reload)

  (def snapshot (json/decode (slurp "./resources/snapshots/buffer.json")))
  (mailer/send-snapshot {:to "change@me.com"
                         :subject "[Buffer] Latest Update"
                         :note "Enjoy this groovy update!"
                         :snapshot snapshot})

  (def snapshot (json/decode (slurp "./resources/snapshots/open.json")))
  (mailer/send-snapshot {:to "change@me.com,change+1@me.com,change+2@me.com"
                         :subject "[OpenCompany] Check it"
                         :note "Hot diggity!"
                         :snapshot snapshot})

  )