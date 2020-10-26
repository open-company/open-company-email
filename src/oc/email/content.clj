(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [oc.lib.text :as text]
            [hiccup.core :as h]
            [hickory.core :as hickory]
            [oc.lib.auth :as auth]
            [oc.lib.jwt :as jwt]
            [oc.lib.user :as user-lib]
            [oc.lib.storage :as storage]
            [oc.email.config :as config]
            [jsoup.soup :as soup]))

(def max-logo 40)

(def iso-format (time-format/formatters :date-time))
(def date-format (time-format/formatter "MMM. d"))
(def date-format-no-dot (time-format/formatter "MMM d"))
(def date-format-year (time-format/formatter "MMM. d YYYY"))
(def digest-subject-format (time-format/formatter "MMM d, YYYY"))
(def date-format-year-comma (time-format/formatter "MMM. d, YYYY"))
(def day-month-date-year (time-format/formatter "EEEE, MMM. dd, YYYY"))
(def reminder-date-format (time-format/formatter "EEEE, MMMM d"))
(def reminder-date-format-year (time-format/formatter "EEEE, MMMM d YYYY"))

(defn- home-url [org-slug]
  (str (s/join "/" [config/web-url org-slug "home"]) "?user-settings=notifications"))

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

;; Links

(def carrot-hello-mailto "mailto:hello@carrot.io")

(def carrot-help "http://help.carrot.io")

;; ----- Copy -----

(def carrot-explainer-fragment "your company digest for the team news and updates no one should miss.")
(def carrot-explainer (str "Carrot is " carrot-explainer-fragment))

(def invite-message "Join your team on Carrot")
(def invite-message-with-company "Join the %s team on Carrot")
(def invite-instructions (str "%s has invited you to Carrot - " carrot-explainer-fragment))
(def invite-link-instructions "Click here to join:")
(def invite-button "accept_invitation")

(def share-message "%s shared a post with you")
(def share-cta "view_post")

(def board-invite-title "You’ve been invited to a private section")
(def board-invite-message "%s invited you to a private section")
(def board-invite-message-2 (str "”. " carrot-explainer))
(def board-invite-button "view_section")

(def reset-message "Password reset")
(def reset-instructions "If you didn't request a password reset, you can ignore this email.")
(def reset-instructions-2 "Please click the link below to reset your password:")
(def reset-button-text "reset_password")

(def verify-message "Please verify your identity")
(def verify-instructions "Welcome to Carrot! Carrot is a personalized news feed for your team. With Carrot, it's easy to stay in sync with fewer interruptions.")
(def verify-instructions-2 "Please click the link below to verify your identity:")
(def verify-button-text "verify_email")

;; Bot removed
(def bot-removed-subject "Your Carrot bot for Slack was removed")
(defn bot-removed-instructions [org-name integration-settings-url]
  [:div.bot-removed-instructions
    "Hi,"
    [:br] [:br]
    "You’re receiving this note because you’re a Carrot admin "
    (when (seq org-name) 
     "for ")
    (when (seq org-name)
      [:b org-name])
    "."
    [:br][:br]
    "We noticed your Carrot bot for Slack was removed. If it was removed on purpose, please ignore this email. If not, you’ll want to "
    [:a
     {:href integration-settings-url}
     "re-enable your bot"]
    "."
    [:br] [:br]
    "The Carrot bot for Slack allows your Carrot updates, comments and notifications"
    " to flow into Slack where it’s easy to see them."])
(def bot-removed-button "integration_settings")

(def bot-removed-footer
  [:div.bot-removed-instructions
    "If you have any questions or want help turning it back on, just reply to this email."
    [:br][:br]
    "Thanks,"[:br]
    "The Carrot Team"])

(defn- preheader-spacer []
  (s/join (repeat 120 "&nbsp;&zwnj;")))

;; ----- Retrieve post data -----

(defn get-post-data [payload]
  (let [notification (:notification payload)
        user-map {:user-id (:user-id payload)}
        c {:storage-server-url config/storage-server-url
           :auth-server-url config/auth-server-url
           :passphrase config/passphrase
           :service-name "Email"}]
    (storage/post-data-for c user-map (:slug (:org payload)) (:board-id notification) (:entry-id notification))))

