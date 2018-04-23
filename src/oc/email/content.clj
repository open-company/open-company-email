(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.format :as time-format]
            [hiccup.core :as h]
            [oc.email.config :as config]))

(def max-logo 32)
(def author-logo 32)

(def iso-format (time-format/formatters :date-time))
(def date-format (time-format/formatter "MMMM d"))
(def date-format-year (time-format/formatter "MMMM d YYYY"))

(def profile-url (str config/web-url "/profile"))

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

;; Links

(def carrot-hello-mailto "mailto:hello@carrot.io")

(def carrot-help "http://help.carrot.io")

;; ----- Copy -----

(def carrot-explainer "Carrot is the company digest that keeps everyone aligned around what matters most.")

(def invite-message "invited you to his team on Carrot")
(def invite-message-with-company "invited you to join the “%s” digest on Carrot")
(def invite-button "Accept invitation")
(def invite-help-links
  [:p {:class "digest-help"}
    "Visit our "
    [:a {:href carrot-help}
      "getting started guide"]
    " for tips and tricks on how to get the most out of Carrot."])

(def share-message "sent you a post")
(def share-cta "Read post")
(def share-help-links
  [:p {:class "digest-help"}
    "Not sure what a post is? Head over to our "
    [:a {:href carrot-help} "support center"]
    " to learn more"])

(def board-invite-message "invited you to join the private section “%s” within your company digest")
(def anonymous-board-invite-message "You've been invited to join the private section “%s” within your company digest")
(def board-invite-explainer "Private sections of the digest are only available to invited team members.")
(def board-invite-help-links
  [:p {:class "digest-help"}
    "Not sure what a private section is? Head over to our "
    [:a {:href carrot-help} "support center"]
    " to learn more"])

(def reset-message "Password reset")
(def reset-instructions "Click the button below to reset your password. If you didn't request a password reset, you can ignore this email.")
(def reset-button-text "Reset Password")
(def reset-ignore "If you didn't request a password reset, you can safely ignore this email.")
(def reset-help-links
  [:p {:class "digest-help"}
    [:a {:href carrot-hello-mailto} "Contact us"]
    " or visit our "
    [:a {:href carrot-help} "support center"]
    " if you’re in need of assistance"])

(def verify-message "Please verify your email for Carrot")
(def verify-instructions "Click the link below to verify your email address.")
(def verify-button-text "Verify Email")
(def verify-ignore "If you didn't create a Carrot account, you can safely ignore this email.")
(def verify-help-links
  [:p {:class "digest-help"}
    [:a {:href carrot-hello-mailto} "Contact us"]
    " or visit our "
    [:a {:href carrot-help} "support center"]
    " if you’re in need of assistance"])

(def digest-help-links
  [:p {:class "digest-help"}
    "You’re receing this digest weekly. You can change your "
    [:a {:href profile-url} "delivery frequency"]
    "."])


;; ----- HTML Fragments -----

(defn- help-links [token-type]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        (case token-type
          :reset reset-help-links
          :verify verify-help-links
          :invite invite-help-links
          :board-notification board-invite-help-links
          :share-link share-help-links
          :digest digest-help-links)]]])

(defn- org-logo
  [{org-name :org-name logo-url :org-logo-url logo-height :org-logo-height logo-width :org-logo-width}]
  (let [logo? (and logo-url logo-height logo-width)
        dimension (when logo? (if (> logo-height logo-width) :height :width))
        size (when logo?
                (if (= dimension :height)
                  (if (> logo-height max-logo) max-logo logo-height)
                  (if (> logo-width max-logo) max-logo logo-width)))]
    (when logo?
      [:table {:class "row logo"}
        [:tr 
          [:th {:class "small-12 large-12 first last columns"}
            [:table 
              [:tr 
                [:th
                  [:img {:class "float-left logo"
                         :align "left"
                         :style (str "background-color: #fff; max-height: " max-logo "px; max-width: " max-logo "px;")
                         dimension size
                         :src logo-url
                         :alt (str org-name " logo")}]]]]]]])))

(defn- spacer-table [pixels css-class]
  [:table {:class "spacer"}
    [:tr
      [:th {:class css-class
            :height (str pixels "px")
            :style (str "font-size:" pixels "px; line-height:" pixels "px;")} " "]]])

