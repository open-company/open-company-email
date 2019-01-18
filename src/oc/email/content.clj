(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.format :as time-format]
            [hiccup.core :as h]
            [hickory.core :as hickory]
            [oc.lib.jwt :as jwt]
            [oc.email.config :as config]))

(def max-logo 40)
(def author-logo 32)

(def iso-format (time-format/formatters :date-time))
(def date-format (time-format/formatter "MMMM d"))
(def date-format-year (time-format/formatter "MMMM d YYYY"))

(def profile-url (str config/web-url "/profile/notifications"))

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

;; Links

(def carrot-hello-mailto "mailto:hello@carrot.io")

(def carrot-help "http://help.carrot.io")

;; ----- Copy -----

(def carrot-explainer "Carrot is the company digest that keeps everyone aligned around what matters most.")

(def invite-message "Join your team on Carrot")
(def invite-message-with-company "Join the %s team on Carrot")
(def invite-instructions "%s has invited you to Carrot - a leadership communication platform that keeps teams focused on what matters.")
(def invite-link-instructions "Click here to join your team:")
(def invite-button "accept_invitation")

(def share-message "%s shared a post with you")
(def share-cta "view_post")

(def board-invite-title "You’ve been invited to a private section on Carrot")
(def board-invite-message "%s has invited you to join a private section on Carrot called “")
(def board-invite-message-2 "”. Carrot is a leadership communication platform that keeps everyone focused on what matters.")
; "%s invited you to join a private section")
(def board-invite-button "view_section")

(def reset-message "Password reset")
(def reset-instructions "If you didn't request a password reset, you can ignore this email.")
(def reset-instructions-2 "Please click the link below to reset your password:")
(def reset-button-text "reset_password")

(def verify-message "Please verify your email")
(def verify-instructions "Welcome to Carrot! Carrot helps leaders rise above noisy chat and email to keep teams aligned.")
(def verify-instructions-2 "Please click the link below to verify your account:")
(def verify-button-text "verify_email")

(def digest-title-daily "Yesterday %s %s")

;; ----- HTML Fragments -----

(defn- org-logo
  [{org-name :org-name logo-url :org-logo-url logo-height :org-logo-height logo-width :org-logo-width
    align :align}]
  (let [logo? (and logo-url logo-height logo-width)]
    (when logo?
      [:table {:class "row logo"}
        [:tr 
          [:th {:class "small-12 large-12 first last columns"}
            [:table 
              [:tr 
                [:th
                  [:div
                    {:class "logo-container"}
                    [:img {:class (str "logo " (if (= align "center")
                                                "auto-x-margin"
                                                "left-float"))
                           :align (or align "left")
                           :src logo-url
                           :alt (str org-name " logo")}]]]]]]]])))

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

(defn- h1 [content & [h1-class]]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:h1 {:class (or h1-class "text-left")} content]]]])

(defn- h2
  ([content entry-url] (h2 content entry-url "" ""))
  ([content entry-url css-class] (h2 content entry-url css-class "text-left"))
  ([content entry-url css-class h2-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:a {:href entry-url}
          [:h2 {:class h2-class} content]]]]]))

(defn- h2-no-link
  ([content css-class h2-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:h2 {:class h2-class} content]]]]))

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
      :view_comment
      "View comment"
      ;; default "view_post"
      "View post")])

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
      [:td {:class "small-12 large-12 columns main-wrapper" :valign "middle" :align "center"}
        (vspacer 24 "header-table" "header-table")
        [:table {:class "row header-table"}
          [:tr
            [:th {:class "small-6 large-6 columns header-icon"}
              [:a
                {:href config/web-url}
                [:img {:src (str config/email-images-prefix "/email_images/carrot_logo_with_copy_colors@2x.png")
                       :width "90"
                       :height "22"
                       :alt "Carrot"}]]]
            [:th {:class "small-6 large-6 columns header-right"}
              [:span.header-right-span
                "Lead with clarity"]]]]
        (vspacer 24 "header-table" "header-table")]]])

(declare reminder-notification-settings-footer)

(defn- email-footer [type]
  [:table {:class "row footer-table"
           :valign "middle"
           :align "center"}
    [:tr
      [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
        (when (= type :reminder-notification)
          reminder-notification-settings-footer)
        (vspacer 24 "footer-table" "footer-table")
        [:table {:class "row footer-table"}
          [:tr
            [:th {:class "small-12 large-12"}
              [:p {:class "footer-paragraph bottom-footer"}
                "Sent with "
                [:span.heart
                  {:style (str "background: url(" config/email-images-prefix "/email_images/footer_heart@2x.png) no-repeat center / 18px 20px;")}]
                " via "
                [:a {:href config/web-url}
                  "Carrot"]]]]]
        (vspacer 40 "footer-table" "footer-table")]]])

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

