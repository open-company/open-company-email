(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.format :as time-format]
            [hiccup.core :as h]
            [hickory.core :as hickory]
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
(def invite-message-with-company "Join the %s team on Carrot")
(def invite-instructions "%s has invited you to Carrot - a leadership communication platform that keeps teams focused on what matters.")
(def invite-button "accept_invitation")

(def share-message "%s shared a post with you")
(def share-cta "read_post")

(def board-invite-message "%s has invited you to join a private section on Carrot called “")
(def board-invite-message-2 "”.")
(def board-invite-message-3 "Carrot is a leadership communication platform that keeps everyone focused on what matters.")
; "%s invited you to join a private section")
(def board-invite-button "view_section")

(def reset-message "Password reset")
(def reset-instructions "Click the button below to reset your password. If you didn't request a password reset, you can ignore this email.")
(def reset-button-text "reset_password")

(def verify-message "Please verify your email")
(def verify-instructions "Welcome to Carrot! Carrot helps leaders rise above noisy chat and email to keep teams aligned.")
(def verify-instructions-2 "Please click the link below to verify your account.")
(def verify-button-text "verify_email")

(def digest-weekly-title "Your weekly brief")
(def digest-daily-title "Your daily brief")
(def digest-message "Hi %s, here are the new posts from your team.")
(def digest-message-no-name "Here are the new posts from your team.")

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
  ([content css-class content-css-class & [inner-th-class]]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class (str "small-12 large-12 columns " inner-th-class)}
        [:p {:class content-css-class} content]]]]))

(defn- h1 [content]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:h1 {:class "text-left"} content]]]])

(defn- h2
  ([content entry-url] (h2 content entry-url "" ""))
  ([content entry-url css-class] (h2 content entry-url css-class "text-left"))
  ([content entry-url css-class h2-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:a {:href entry-url}
          [:h2 {:class h2-class} content]]]]]))

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
  "
  First it fixes relative URLs, it prepends our production CDN domain to it if it's relative.
  Then if the url is pointing to one of our happy faces, it replaces the SVG extension with PNG
  to have it resizable. If it's not one of our happy faces, it uses the on-the-fly resize url."
  [avatar-url]
  (let [absolute-avatar-url (if (s/starts-with? avatar-url "/img")
                              (str "https://d1wc0stj82keig.cloudfront.net" avatar-url)
                              avatar-url)
        r (re-seq #"happy_face_(red|green|blue|purple|yellow).svg$" absolute-avatar-url)]
    (if r
      (str (subs absolute-avatar-url 0 (- (count absolute-avatar-url) 3)) "png")
      (circle-image absolute-avatar-url 32))))

(defn- note-author [author & [avatar-url]]
  [:table {:class "row note-paragraph"}
    [:tr
      [:th {:class "small-12 large-12"}
        (when avatar-url
          [:img
            {:class "note-author-avatar note-left-padding"
             :src avatar-url}])
        [:span
          {:class (str "note-author-name " (when-not avatar-url "note-left-padding"))}
          author]]]])

(defn- left-button
  "Image of the green button or read post button.
  The image has to be retina and set only the width like this:
  <img alt=\"Litmus\"
       src=\"hero@2x.png\"
       width=\"600\"
       style=\"width: 100%;
               max-width: 600px;
               font-family: sans-serif;
               color: #ffffff;
               font-size: 20px;
               display: block;
               border: 0px;\"
       border=\"0\">
  As suggested here:
  https://litmus.com/blog/understanding-retina-images-in-html-email"
  [cta-text url & [css-class]]
  (let [image-width (case cta-text
                    "go_to_digest" 136
                    "reset_password" 159
                    "accept_invitation" 142
                    "verify_email" 120
                    "view_section" 184
                    ;; default "read_post"
                    112)]
    [:a {:href url
         :class (str "green-button " css-class)}
      (case (keyword cta-text)
        :go_to_digest
        "Go to digest"
        :reset_password
        "Reset password"
        :accept_invitation
        "Accept invite"
        :verify_email
        "Verify email"
        :view_section
        "View private section"
        ;; default "read_post"
        "View post")]))

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

(defn- email-header [& [right-copy]]
  [:table {:class "row header-table"
           :valign "middle"
           :align "center"}
    [:tr
      [:td {:class "small-1 hide-for-large columns" :valign "middle" :align "left"}]
      [:td {:class "small-10 large-12 columns" :valign "middle" :align "center"}
        (vspacer 24 "header-table" "header-table")
        [:table {:class "row header-table"}
          [:tr
            [:th {:class "small-2 large-2 columns header-icon"}
              [:a
                {:href config/web-url}
                [:img {:src (str config/email-images-prefix "/email_images/carrot_logo_with_copy_colors.png")
                       :srcSet (str config/email-images-prefix "/email_images/carrot_logo_with_copy_colors@2x.png 2x")
                       :width "74"
                       :height "18"
                       :alt "Carrot"}]]]
            [:th {:class "small-10 large-10 columns"}
              [:p {:class "header-paragraph top-header"}
                "Leadership communication"]]]]
        (vspacer 24 "header-table header-bottom-border" "header-table")
        (vspacer 40 "header-table" "header-table")]
      [:td {:class "small-1 hide-for-large columns" :valign "middle" :align "right"}]]])

(defn- email-footer []
  [:table {:class "row footer-table"
           :valign "middle"
           :align "center"}
    [:tr
      [:td {:class "small-1 hide-for-large columns" :valign "middle" :align "left"}]
      [:td {:class "small-10 large-12 columns" :valign "middle" :align "center"}
        (vspacer 24 "footer-table" "footer-table")
        (vspacer 24 "footer-table footer-top-border" "footer-table")
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
                [:img {:src (str config/email-images-prefix "/email_images/carrot_logo_grey_email.png")
                       :srcSet (str config/email-images-prefix "/email_images/carrot_logo_grey_email@2x.png 2x")
                       :width "13"
                       :height "24"
                       :alt "Carrot"}]]]]]
        (vspacer 40 "footer-table" "footer-table")]
      [:td {:class "small-1 hide-for-large columns" :valign "middle" :align "right"}]]])