(defn- spacer
  ([pixels] (spacer pixels ""))
  ([pixels outer-css-class] (spacer pixels outer-css-class ""))
  ([pixels outer-css-class inner-css-class]
  [:table {:class (str "row " outer-css-class)}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        (spacer-table pixels inner-css-class)]]]))

(defn- vspacer
  ([pixels] (spacer pixels ""))
  ([pixels outer-css-class] (spacer pixels outer-css-class ""))
  ([pixels outer-css-class inner-css-class]
  [:table {:class (str "row " outer-css-class)}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        (spacer-table pixels inner-css-class)]]]))

(def horizontal-line
  [:table {:class "row horizontal-line"}
    [:tr
      [:td {:class "small-12 large-12"}
        (spacer 8)]]])

(defn- paragraph
  ([content] (paragraph content ""))
  ([content css-class] (paragraph content css-class "text-left"))
  ([content css-class content-css-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:p {:class content-css-class} content]]]]))

(defn- h1 [content]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:h1 {:class "text-left"} content]]]])

(defn- h2
  ([content] (h2 content "" ""))
  ([content css-class] (h2 content css-class "text-left"))
  ([content css-class h2-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-10 large-8 columns"}
        [:h2 {:class h2-class} content]]
      [:th {:class "small-1 large-2 last columns"}]]]))

(defn- circle-image
  "Return an on the fly url of the image circle and resized."
  [image-url size]
  ;; Filestack URL https://cdn.filestackcontent.com/qemc9YslR9yabfqL4GTe
  (let [filestack-static-url "https://cdn.filestackcontent.com/"
        is-filestack-resource? (clojure.string/starts-with? image-url filestack-static-url)
        filestack-resource (if is-filestack-resource?
                             (subs image-url (count filestack-static-url))
                             image-url)]
    (str "https://process.filestackapi.com/"
         (when-not is-filestack-resource?
           (str config/filestack-api-key "/"))
         "resize=w:" author-logo ",h:" author-logo ",fit:crop,align:faces/"
         "circle/"
         filestack-resource)))

(defn- note-author [avatar-url author]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-12 large-12"}
        [:img {:class "note-author-avatar" :src (circle-image avatar-url 32)}]
        [:span {:class "note-author-name"} author]]]])

(defn- read-post-button [link-text link-url]
  [:a {:href link-url}
    [:img {:src "https://open-company.s3.amazonaws.com/read_post.png"
           :class "read-post-image"
           :width 16
           :height 16}]
    [:span {:class "read-post-text"}
      link-text]])

(defn- left-button [cta-text url]
  [:a {:href url}
    [:span {:class "cta-text"}
      cta-text]])

;; Comments not shown in digests at the moment
; (defn- comment-attribution [comment-count comment-authors]
;   (let [attribution (text/attribution 2 comment-count "comment" comment-authors)]
;     [:tr
;       [:th {:class "small-1 large-1 first columns"}]
;       [:th {:class "small-10 large-10 columns"}
;         [:table
;           [:tr
;             [:th
;               [:p {:class "attribution"} attribution]]]]]
;       [:th {:class "small-1 large-1 last columns"}]]))

(defn- transactional-footer []
  [:table {:class "row footer-table"}
    [:tr
      [:td {:class "small-1 large-2 columns"}]
      [:td {:class "small-10 large-8 columns"}
        (vspacer 15 "footer-table" "footer-table")
        [:table {:class "row footer-table"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:p {:class "footer-paragraph"}
                "You can "
                [:a {:href profile-url} "unsubscribe"]
                " to these emails or update your "
                [:a {:href profile-url} "notification settings"]
                " at anytime."]]]]
        (vspacer 15 "footer-table" "footer-table")
        [:table {:class "row footer-table"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:p {:class "footer-paragraph bottom-footer"}
                "Sent with love from Carrot"]]]]
        (vspacer 15 "footer-table" "footer-table")]
      [:td {:class "small-1 large-2 columns"}]]])

;; ----- Posts common ----

(defn- post-date [timestamp]
  (let [d (time-format/parse iso-format timestamp)]
    (time-format/unparse date-format d)))

