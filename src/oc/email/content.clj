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

(def invite-message "Join your team on Carrot")
(def invite-message-with-company "Join %s on Carrot")
(def invite-instructions "%s has invited you to join the %s digest.")
(def invite-button "Accept invitation")

(def share-message "%s sent you a post on Carrot")
(def share-cta "Read post")

(def board-invite-message "%s invited you to join a private section")
(def board-invite-explainer "%s (%s)")
(def board-invite-button "View %s")

(def reset-message "Password reset")
(def reset-instructions "Click the button below to reset your password. If you didn't request a password reset, you can ignore this email.")
(def reset-button-text "Reset Password")

(def verify-message "Please verify your email")
(def verify-instructions "Welcome to Carrot, your new company digest! Please click the link below to verify your account.")
(def verify-button-text "Verify Email")

;; ----- HTML Fragments -----

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

(defn- fix-avatar-url
  "If the url is pointing to one of our happy faces replace the ending svg with png to have it resizable.
   If it's not use the on the fly resize url."
  [avatar-url]
  (let [r (re-seq #"happy_face_(red|green|blue|purple|yellow).svg$" avatar-url)]
    (if r
      (str (subs avatar-url 0 (- (count avatar-url) 3)) "png")
      (circle-image avatar-url 32))))

(defn- note-author [avatar-url author divider-line?]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-12 large-12"}
        (when divider-line?
          horizontal-line)
        (when divider-line?
          (spacer 16))
        [:img {:class "note-author-avatar"
               :src (fix-avatar-url avatar-url)}]
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

(defn- digest-footer [daily?]
  [:table {:class "row digest-footer-table"}
    [:tr
      [:td {:class "small-1 large-2 columns"}]
      [:td {:class "small-10 large-8 columns"}
        (vspacer 16 "digest-footer-table" "digest-footer-table")
        [:table {:class "row digest-footer-table"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:p {:class "digest-footer-paragraph"}
                "You're receiving this brief "
                (if daily? "daily" "weekly")
                ". Switch to "
                [:a {:href profile-url}
                  (if daily? "weekly" "daily")]
                "? You may also "
                [:a {:href profile-url}
                  "unsubscribe"]
                "."]]]]
        (vspacer 16 "digest-footer-table" "digest-footer-table")
        [:table {:class "row digest-footer-table"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:p {:class "digest-bottom-footer-paragraph"}
                "Sent via Carrot"]]]]
        (vspacer 16 "digest-footer-table" "digest-footer-table")]
      [:td {:class "small-1 large-2 columns"}]]])

(defn- simple-footer []
  [:table {:class "row footer-table"}
    [:tr
      [:td {:class "small-1 large-2 columns"}]
      [:td {:class "small-10 large-8 columns"}
        (vspacer 16 "footer-table footer-top-border" "footer-table")
        [:table {:class "row footer-table"}
          [:tr
            [:th {:class "small-10 large-10 columns"}
              [:p {:class "footer-paragraph bottom-footer"}
                "Sent via "
                [:a {:href config/web-url}
                  "Carrot"]]]
            [:th {:class "small-2 large-2 columns footer-icon"}
              [:a
                {:href config/web-url}
                [:img {:src "https://open-company.s3.amazonaws.com/carrot-logo-grey-min.png"
                       :width "13"
                       :height "24"
                       :alt "Carrot"}]]]]]
        (vspacer 16 "footer-table" "footer-table")]
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
   (spacer 24)
   (post-attribution entry)
   (spacer 24)
   (read-post-button "Read post" (:url entry))])

(defn- posts-with-board-name [board]
  (let [board-name (:name board)]
    (assoc board :posts (map #(assoc % :board-name board-name) (:posts board)))))

(defn- weekly-digest? [digest-data]
  (or (= "weekly" (:digest-frequency digest-data))
      (= :weekly (:digest-frequency digest-data))))

(defn- digest-content [digest]
  (let [logo-url (:logo-url digest)
        logo? (not (s/blank? logo-url))
        weekly? (weekly-digest? digest)
        org-name (:org-name digest)
        title (if weekly? "Your weekly brief" "Your morning brief")
        boards (map posts-with-board-name (:boards digest))
        posts (mapcat :posts boards)
        digests-url (s/join "/" [config/web-url (:org-slug digest) "all-posts"])
        subtitle (str "Good morning! Here are the new posts shared to the " (:org-name digest) " digest this " (if weekly? "past week" "yesterday") ".")]
    [:td {:class "small-10 large-8 columns"}
      (spacer 40)
      (when logo? (org-logo {:org-name (:org-name digest)
                             :org-logo-url logo-url
                             :org-logo-width (:logo-width digest)
                             :org-logo-height (:logo-height digest)}))
      (when logo? (spacer 40))
      (h1 title)
      (spacer 20)
      (paragraph subtitle)
      (spacer 20)
      (left-button "Go to digest" digests-url)
      (mapcat post posts)
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
        fixed-from (if (not (s/blank? from)) "Someone" from)
        invite-message (format board-invite-message from)
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
      (when logo? (spacer 40))
      (h1 invite-message)
      (spacer 40)
      (paragraph (format board-invite-explainer board-name board-url))
      (when show-note? (spacer 40))
      (when show-note? (note-author from-avatar from true))
      (when show-note? (spacer 16))
      (when show-note? (paragraph note))
      (spacer 20)
      (left-button (format board-invite-button board-name) board-url)
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
        invite-message (if (s/blank? org-name)
                         invite-message
                         (format invite-message-with-company org-name))]
    [:td {:class td-classes}
      (spacer 40)
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height}))
      (when logo? (spacer 40))
      (h1 invite-message)
      (spacer 30)
      (paragraph (format invite-instructions from org-name))
      (spacer 40)
      (when show-note? (note-author from-avatar from true))
      (when show-note? (spacer 16))
      (when show-note? (paragraph note))
      (spacer 20)
      (left-button invite-button (:token-link invite))
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
      (h1 (format share-message from))
      (when show-note? (spacer 40))
      (when show-note? (note-author from-avatar from false))
      (when show-note? (spacer 16))
      (when show-note? (paragraph note))
      (spacer 40)
      (h2 headline)
      (spacer 8)
      (post-attribution entry)
      (spacer 8)
      (read-post-button share-cta entry-url)
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
    :link (:token-link (keywordize-keys msg))})

(defn- token-content [td-classes token-type msg]
  (let [message (token-prep token-type msg)
        logo-url (:org-logo-url msg)
        logo? (not (s/blank? logo-url))
        org-name (:org-name msg)]
    [:td {:class td-classes}
      (spacer 40)
      (when logo? (org-logo msg))
      (when logo? (spacer 40))
      (h2 (:message message))
      (spacer 40)
      (paragraph (:instructions message))
      (spacer 20)
      (left-button (:button-text message) (:token-link msg))
      (spacer 72)]))

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
            (case type
              :digest (digest-footer (not (weekly-digest? data)))
              (simple-footer))]]]]))

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
         (:link message))))

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