;; ----- Posts common ----

(defn- post-date [timestamp]
  (let [d (time-format/parse iso-format timestamp)]
    (time-format/unparse date-format d)))

(defn- post-attribution [entry show-board? & [css-class]]
  (paragraph
    (str (-> entry :publisher :name)
         (when show-board?
          " in ")
         (when show-board?
          (:board-name entry))
         " • " (post-date (:published-at entry)))
   css-class "text-left attribution"))

(defn- post-block [entry entry-url]
  (let [publisher (:publisher entry)
        avatar-url (fix-avatar-url (:avatar-url publisher))]
    [:div.post-block
      (when avatar-url
        [:img.post-avatar
          {:src avatar-url}])
      (h2 (:headline entry) entry-url "post-right-side")
      (spacer 4 "post-right-side")
      (post-attribution entry true "post-right-side")]))

;; ----- Digest -----

(defn- post [entry]
  [horizontal-line
   (spacer 24)
   (h2 (:headline entry) (:url entry))
   (spacer 4)
   (post-attribution entry true)
   (spacer 24)])

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
        title (if weekly? digest-weekly-title digest-daily-title)
        boards (map posts-with-board-name (:boards digest))
        posts (mapcat :posts boards)
        digest-url (s/join "/" [config/web-url (:org-slug digest) "all-posts"])
        first-name (:first-name digest)
        subtitle (if (s/blank? first-name)
                    digest-message-no-name
                    (format digest-message first-name))]
    [:td {:class "small-10 large-12 columns" :valign "middle" :align "center"}
      (spacer 40)
      (when logo? (org-logo {:org-name (:org-name digest)
                             :org-logo-url logo-url
                             :org-logo-width (:logo-width digest)
                             :org-logo-height (:logo-height digest)}))
      (when logo? (spacer 32))
      (h1 title)
      (spacer 24)
      (paragraph subtitle)
      (spacer 40)
      (mapcat post posts)
      (spacer 56)
      [:table {:class "row"}
        [:tr
          [:th {:class "small-12 large-12 columns"}
            [:p {:class "digest-footer-paragraph"}
              "You're receiving this brief "
              (if weekly? "weekly" "daily")
              ". "
              [:a {:href profile-url}
                (str "Switch to " (if weekly? "daily" "weekly"))]
              "? You may also "
              [:a {:href profile-url}
                "unsubscribe"]
              "."]]]]]))

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
        fixed-from (if-not (s/blank? from) "Someone" from)
        from-avatar (-> notice :inviter :avatar-url)
        from-avatar? (not (s/blank? from-avatar))
        note (:note notice)
        note? (not (s/blank? note))
        show-note? (and from-avatar? note?)]
    [:td {:class "small-10 large-12 columns" :valign "middle" :align "center"}
      (spacer 40)
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height}))
      (when logo? (spacer 32))
      [:table {:class "row"}
        [:tr
          [:th {:class "small-12 large-12 columns"}
            [:p {:class "text-left"}
              (format board-invite-message from)
              [:a {:href board-url}
                board-name]
              board-invite-message-2]]]]
      (spacer 24)
      (paragraph board-invite-message-3)
      (spacer 24)
      (when show-note? (spacer 8 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph"))
      (when show-note? (note-author from))
      (when show-note? (spacer 16 "note-paragraph" "note-paragraph"))
      (when show-note? (paragraph note "note-paragraph"))
      (when show-note? (spacer 16 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph"))
      (when show-note? (spacer 16))
      (left-button board-invite-button board-url)
      (spacer 56)]))

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
        invite-message (if (s/blank? org-name)
                         invite-message
                         (format invite-message-with-company org-name))]
    [:td {:class td-classes :valign "middle" :align "center"}
      (spacer 40)
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height}))
      (when logo? (spacer 32))
      (h1 invite-message)
      (spacer 24)
      (paragraph (format invite-instructions from))
      (spacer 16)
      (when note? (spacer 8 "note-paragraph top-note-paragraph" "note-paragraph"))
      (when note? (note-author from))
      (when note? (spacer 16 "note-paragraph" "note-paragraph"))
      (when note? (paragraph note "note-paragraph"))
      (when note? (spacer 16 "note-paragraph bottom-note-paragraph" "note-paragraph"))
      (when note? (spacer 16))
      [:a
        {:class "token-link"
         :href (:token-link invite)}
        (:token-link invite)]
      (spacer 56)]))

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
        entry-url (s/join "/" [config/web-url org-slug "post" secure-uuid])]
    [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
      (spacer 40)
      (when logo? (org-logo entry))
      (when logo? (spacer 32))
      (h1 (format share-message from))
      (spacer 24)
      (post-block entry entry-url)
      (spacer 24)
      (when show-note? (spacer 8 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph"))
      (when show-note? (note-author from))
      (when show-note? (spacer 16 "note-paragraph" "note-paragraph"))
      (when show-note? (paragraph note "note-paragraph"))
      (when show-note? (spacer 16 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph"))
      (when show-note? (spacer 24))
      (left-button share-cta entry-url)
      (spacer 56)]))

(defn- notify-content [msg]
  (let [notification (:notification msg)
        content (:content notification)
        org (:org msg)
        logo-url (:logo-url org)
        logo? (not (s/blank? logo-url))
        org-name (:name org)
        org-name? (not (s/blank? org-name))
        org-slug (:slug org)
        mention? (:mention notification)
        comment? (:interaction-id notification)
        first-name (:first-name msg)
        first-name? (not (s/blank? first-name))
        author (:author notification)
        intro (if mention?
                (str "You were mentioned in a " (if comment? "comment" "post") ":")
                (str "There is a new comment on your post:"))
        notification-author (:author notification)
        notification-author-name (:name notification-author)
        notification-author-url (fix-avatar-url (:avatar-url notification-author))
        secure-uuid (:secure-uuid notification)
        origin-url config/web-url
        entry-url (s/join "/" [origin-url org-slug "post" secure-uuid])
        ; attribution (if mention?
        ;              [:a {:href entry-url} (str notification-author-name " mentioned you")]
        ;              [:a {:href entry-url} (str notification-author-name " commented")])
        notification-html-content (-> (hickory/parse content) hickory/as-hiccup first (nth 3) rest rest)]
    [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
      (spacer 40)
      (when logo? (org-logo (clojure.set/rename-keys org {:logo-url :org-logo-url
                                                          :name :org-name
                                                          :logo-height :org-logo-height
                                                          :logo-width :org-logo-width})))
      (when logo? (spacer 32))
      [:a {:href entry-url}
        (h1 intro)]
      (spacer 24)
      (spacer 8 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph")
      (note-author notification-author-name notification-author-url)
      (spacer 16 "note-paragraph" "note-paragraph")
      (paragraph notification-html-content "note-paragraph note-left-padding" "text-left" "note-x-margin")
      (spacer 16 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph")
      (spacer 56)]))

(defn- token-prep [token-type msg]
  {
    :message (case token-type
              :reset reset-message
              :verify verify-message)
    :instructions (case token-type
                    :reset reset-instructions
                    :verify verify-instructions)
    :instructions-2 (case token-type
                     :verify verify-instructions-2
                     :reset "")
    :button-text (case token-type
                    :reset reset-button-text
                    :verify verify-button-text)
    :link (:token-link (keywordize-keys msg))})

(defn- token-content [td-classes token-type msg]
  (let [message (token-prep token-type msg)
        logo-url (:org-logo-url msg)
        logo? (not (s/blank? logo-url))
        org-name (:org-name msg)]
    [:td {:class td-classes :valign "middle" :align "center"}
      (spacer 40)
      (when logo? (org-logo msg))
      (when logo? (spacer 32))
      (h1 (:message message))
      (spacer 24)
      (paragraph (:instructions message))
      (when (:instructions-2 message)
        (spacer 24))
      (when (:instructions-2 message)
        (paragraph (:instructions-2 message)))
      (spacer 16)
      [:a
        {:class "token-link"
         :href (:token-link msg)}
        (:token-link msg)]
      (spacer 56)]))

;; ----- General HTML, common to all emails -----

(defn- body [data]
  (let [type (:type data)]
    [:body
      [:table {:class "body"
               :with "100%"}
        [:tr
          [:td {:valign "middle"
                :align "center"}
            [:center
              (email-header)
              [:table {:class "row email-content"
                       :valign "middle"
                       :align "center"}
                [:tr
                  [:td {:class "small-1 hide-for-large columns"
                        :valign "middle"
                        :align "left"}]
                  (case type
                    :reset (token-content "small-10 large-12 columns" type data)
                    :verify (token-content "small-10 large-12 columns" type data)
                    :invite (invite-content "small-10 large-12 columns" data)
                    :board-notification (board-notification-content data)
                    :share-link (share-content data)
                    :digest (digest-content data)
                    :notify (notify-content data))
                  [:td {:class "small-1 hide-for-large columns"
                        :valign "middle"
                        :align "right"}]]]
              (email-footer)]]]]]))

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
        prefix (if (s/blank? from) "You've been invited" (str from " has invited you"))
        org (if (s/blank? org-name) "" (str org-name " on "))]
    (str prefix " to join " org "Carrot")))

(defn notify-html [msg]
  (html msg :notify))

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
        update-url (s/join "/" [config/web-url org-slug "post" secure-uuid])]
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


  ;; Notifications

  (def comment-notification (json/decode (slurp "./opt/samples/notifications/comment.json")))
  (spit "./hiccup.html" (content/notify-html comment-notification))

  (def post-mention-notification (json/decode (slurp "./opt/samples/notifications/post-mention.json")))
  (spit "./hiccup.html" (content/notify-html post-mention-notification))

  (def comment-mention-notification (json/decode (slurp "./opt/samples/notifications/comment-mention.json")))
  (spit "./hiccup.html" (content/notify-html comment-mention-notification))

  )