(defn- post-attribution [entry]
  (paragraph (str "Posted by " (-> entry :publisher :name) " in " (:board-name entry) " on " (post-date (:published-at entry)))
   "body-block" "text-left attribution"))

;; ----- Digest -----



(defn- post [entry]
  [(spacer 32)
   horizontal-line
   (spacer 32)
   (h2 (:headline entry))
   (spacer 16)
   (post-attribution entry)
   (spacer 16)
   (read-post-button "Read post" (:url entry))])

(defn- posts-with-board-name [board]
  (let [board-name (:name board)]
    (assoc board :posts (map #(assoc % :board-name board-name) (:posts board)))))

(defn- digest-content [digest]
  (let [logo-url (:logo-url digest)
        logo? (not (s/blank? logo-url))
        weekly? (= "weekly" (:digest-frequency digest))
        org-name (:org-name digest)
        title (if weekly? (str org-name " Weekly Digest") (str org-name " Daily Digest"))
        boards (map posts-with-board-name (:boards digest))
        posts (mapcat :posts boards)]
    [:td {:class "small-10 large-8 columns"}
      (spacer 40)
      (when logo? (org-logo {:org-name (:org-name digest)
                             :org-logo-url logo-url
                             :org-logo-width (:logo-width digest)
                             :org-logo-height (:logo-height digest)}))
      (when logo? (spacer 40))
      (h1 title)
      (mapcat post posts)
      (spacer 24)
      horizontal-line
      (help-links :digest)
      (spacer 73)]))

;; ----- Transactional Emails -----

(defn- board-notification-content [notice]
  (let [org (:org notice)
        org-name (:name org)
        logo-url (:logo-url org)
        logo-width (:logo-width org)
        logo-height (:logo-height org)
        logo? (not (s/blank? logo-url))
        board-url (:board-url notice)
        board-name (-> notice :board :name)
        first-name (-> notice :inviter :first-name)
        last-name (-> notice :inviter :last-name)
        from (s/join " " [first-name last-name])
        invite-message (if (s/blank? from) anonymous-board-invite-message (str (s/trim from) " " board-invite-message))
        formatted-invite-message (format invite-message from)
        from-avatar (-> notice :inviter :avatar-url)
        from-avatar? (not (s/blank? from-avatar))
        note (:note notice)
        note? (not (s/blank? note))
        show-note? (and from-avatar? note?)]
    [:td {:class "small-10 large-8 columns"}
      (spacer 40)
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height}))
      (when logo? (spacer 35))
      (h1 formatted-invite-message)
      (spacer 35)
      (when show-note? (note-author from-avatar from))
      (when show-note? (spacer 16))
      (when show-note? (paragraph note))
      (spacer 35)
      (left-button "Accept invitation" board-url)
      (spacer 24)
      horizontal-line
      (help-links :invite)
      (spacer 73)]))

(defn- invite-content [td-classes invite]
  (let [logo-url (:org-logo-url invite)
        logo-width (:org-logo-width invite)
        logo-height (:org-logo-height invite)
        logo? (not (s/blank? logo-url))
        org-name (:org-name invite)
        from (if (s/blank? (:from invite)) "Someone" (:from invite))
        from-avatar (:from-avatar invite)
        from-avatar? (not (s/blank? from-avatar))
        note (:note invite)
        note? (not (s/blank? note))
        show-note? (and from-avatar? note?)
        invite-message (str from " "
                        (if (s/blank? org-name)
                          invite-message
                          (format invite-message-with-company org-name)))]
    [:td {:class td-classes}
      (spacer 40)
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height}))
      (when logo? (spacer 35))
      (h1 invite-message)
      (spacer 20)
      (when show-note? (note-author from-avatar from))
      (when show-note? (spacer 16))
      (when show-note? (paragraph note))
      (spacer 35)
      (left-button invite-button (:token-link invite))
      (spacer 24)
      horizontal-line
      (help-links :invite)
      (spacer 73)]))