;; ----- HTML Fragments -----

(defn- preheader [text]
  [:span.hidden (str text (preheader-spacer))])

(defn- org-logo
  [{org-name :org-name logo-url :org-logo-url logo-height :org-logo-height logo-width :org-logo-width
    align :align css-class :class}]
  (let [logo? (and logo-url logo-height logo-width)]
    (when logo?
      [:table {:class (str "row  " (if css-class
                                     css-class
                                     " logo"))}
        [:tr {:class css-class}
          [:th {:class css-class}
            [:table {:class css-class}
              [:tr {:class css-class}
                [:th {:class css-class}
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

(defn- paragraph
  ([content] (paragraph content ""))
  ([content css-class] (paragraph content css-class ""))
  ([content css-class content-css-class & [inner-th-class]]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class (str "small-12 large-12 columns " inner-th-class)}
        [:p {:class content-css-class} content]]]]))

(defn- h1 [content & [h1-class]]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:h1 {:class (or h1-class "")} content]]]])

(defn- horizontal-line
  ([] (horizontal-line 1))
  ([height]
   [:table {:class "horizontal-line"}
    [:tbody
     [:tr
      [:td
       (spacer height)]]]]))

(defn- h2
  ([content entry-url] (h2 content entry-url "" ""))
  ([content entry-url css-class] (h2 content entry-url css-class ""))
  ([content entry-url css-class h2-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-12 large-12 columns"}
        [:a {:href entry-url}
          [:h2 {:class h2-class} content]]]]]))

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
      "Verify identity"
      :view_section
      "View section"
      :view_comment
      "Reply"
      :integration_settings
      "Enable Carrot bot for Slack"
      ;; default "view_post"
      "View post")])

(defn- email-header [is-digest?]
  [:table {:class "header-table"
           :valign "middle"
           :align "center"}
    [:tr
      [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
        (vspacer 24 "" "")
        [:table
          [:tr
            [:th {:class "small-6 large-6 columns header-icon"}
              [:a
                {:href config/web-url}
                [:img {:src (str config/email-images-prefix "/email_images/carrot_logo_with_copy_colors@2x.png")
                       :width "95"
                       :height "32"
                       :alt "Carrot"}]]]
            [:th {:class "small-6 large-6 columns header-right"}
              (when (= type :digest)
                [:div.digest-date
                  (time-format/unparse date-format-year-comma (time/now))])]]]
        (vspacer (if (= type :digest) 16 24) "header-table" "header-table")]]])

(defn- email-footer [data type]
  (let [digest? (= type :digest)]
    [:table {:class (str "row footer-table" (when digest? " digest-footer-table"))
             :valign "middle"
             :align "center"}
      [:tr
        [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
          (vspacer 24 "footer-table" "footer-table")
          [:table {:class "row footer-table"}
            [:tr
              [:th {:class "small-12 large-12"}
                [:p {:class "footer-paragraph bottom-footer"}
                  [:a {:href config/web-url}
                    [:span.footer-link
                      {:style (str "background: url(" config/email-images-prefix "/email_images/carrot_grey@2x.png) no-repeat center / 10px 18px;")}]
                    "Sent by Carrot"]]]]
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                (vspacer 16 "footer-table" "footer-table")]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                [:p {:class "footer-paragraph bottom-footer"}
                  (str "You received this digest because you are part of the " (:org-name data) " team.")]]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                [:p {:class "footer-paragraph bottom-footer underline-link"}
                  [:a {:href (str (:url (:following data)) "?user-settings=notifications")}
                    "Manage your digest settings"]]]])]
          (vspacer 40 "footer-table" "footer-table")]]]))

;; ----- Posts common ----

(defn- post-headline
  ([entry]
    (post-headline entry false))
  ([entry add-arrow?]
  [:div
    [:span.post-title
      (str
       (.text (soup/parse (:headline entry)))
       (when add-arrow?
         " →"))]]))

(defn- post-body [cleaned-body]
  [:div.post-body
    cleaned-body])