(defn- post-headline [entry]
  (let [ms (:must-see entry)]
    [:div
      [:span.post-title
        (:headline entry)]
      (when ms
        [:span.must-see-container
          [:img
            {:class "must-see-icon"
             :width "8"
             :height "10"
             :src (str config/email-images-prefix "/email_images/must_see@2x.png")}]
          [:span.must-see
            "MUST SEE"]])]))

(defn- post-block
  ([entry] (post-block entry (:url entry)))
  ([entry entry-url]
  (let [publisher (:publisher entry)
        avatar-url (fix-avatar-url (:avatar-url publisher))
        headline (post-headline entry)
        vid (:video-id entry)]
    [:table
      {:cellpadding "0"
       :cellspacing "0"
       :border "0"
       :class "row"}
      [:tr
        (when avatar-url
          [:td
            {:class "post-block-avatar"}
            [:img.post-avatar
              {:src avatar-url}]])
        [:td
          {:class (if avatar-url "post-block-avatar-right" "post-block-right")}
          (h2 headline entry-url "")
          (spacer 4 "")
          (post-attribution entry true "")
          (when vid
            (spacer 16 ""))
          (when vid
            [:table
              {:class "row video-cover-table"}
              [:tr
                [:td
                  [:a
                    {:class "video-cover"
                     :href entry-url
                     :style (str "background-image: url(https://" (:video-image entry) ");")}
                    [:img
                      {:class "video-play"
                       :src (str config/email-images-prefix "/email_images/video_play@2x.png")
                       :width 40
                       :height 40}]
                    [:div
                      {:class "video-duration-container"}
                      [:span
                        {:class "video-duration"}
                        (:video-duration entry)]]]]]])]]])))