(defn- share-content [entry]
  (let [logo-url (:org-logo-url entry)
        logo? (not (s/blank? logo-url))
        org-name (:org-name entry)
        org-name? (not (s/blank? org-name))
        headline (:headline entry)
        org-slug (:org-slug entry)
        sharer (:sharer-name entry)
        publisher (:publisher entry)
        attribution (str (:name publisher) " posted to " (:board-name entry))
        note (:note entry)
        note? (not (s/blank? note))
        from (if (s/blank? sharer) "Someone" sharer)
        from-avatar (:sharer-avatar-url entry)
        from-avatar? (not (s/blank? from-avatar))
        show-note? (and note? from-avatar?)
        secure-uuid (:secure-uuid entry)
        origin-url config/web-url
        entry-url (s/join "/" [origin-url org-slug "post" secure-uuid])]
    [:td {:class "small-10 large-8 columns"}
      (spacer 40)
      (when logo? (org-logo entry))
      (when logo? (spacer 40))
      (h1 (str from " " share-message))
      (when show-note? (spacer 40))
      (when show-note? (note-author from-avatar from))
      (when show-note? (spacer 16))
      (when show-note? (paragraph note))
      (spacer 40)
      (h2 headline)
      (spacer 8)
      (post-attribution entry)
      (spacer 8)
      (read-post-button share-cta entry-url)
      (spacer 24)
      horizontal-line
      (help-links :share-link)
      (spacer 73)]))

(defn- token-prep [token-type msg]
  {
    :message (case token-type
              :reset reset-message
              :verify verify-message)
    :instructions (case token-type
                    :reset reset-instructions
                    :verify verify-instructions)
    :button-text (case token-type
                    :reset reset-button-text
                    :verify verify-button-text)
    :link (:token-link (keywordize-keys msg))
    :ignore (case token-type
                :reset reset-ignore
                :verify verify-ignore)})

(defn- token-content [td-classes token-type msg]
  (let [message (token-prep token-type msg)
        logo-url (:org-logo-url msg)
        logo? (not (s/blank? logo-url))
        org-name (:org-name msg)]
    [:td {:class td-classes}
      (spacer 40)
      (when logo? (org-logo msg))
      (when logo? (spacer 35))
      (spacer 35)
      (h2 (:message message))
      (spacer 28)
      (paragraph (:instructions message))
      (spacer 35)
      (left-button (:button-text message) (:token-link msg))
      (spacer 35)
      (paragraph (:ignore message))
      (spacer 35)
      horizontal-line
      (help-links token-type)
      (spacer 40)
      (spacer 33)]))

;; ----- General HTML, common to all emails -----

(defn- body [data]
  (let [type (:type data)]
    [:body
      [:table {:class "body"}
        [:tr
          [:td
            [:table {:class "row"}
              [:tr
                [:td {:class "small-1 large-2 columns"}]
                (case type
                  :reset (token-content "small-10 large-8 columns" type data)
                  :verify (token-content "small-10 large-8 columns" type data)
                  :invite (invite-content "small-10 large-8 columns" data)
                  :board-notification (board-notification-content data)
                  :share-link (share-content data)
                  :digest (digest-content data))
                [:td {:class "small-1 large-2 columns"}]]]
            (transactional-footer)]]]]))