(defn- digest-post-block
  [entry]
  (let [publisher (:publisher entry)
        avatar-url (user-lib/fix-avatar-url config/filestack-api-key (:avatar-url publisher) 128)
        comment-count-label (:comment-count-label entry)
        comments? (seq comment-count-label)]
    [:table
      {:cellpadding "0"
       :cellspacing "0"
       :border "0"
       :class "row digest-post-block text-left"}
      [:tr
        [:td
          (spacer 16)]]
      [:tr
        [:td.digest-post-avatar-td
          [:a
            {:href (:url publisher)}
            [:img.digest-post-avatar
              {:src avatar-url
               :alt (:name publisher)}]]]
        [:td
          [:table
            {:cellpadding "0"
             :cellspacing "0"
             :border "0"
             :class "row digest-post-block"}
            [:tr [:td
              [:a
                {:href (:url entry)}
                [:table
                  {:cellpadding "0"
                  :cellspacing "0"
                  :border "0"
                  :class "row digest-post-block"}
                  [:tr
                    [:td
                      [:span.digest-post-attribution
                        (:name publisher)
                        " in "
                        (:board-name entry)]]]
                  [:tr
                    [:td
                      [:span.digest-post-headline-row
                        (str (:headline entry) " →")
                      ; (h2  (:url entry) "" "digest-post-title")
                      ]]]]]]]
            ; (when has-body
            ;   [:tr [:td (spacer 4)]])
            ; (when has-body
            ;   [:tr [:td
            ;     (post-body cleaned-body)]])
            [:tr [:td 
                (spacer 8)]]]]]]))

(defn digest-title [org-name]
  (let [date-str (time-format/unparse digest-subject-format (time/now))]
    (str "Your " (or org-name "Carrot") " digest for " date-str)))