(defn- posts-with-board-name [board]
  (let [board-name (:name board)]
    (assoc board :posts (map #(assoc % :board-name board-name) (:posts board)))))

(defn digest-title [org-name]
  (if org-name
    (format digest-title-daily "at" org-name)
    (format digest-title-daily "in" "Carrot")))

(defn- digest-preheader [digest-data]
  (let [org-name (:org-name digest-data)]
    [:span.hidden
      (str "See the latest updates from your team." (s/join (repeat 120 "&nbsp;&zwnj;")))]))

(defn- get-digest-url [digest-data]
  (s/join "/" [config/web-url (:org-slug digest-data) "all-posts"]))

(defn- go-to-posts-script [data]
  [:script {:type "application/ld+json"}
"
{
  \"@context\": \"http://schema.org\",
  \"@type\": \"EmailMessage\",
  \"description\": \"" (digest-title (:org-name data)) "\",
  \"potentialAction\": {
    \"@type\": \"ViewAction\",
    \"target\": \"" (get-digest-url data) "\",
    \"name\": \"Go to posts\"
  }
}
"])

(defn- digest-content [digest]
  (let [logo-url (:logo-url digest)
        logo? (not (s/blank? logo-url))
        org-name (:org-name digest)
        boards (map posts-with-board-name (:boards digest))
        posts (mapcat :posts boards)
        digest-url (get-digest-url digest)
        first-name (:first-name digest)
        digest-headline (digest-title org-name)]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      [:center
        (when logo? (org-logo {:org-name (:org-name digest)
                               :org-logo-url logo-url
                               :org-logo-width (:logo-width digest)
                               :org-logo-height (:logo-height digest)
                               :align "center"}))
        (when logo? (spacer 32))
        (h1 digest-headline "center-align")
        (spacer 16)
        [:table {:class "row"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:a.go-to-posts
                {:href digest-url}
                "Go to posts"]]]]
        (spacer 40)
        [:table
          {:cellpadding "0"
           :cellspacing "0"
           :border "0"}
          [:tr
            [:td
              (for [p posts]
                [:table
                  {:cellpadding "0"
                   :cellspacing "0"
                   :border "0"
                   :class "row"}
                  [:tr
                    [:td {:class "small-12 large-12"}
                      horizontal-line
                      (spacer 20)
                      (post-block p)
                      (spacer 28)]]])]]]]]))

;; Reminder alert

(defn- first-name [user-map]
  (or (:first-name user-map) (first (s/split (:name user-map) #"\s"))))

(defn reminder-alert-headline [reminder-data]
  (str "Hi " (first-name (:assignee reminder-data)) ", it's time to update your team"))

(defn reminder-alert-settings-footer [frequency]
  [:table {:class "row reminders-footer"
         :valign "middle"
         :align "center"}
  [:tr
    [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
      (vspacer 32 "" "settings-footer")
      [:table {:class "row reminders-footer center-align"}
        [:tr
          [:th {:class "small-12 large-12"}
            [:p {:class "settings-footer"}
              (str
              "This is a "
              (case (s/lower-case frequency)
                "quarter" "quarterly"
                "month" "monthly"
                "other week" "bi-weekly"
                ;:else
                "weekly")
              " reminder. You can adjust or turn off reminders in ")
              [:a {:href profile-url}
                "Carrot"]
              "."]]]]
      (vspacer 16 "" "settings-footer")]]])

(defn- reminder-alert-content [reminder]
  (let [org (:org reminder)
        org-name (:name org)
        org-slug (:slug org)
        logo-url (:logo-url org)
        logo-width (:logo-width org)
        logo-height (:logo-height org)
        logo? (not (s/blank? logo-url))
        reminder-data (:reminder reminder)
        author (:author reminder-data)
        title (:headline reminder-data)
        headline (reminder-alert-headline reminder-data)
        create-post-url (str (s/join "/" [config/web-url org-slug "all-posts"]) "?new")]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      [:center
        ; (when logo? (org-logo {:org-name org-name
        ;                        :org-logo-url logo-url
        ;                        :org-logo-width logo-width
        ;                        :org-logo-height logo-height
        ;                        :align "center"}))
        ; (when logo? (spacer 32))
        (spacer 80)
        (h1 headline "center-align")
        (spacer 8)
        (paragraph title "center-align" "center-align")
        (spacer 24)
        [:table {:class "row"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:a.view-reminders
                {:href create-post-url}
                "Ok, let's do it"]]]]
        (spacer 80)
        (reminder-alert-settings-footer (:frequency reminder-data))]]))

;; Reminder notification

(defn reminder-notification-headline [reminder-data]
  (str (first-name (:author reminder-data)) " created a new reminder for you"))

(defn reminder-notification-subline [reminder-data]
  (str (:frequency reminder-data) " starting " (post-date (:next-send reminder-data))))

(def reminder-notification-settings-footer
  [:table {:class "row reminders-footer"
         :valign "middle"
         :align "center"}
  [:tr
    [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
      (vspacer 32 "" "reminders-footer")
      [:table {:class "row reminders-footer center-align"}
        [:tr
          [:th {:class "small-12 large-12"}
            [:p {:class "reminders-footer reminders-footer-paragraph"}
              "You can always adjust or turn off reminders in "
              [:a {:href profile-url}
                "Carrot"]]]]]
      (vspacer 32 "footer-table reminders-bottom-footer" "reminders-footer")]]])

(defn- reminder-notification-content [reminder]
  (let [org (:org reminder)
        org-name (:name org)
        org-slug (:slug org)
        logo-url (:logo-url org)
        logo-width (:logo-width org)
        logo-height (:logo-height org)
        logo? (not (s/blank? logo-url))
        reminder-data (:reminder reminder)
        author (:author reminder-data)
        title (:headline reminder-data)
        headline (reminder-notification-headline reminder-data)
        subline (reminder-notification-subline reminder-data)
        reminders-url (str (s/join "/" [config/web-url org-slug "all-posts"]) "?reminders")]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      [:center
        (when logo? (org-logo {:org-name org-name
                               :org-logo-url logo-url
                               :org-logo-width logo-width
                               :org-logo-height logo-height
                               :align "center"}))
        (when logo? (spacer 32))
        (h1 headline "center-align")
        (spacer 65)
        (h2-no-link subline "center-align" "reminder-headline")
        (spacer 8)
        (paragraph title "center-align" "center-align")
        (spacer 16)
        [:table {:class "row"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:a.view-reminders
                {:href reminders-url}
                "View reminder"]]]]
        (spacer 40)]]))

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
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      (spacer 64)
      (h1 board-invite-title)
      (spacer 16)
      [:table {:class "row"}
        [:tr
          [:th {:class "small-12 large-12 columns"}
            [:p {:class "text-left"}
              (format board-invite-message from)
              [:a {:href board-url}
                board-name]
              board-invite-message-2]]]]
      (when show-note? (spacer 16))
      (when show-note? (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph"))
      (when show-note? (note-author from))
      (when show-note? (spacer 8 "note-paragraph" "note-paragraph"))
      (when show-note? (paragraph note "note-paragraph"))
      (when show-note? (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph"))
      (spacer 24)
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
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height}))
      (when logo? (spacer 32))
      (h1 invite-message)
      (spacer 24)
      (paragraph (format invite-instructions from))
      (spacer 16)
      (when note? (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph"))
      (when note? (note-author from))
      (when note? (spacer 8 "note-paragraph" "note-paragraph"))
      (when note? (paragraph note "note-paragraph"))
      (when note? (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph"))
      (when note? (spacer 16))
      (paragraph invite-link-instructions)
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
    [:td {:class "small-12 large-12 columns vertical-padding" :valign "middle" :align "center"}
      (when logo? (org-logo entry))
      (when logo? (spacer 32))
      (h1 (format share-message from))
      (spacer 24)
      (post-block entry entry-url)
      (spacer 24)
      (when show-note? (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph"))
      (when show-note? (note-author from))
      (when show-note? (spacer 8 "note-paragraph" "note-paragraph"))
      (when show-note? (paragraph note "note-paragraph"))
      (when show-note? (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph"))
      (when show-note? (spacer 24))
      (left-button share-cta entry-url)
      (spacer 56)]))

(defn- notify-content [msg]
  (let [notification (:notification msg)
        content (:content notification)
        org (:org msg)
        entry-data (:entry-data notification)
        board-slug (:board-slug entry-data)
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
                (if comment?
                  (str "You were mentioned in a comment: ")
                  (str "You were mentioned in a post: "))
                (str "There is a new comment on your post:"))
        notification-author (:author notification)
        notification-author-name (:name notification-author)
        notification-author-url (fix-avatar-url (:avatar-url notification-author))
        uuid (:entry-id notification)
        secure-uuid (:secure-uuid notification)
        origin-url config/web-url
        token-claims {:org-uuid (:org-id notification)
                      :secure-uuid secure-uuid
                      :name (str first-name " " (:last-name msg))
                      :first-name first-name
                      :last-name (:last-name msg)
                      :user-id (:user-id msg)
                      :avatar-url (:avatar-url msg)
                      :team-id (:team-id org)} ;; Let's read the team-id from the org to avoid problems on multiple org users
        id-token (jwt/generate-id-token token-claims config/passphrase)
        entry-url (s/join "/" [origin-url
                               org-slug
                               board-slug
                               "post"
                               uuid
                               (str "?id=" id-token)])
        button-cta (if (or (not mention?) comment?)
                    "view_comment"
                    "view_post")
        notification-html-content (-> (hickory/parse content) hickory/as-hiccup first (nth 3) rest rest)]
    [:td {:class "small-12 large-12 columns vertical-padding" :valign "middle" :align "center"}
      (spacer 64)
      (h1 intro)
      (spacer 24)
      (post-block entry-data entry-url)
      (spacer 24)
      (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph")
      (note-author notification-author-name notification-author-url)
      (spacer 8 "note-paragraph" "note-paragraph")
      (paragraph notification-html-content "note-paragraph note-left-padding" "text-left" "note-x-margin")
      (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph")
      (spacer 24)
      (left-button button-cta entry-url)
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
                     :reset reset-instructions-2)
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
      (when logo? (org-logo msg))
      (when logo? (spacer 32))
      (h1 (:message message))
      (spacer 24)
      (paragraph (:instructions message))
      (when (:instructions-2 message)
        (spacer 24))
      (when (:instructions-2 message)
        (paragraph (:instructions-2 message)))
      [:a
        {:class "token-link"
         :href (:token-link msg)}
        (:token-link msg)]
      (spacer 56)]))

;; ----- General HTML, common to all emails -----

(defn- body [data]
  (let [type (:type data)]
    [:body
      (when (= (:type data) :digest)
        (go-to-posts-script data))
      (when (= type :digest)
        (digest-preheader data))
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
                  [:td
                    {:class "vertical-padding"}
                    (spacer 40 "top-email-content")]]
                [:tr
                  (case type
                    :reset (token-content "small-12 large-12 columns main-wrapper vertical-padding" type data)
                    :verify (token-content "small-12 large-12 columns main-wrapper vertical-padding" type data)
                    :invite (invite-content "small-12 large-12 columns main-wrapper vertical-padding" data)
                    :board-notification (board-notification-content data)
                    :share-link (share-content data)
                    :digest (digest-content data)
                    :notify (notify-content data)
                    :reminder-notification (reminder-notification-content data)
                    :reminder-alert (reminder-alert-content data))]]
              (email-footer type)]]]]]))

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

(defn reminder-notification-html [reminder]
  (html reminder :reminder-notification))

(defn reminder-alert-html [reminder]
  (html reminder :reminder-alert))

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

  ;; Reminder notification

  (def reminder-notification (json/decode (slurp "./opt/samples/reminders/notification.json")))
  (spit "./hiccup.html" (content/reminder-notification-html reminder-notification))
  
  ;; Reminder alert

  (def reminder-alert (json/decode (slurp "./opt/samples/reminders/alert.json")))
  (spit "./hiccup.html" (content/reminder-alert-html reminder-alert))
  )