(defn- head [data]
  [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
    [:head 
      [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
      [:meta {:name "viewport", :content "width=device-width"}]
      [:link {:rel "stylesheet", :href "resources/css/foundation.css"}]
      [:link {:rel "stylesheet", :href (str "resources/css/oc.css")}]
      [:title]]
    (body data)])

(defn- html [data type]
  (str doc-type (h/html (head (assoc (keywordize-keys data) :type type)))))

;; ----- External -----

(defn invite-subject [invite bold?]
  (let [msg (keywordize-keys invite)
        org-name (if bold? (str "<b>" (:org-name msg) "</b>") (:org-name msg))
        from (:from msg)
        prefix (if (s/blank? from) "You've been invited" (str from " invited you"))
        org (if (s/blank? org-name) "" (str org-name " on "))]
    (str prefix " to join " org "Carrot")))

(defn share-link-html [entry]
  (html entry :share-link))

(defn share-link-text [entry]
  (let [note (:note entry)
        note? (not (s/blank? note))
        org-name (:org-name entry)
        org-name? (not (s/blank? org-name))
        headline (:headline entry)
        headline? (not (s/blank? headline))
        org-slug (:org-slug entry)
        secure-uuid (:secure-uuid entry)
        origin-url config/web-url
        update-url (s/join "/" [origin-url org-slug "post" secure-uuid])]
    (str (when note? (str note "\n\n"))
         "Check out the latest" (when org-name? (str " from " org-name)) ":"
         "\n\n" (when headline? (str headline "- ")) update-url)))

(defn invite-html [invite]
  (html (-> invite
          (assoc :subject (invite-subject invite false))
          (assoc :text (invite-subject invite true))) :invite))

(defn invite-text [invite]
  (let [link (:token-link (keywordize-keys invite))]
    (str (invite-subject invite false) ".\n\n"
         carrot-explainer "\n\n"
         "Open the link below to check it out.\n\n"
         link "\n\n")))

(defn token-html [token-type msg]
  (html msg token-type))

(defn token-text [token-type msg]
  (let [message (token-prep token-type msg)]
    (str (:message message) "\n\n"
         (:instructions message) "\n\n"
         (:link message) "\n\n"
         (:ignore message))))

(defn digest-html [digest]
  (html digest :digest))

(defn board-notification-html [message]
  (html message :board-notification))

;; ----- REPL Usage -----

(comment
  
  ;; For REPL testing and content development

  (require '[hickory.core :as hickory])
  (require '[oc.email.content :as content] :reload)

  ;; Recreate hiccup from various HTML fragments

  (defn clean-html [data]
    (-> data
      (s/replace "  " "")
      (s/replace "\n" "")
      (s/replace "\t" "")))

  ;; Generate test email HTML content from sample data

  (require '[oc.email.content :as content] :reload)

  ;; Carrot invites

  (def data (clean-html (slurp "./resources/invite/paragraph.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/invite/cta-button.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def invite (json/decode (slurp "./opt/samples/carrot-invites/apple.json")))
  (spit "./hiccup.html" (content/invite-html invite))

  (def invite (json/decode (slurp "./opt/samples/carrot-invites/microsoft.json")))
  (spit "./hiccup.html" (content/invite-html invite))
  (content/invite-text invite)

  (def invite (json/decode (slurp "./opt/samples/carrot-invites/combat.json")))
  (spit "./hiccup.html" (content/invite-html invite))
  (content/invite-text invite)

  (def invite (json/decode (slurp "./opt/samples/carrot-invites/sparse-data.json")))
  (spit "./hiccup.html" (content/invite-html invite))
  (content/invite-text invite)

  ;; Private board invites

  (def notification (json/decode (slurp "./opt/samples/board-invites/investors.json")))
  (spit "./hiccup.html" (content/board-notification-html notification))

  ;; Shares

  (def note "Enjoy the groovy update.")
  (def share-request (json/decode (slurp "./opt/samples/share/bago.json")))
  (spit "./hiccup.html" (content/share-link-html (assoc share-request :note note)))

  ;; Resets

  (def token-request (json/decode (slurp "./opt/samples/token/apple.json")))
  
  (spit "./hiccup.html" (content/token-html :reset token-request))
  (content/token-text :reset token-request)

  (spit "./hiccup.html" (content/token-html :verify token-request))
  (content/token-text :verify token-request)

  (def token-request (json/decode (slurp "./opt/samples/token/sparse-data.json")))

  (spit "./hiccup.html" (content/token-html :reset token-request))
  (content/token-text :reset token-request)

  (spit "./hiccup.html" (content/token-html :verify token-request))
  (content/token-text :verify token-request)

  ;; Digests

  (def data (clean-html (slurp "./resources/digest/logo.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/banner.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/board-name.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/post-link.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/post-attribution.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/carrot-logo.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/sent-by.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/you-receive.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/digest/change-to.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def digest (json/decode (slurp "./opt/samples/digests/carrot.json")))
  (spit "./hiccup.html" (content/digest-html digest))

  (def digest (json/decode (slurp "./opt/samples/digests/apple.json")))
  (spit "./hiccup.html" (content/digest-html digest))

  (def digest (json/decode (slurp "./opt/samples/digests/no-logo.json")))
  (spit "./hiccup.html" (content/digest-html digest))

  )