(defn- go-to-posts-script [data]
  [:script {:type "application/ld+json"}
"
{
  \"@context\": \"http://schema.org\",
  \"@type\": \"EmailMessage\",
  \"description\": \"" (or (:digest-subject data) (digest-title (:org-name data))) "\",
  \"potentialAction\": {
    \"@type\": \"ViewAction\",
    \"target\": \"" (-> data :following :url) "\",
    \"name\": \"Go to posts\"
  }
}
"])

(defn- digest-content
  [{:keys [following replies unfollowing new-boards digest-label org-light-brand-color] :as digest}]
  (let [user (select-keys digest [:user-id :name :avatar-url])
        following? (seq (:following-list following))
        brand-color (or org-light-brand-color config/default-brand-color)]
    [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
      [:center
        (spacer 40)
        [:table
          {:cellpadding "0"
           :cellspacing "0"
           :border "0"
           :class "digest-content"}
          [:tr
            [:td
              digest-label]]
          [:tr [:td (spacer 32)]]
          [:tr
            [:td
             [:center
              [:a.digest-cta
               {:href (:url following)
                :style {:background-color (-> brand-color :primary :hex)
                        :color (-> brand-color :secondary :hex)}}
               "View updates"]]]]
          [:tr [:td (spacer 32)]]
          [:tr [:td (horizontal-line)]]
          (when following?
            [:tr
             [:td
              (spacer 8)]])
          (when following?
            [:tr
              [:td
                (for [p (:following-list following)
                      :let [post (assoc p :replies-url (:url replies))]]
                  [:table
                    {:cellpadding "0"
                     :cellspacing "0"
                     :border "0"
                     :class "row digest-posts-container"}
                    [:tr
                      [:td {:class "small-12 large-12 columns"}
                        (digest-post-block post)]]])]])
          [:tr
            [:td
              (spacer 16)]]]
        (spacer 40)]]))

;; Reminder alert

(defn- first-name [user-map]
  (or (:first-name user-map) (first (s/split (:name user-map) #"\s"))))

(defn reminder-alert-headline [data]
  (str "Hi " (first-name (:assignee (:reminder (:notification data)))) ", it's time to update your team"))

(defn- frequency-string [f]
  (case (s/lower-case f)
    "weekly" "Weekly"
    "biweekly" "Every other week"
    "monthly" "Monthly"
    "Quarterly"))

(defn- reminder-due-date [timestamp]
  (let [d (time-format/parse iso-format timestamp)
        n (time/now)
        same-year? (= (time/year n) (time/year d))
        output-format (if same-year? reminder-date-format reminder-date-format-year)]
    (time-format/unparse output-format d)))

(defn reminder-notification-subline [reminder-data]
  (str (frequency-string (:frequency reminder-data)) " starting " (reminder-due-date (:next-send reminder-data))))

(defn- reminder-alert-content [reminder]
  (let [org (:org reminder)
        org-name (:name org)
        org-slug (:slug org)
        logo-url (:logo-url org)
        logo-width (:logo-width org)
        logo-height (:logo-height org)
        logo? (not (s/blank? logo-url))
        reminder-data (:reminder (:notification reminder))
        author (:author reminder-data)
        headline (reminder-alert-headline reminder)
        create-post-url (str (s/join "/" [config/web-url org-slug "home"]) "?new")
        subline (reminder-notification-subline reminder-data)]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      [:center
        (spacer 40)
        (h1 headline "center-align")
        (spacer 24)
        
        (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph")
        (paragraph (:headline reminder-data) "note-paragraph" "text-left reminder-headline" "note-x-margin")
        (spacer 8 "note-paragraph" "note-paragraph")
        (paragraph subline "note-paragraph" "text-left reminder-subline" "note-x-margin")
        (spacer 16 "note-paragraph" "note-paragraph")
        (paragraph [:span.note-paragraph
                     "You can always adjust or turn off recurring updates in "
                     [:a
                       {:href (home-url org-slug)}
                       "Carrot"]]
         "note-paragraph note-paragraph-footer" "text-left")
        (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph")
        (spacer 16)
        [:table {:class "row"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:a.view-reminders
                {:href create-post-url}
                "Ok, let's do it"]]]]
        (spacer 40)]]))

;; Reminder notification

(defn reminder-notification-headline [data]
  (str (first-name (:author (:reminder (:notification data)))) " created a new reminder for you"))

(defn- reminder-notification-content [reminder]
  (let [org (:org reminder)
        org-name (:name org)
        org-slug (:slug org)
        logo-url (:logo-url org)
        logo-width (:logo-width org)
        logo-height (:logo-height org)
        logo? (not (s/blank? logo-url))
        reminder-data (:reminder (:notification reminder))
        author (:author reminder-data)
        author-avatar-url (:avatar-url author)
        is-default-avatar? (s/starts-with? author-avatar-url "/img")
        title (:headline reminder-data)
        headline (reminder-notification-headline reminder)
        subline (reminder-notification-subline reminder-data)
        reminders-url (str (s/join "/" [config/web-url org-slug "home"]) "?reminders")]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      [:center
        (when (and (not is-default-avatar?)
                   (seq author-avatar-url))
          [:img.reminder-author
            {:src (user-lib/fix-avatar-url config/filestack-api-key author-avatar-url 80)}])
        (when (and (not is-default-avatar?)
                   (seq author-avatar-url))
          (spacer 24))
        (h1 headline "center-align")
        (spacer 24)
        (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph")
        (paragraph (:headline reminder-data) "note-paragraph" "text-left reminder-headline" "note-x-margin")
        (spacer 8 "note-paragraph" "note-paragraph")
        (paragraph subline "note-paragraph" "text-left reminder-subline" "note-x-margin")
        (spacer 16 "note-paragraph" "note-paragraph")
        (paragraph [:span.note-paragraph
                     "You can always adjust or turn off recurring updates in "
                     [:a
                       {:href (home-url org-slug)}
                       "Carrot"]]
         "note-paragraph note-paragraph-footer" "text-left")
        (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph")
        (spacer 16)
        [:table {:class "row"}
          [:tr
            [:th {:class "small-12 large-12 columns"}
              [:a.view-reminders
                {:href reminders-url}
                "View recurring update"]]]]
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
        invite-header (if-not (s/blank? (s/trim from))
                       (format board-invite-message from)
                       board-invite-title)
        from-avatar (-> notice :inviter :avatar-url)
        from-avatar? (not (s/blank? from-avatar))
        note (:note notice)
        note? (not (s/blank? note))
        show-note? (and from-avatar? note?)]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      (spacer 64)
      (h1 invite-header)
      (spacer 16)
      [:table {:class "row"}
        [:tr
          [:th {:class "small-12 large-12 columns"}
            [:a {:href board-url}
              (str board-name " (private)")]]]]
      (when show-note? (spacer 16))
      (when show-note? (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph"))
      (when show-note? (paragraph note "note-paragraph" "text-left" "note-x-margin"))
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
        note (:note invite)
        note? (not (s/blank? note))
        from (:from invite)
        prefix (if (s/blank? from) "You've been invited" (str from " has invited you"))
        org (if (s/blank? org-name) "" (str org-name " on "))
        invite-message (if (s/blank? org-name)
                         invite-message
                         (format invite-message-with-company org-name))]
    [:td {:class td-classes :valign "middle" :align "center"}
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height
                             :class "small-12 large-12 first last columns"}))
      (when logo? (spacer 24))
      (h1 (str prefix " to join " org "Carrot") "invite-header")
      (spacer 24)
      (when note? (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph"))
      (when note? (note-author from))
      (when note? (spacer 8 "note-paragraph" "note-paragraph"))
      (when note? (paragraph note "note-paragraph"))
      (when note? (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph"))
      (when note? (spacer 16))
      (paragraph invite-link-instructions "" "invite-link-instructions")
      [:a
        {:class "token-link"
         :href (:token-link invite)}
        (:token-link invite)]
      (spacer 40)]))

(defn- share-title [data]
  (let [sharer (:sharer-name data)
        from (if (s/blank? sharer) "Someone" sharer)]
    (format share-message from)))

(defn- share-content [entry]
  (let [logo-url (:org-logo-url entry)
        logo? (not (s/blank? logo-url))
        org-name (:org-name entry)
        org-name? (not (s/blank? org-name))
        headline (.text (soup/parse (:headline entry)))
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
      (when logo? (org-logo (assoc entry :class "small-12 large-12 first last columns")))
      (when logo? (spacer 32))
      (h1 (share-title entry))
      (spacer 8)
      (h2 (:headline entry) entry-url "post-title")
      (spacer 24)
      (when show-note? (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph"))
      (when show-note? (paragraph note "note-paragraph"))
      (when show-note? (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph"))
      (when show-note? (spacer 24))
      (left-button share-cta entry-url)
      (spacer 56)]))

(defn notify-intro [msg]
  (let [notification (:notification msg)
        mention? (:mention? notification)
        comment? (:interaction-id notification)
        entry-publisher (:entry-publisher notification)
        user-id (:user-id notification)
        author (:author notification)]
    (if mention?
      (if comment?
        (str "You were mentioned in a comment")
        (str "You were mentioned in a post"))
      (if (= (:user-id entry-publisher) user-id)
        (if (seq author)
          (str (:name author) " commented on your post")
          (str "There is a new comment on your post"))
        (str (:name author) " replied to a thread")))))

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
        interaction-id (:interaction-id notification)
        first-name (:first-name msg)
        first-name? (not (s/blank? first-name))
        author (:author notification)
        intro (notify-intro msg)
        notification-author (:author notification)
        notification-author-name (:name notification-author)
        is-default-avatar? (s/starts-with? (:avatar-url notification-author) "/img")
        notification-author-url (user-lib/fix-avatar-url config/filestack-api-key (:avatar-url notification-author) 128)
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
        base-url (if (seq interaction-id)
                   (s/join "/" [origin-url org-slug board-slug "post" uuid "comment" interaction-id])
                   (s/join "/" [origin-url org-slug board-slug "post" uuid]))
        entry-url (str base-url "?id=" id-token)
        button-cta (if (or (not mention?) interaction-id)
                    "view_comment"
                    "view_post")
        notification-html-content (-> (hickory/parse content) hickory/as-hiccup first (nth 3) rest rest)
        post-data (get-post-data msg)]
    [:td {:class "small-12 large-12 columns vertical-padding" :valign "middle" :align "center"}
      (spacer 40)
      (when-not is-default-avatar?
        [:img.notify-author-avatar
         {:src notification-author-url}])
      (when-not is-default-avatar?
        (spacer 24))
      (h1 intro)
      (spacer 8)
      (h2 (:headline post-data) entry-url)
      (spacer 24)
      (spacer 24 "note-paragraph top-note-paragraph" "note-paragraph top-note-paragraph")
      (paragraph notification-html-content "note-paragraph note-left-padding" "text-left" "note-x-margin")
      (spacer 24 "note-paragraph bottom-note-paragraph" "note-paragraph bottom-note-paragraph")
      (spacer 24)
      (left-button button-cta entry-url)
      (spacer 40)]))

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
        org-name (:org-name msg)]
    [:td {:class td-classes :valign "middle" :align "center"}
      (h1 (:message message) "token-headeer")
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
      (spacer 40)]))

;; ----- Bot removed

 (defn- bot-removed-content [msg]
  (let [add-bot-url (str config/web-url "/" (:org-slug msg) "/home" "?org-settings=integrations")
        subline (bot-removed-instructions (:org-name msg) add-bot-url)
        logo? (not (s/blank? (:org-logo-url msg)))]
    [:td {:class "small-12 large-12 columns vertical-padding" :valign "middle" :align "center"}
      [:center
        (when logo? (org-logo (merge msg {:align "center"
                                          :class "small-12 large-12 first last columns"})))
        (when logo? (spacer 32))
        (h1 bot-removed-subject "center-align")
        (spacer 24)
        (paragraph subline)
        (spacer 24)
        (left-button bot-removed-button add-bot-url "integration-settings-button")
        (spacer 24)
        (paragraph bot-removed-footer)
        (spacer 56)]]))

;; ----- General HTML, common to all emails -----

(defn- body [data]
  (let [type (:type data)
        digest? (= type :digest)]
    [:body
      (when digest?
        (go-to-posts-script data))
      (case type
        :reset (preheader reset-message)
        :verify (preheader "Welcome to Carrot")
        :invite (preheader invite-message)
        :board-notification (preheader board-invite-title)
        :share-link (preheader (share-title data))
        :digest (preheader "See the latest updates and news from your team.")
        :notify (preheader (notify-intro data))
        :reminder-notification (preheader (reminder-notification-headline data))
        :reminder-alert (preheader (reminder-alert-headline data))
        :bot-removed (preheader bot-removed-subject))
      [:table {:class "body"
               :with "100%"}
        [:tr
          [:td {:valign "middle"
                :align "center"}
            [:center
              (email-header type)
              (horizontal-line)
              [:table {:class (str "row " (cond
                                            digest? "digest-email-content"
                                            :else "email-content"))
                       :valign "middle"
                       :align "center"}
                (when-not digest?
                  [:tr
                    [:td
                      {:class "vertical-padding"}
                      (spacer 40 "top-email-content")]])
                [:tr
                  (case type
                    (:reset :verify) (token-content "small-12 large-12 columns main-wrapper vertical-padding" type data)
                    :invite (invite-content "small-12 large-12 columns main-wrapper vertical-padding" data)
                    :board-notification (board-notification-content data)
                    :share-link (share-content data)
                    :digest (digest-content data)
                    :notify (notify-content data)
                    :reminder-notification (reminder-notification-content data)
                    :reminder-alert (reminder-alert-content data)
                    :bot-removed (bot-removed-content data))]]
              (horizontal-line)
              (email-footer data type)]]]]]))

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

(defn bot-removed-html [msg]
  (html msg :bot-removed))

(defn share-link-html [entry]
  (html entry :share-link))

(defn share-link-text [entry]
  (let [note (:note entry)
        note? (not (s/blank? note))
        org-name (:org-name entry)
        org-name? (not (s/blank? org-name))
        headline (.text (soup/parse (:headline entry)))
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
  (require '[cheshire.core :as json])
  (require '[oc.email.content :as content] :reload)

  ;; Recreate hiccup from various HTML fragments

  (defn clean-html [data]
    (-> data
      (s/replace "  " "")
      (s/replace "\n" "")
      (s/replace "\t" "")))

  ;; Generate html file with inline style from a normal HTML file

  (require '[clojure.java.shell :as shell])

  (defn inline-css [html-file inline-file]
    (shell/sh "juice"
              "--web-resources-images" "false"
              "--remove-style-tags" "true"
              "--preserve-media-queries" "true"
              "--preserve-font-faces" "false"
              html-file inline-file))

  ;; Generate test email HTML content from sample data

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

  ;; Bot removed email

  (def bot-removed-data (json/decode (slurp "./opt/samples/bot-removed/carrot.json")))
  (spit "./hiccup.html" (content/bot-removed-html bot-removed-data))
  )
