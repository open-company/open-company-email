(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clj-time.core :as t]
            [oc.lib.text :as text]
            [hiccup.core :as h]
            [hickory.core :as hickory]
            [oc.lib.auth :as auth]
            [oc.lib.jwt :as jwt]
            [oc.lib.user :as user]
            [oc.lib.storage :as storage]
            [oc.email.config :as config]
            [jsoup.soup :as soup]))

(def max-logo 40)

(def iso-format (time-format/formatters :date-time))
(def date-format (time-format/formatter "MMM. d"))
(def date-format-no-dot (time-format/formatter "MMM d"))
(def date-format-year (time-format/formatter "MMM. d YYYY"))
(def date-format-year-comma (time-format/formatter "MMM. d, YYYY"))
(def day-month-date-year (time-format/formatter "EEEE, MMM. dd, YYYY"))
(def reminder-date-format (time-format/formatter "EEEE, MMMM d"))
(def reminder-date-format-year (time-format/formatter "EEEE, MMMM d YYYY"))

(defn- profile-url [org-slug]
  (str (s/join "/" [config/web-url org-slug "all-posts"]) "?user-settings=notifications"))

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
(def invite-link-instructions "Click here to join your team:")
(def invite-button "accept_invitation")

(def share-message "%s shared a post with you")
(def share-cta "view_post")

(def board-invite-title "You’ve been invited to a private section on Carrot")
(def board-invite-message "%s has invited you to join a private section on Carrot called “")
(def board-invite-message-2 (str "”. " carrot-explainer))
(def board-invite-button "view_section")

(def reset-message "Password reset")
(def reset-instructions "If you didn't request a password reset, you can ignore this email.")
(def reset-instructions-2 "Please click the link below to reset your password:")
(def reset-button-text "reset_password")

(def verify-message "Please verify your email")
(def verify-instructions (str "Welcome to Carrot! " carrot-explainer))
(def verify-instructions-2 "Please click the link below to verify your account:")
(def verify-button-text "verify_email")

(def digest-title-daily "☕ Your %s morning digest")

;; Follow-up notification
(def follow-up-subject-text "%s created a follow-up for you")

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


(defn- email-header [type]
  [:table {:class "row header-table"
           :valign "middle"
           :align "center"}
    [:tr
      [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
        (vspacer 24 "header-table " "header-table")
        [:table {:class "row header-table"}
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

(declare reminder-notification-settings-footer)

(defn- email-footer [data type]
  (let [digest? (= type :digest)]
    [:table {:class (str "row footer-table" (when digest? " digest-footer-table"))
             :valign "middle"
             :align "center"}
      [:tr
        [:td {:class "small-12 large-12 columns" :valign "middle" :align "center"}
          (when (= type :reminder-notification)
            (reminder-notification-settings-footer data))
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
                  "You’re setup to receive the daily digest each morning at 7 AM."]]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                (vspacer 4 "footer-table" "footer-table")]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                [:p {:class "footer-paragraph bottom-footer underline-link"}
                  [:a {:href (str config/web-url "/" (:org-slug data) "/all-posts?user-settings=notifications")}
                    "Manage your daily digest settings"]]]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                (vspacer 16 "footer-table" "footer-table")]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                [:p {:class "footer-paragraph bottom-footer"}
                  "Have a feature idea or request?"]]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                (vspacer 4 "footer-table" "footer-table")]])
            (when digest?
              [:tr [:th {:class "small-12 lrge-12"}
                [:p {:class "footer-paragraph bottom-footer underline-link"}
                  [:a {:href "mailto:hello@carrot.io"}
                    "Chat with us"]]]])]
          (vspacer 40 "footer-table" "footer-table")]]]))

;; ----- Posts common ----

(defn- board-access [entry]
  (cond
    (= (:board-access entry) "private")
    " (private)"
    (= (:board-access entry) "public")
    " (public)"
    :else
    ""))

(defn- post-date [timestamp]
  (let [d (time-format/parse iso-format timestamp)
        n (time/now)
        same-year? (= (time/year n) (time/year d))
        output-format (if same-year? date-format date-format-year)]
    (time-format/unparse output-format d)))

(defn- post-attribution [entry]
  (let [publisher-name (-> entry :publisher :name)
        attribution (when (seq (:comment-count-label entry))
                      [:span
                        (str " " (:comment-count-label entry))
                        (when (seq (:new-comment-label entry))
                          [:label.new-comments
                            (str " (" (:new-comment-label entry) ")")])])
        paragraph-text [:span
                         publisher-name " in " (:board-name entry)
                         (board-access entry)
                         attribution]]
    (paragraph paragraph-text "" "text-left attribution")))

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
  [:div
    [:p.post-body
      cleaned-body]])

(defn- post-block
  ([entry] (post-block entry (:url entry)))
  ([entry entry-url]
  (let [publisher (:publisher entry)
        avatar-url (user/fix-avatar-url config/filestack-api-key (:avatar-url publisher))
        headline (post-headline entry)
        vid (:video-id entry)
        abstract (:abstract entry)
        cleaned-body (if (clojure.string/blank? abstract) (text/truncated-body (:body entry)) abstract)
        has-body (seq cleaned-body)]
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
          (when has-body
            (spacer 4 ""))
          (when has-body
            (post-body cleaned-body))
          (spacer 8 "")
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
                        (:video-duration entry)]]]]]])
          (when vid
            (spacer 16 ""))
          (post-attribution entry)]]])))

(defn- digest-post-block
  [user entry]
  (let [publisher (:publisher entry)
        avatar-url (user/fix-avatar-url config/filestack-api-key (:avatar-url publisher))
        vid (:video-id entry)
        abstract (:abstract entry)
        cleaned-body (if (clojure.string/blank? abstract) (text/truncated-body (:body entry)) abstract)
        has-body (seq cleaned-body)
        published-date (time-format/unparse date-format-no-dot (time-format/parse iso-format (:published-at entry)))
        superuser-token (auth/user-token {:user-id (:user-id user)} config/auth-server-url config/passphrase "Email")]
    [:table
      {:cellpadding "0"
       :cellspacing "0"
       :border "0"
       :class "row digest-post-block"}
      [:tr
        [:td
          (spacer 24)]]
      [:tr
        [:td.digest-post-avatar-td
          [:img.digest-post-avatar
            {:src avatar-url}]]
        [:td
          [:table
            {:cellpadding "0"
             :cellspacing "0"
             :border "0"
             :class "row digest-post-block"}
            [:tr [:td
              (h2 (str (:headline entry) " →") (:url entry) "" "digest-post-title")]]
            (when has-body
              [:tr [:td (spacer 4)]])
            (when has-body
              [:tr [:td
                (post-body cleaned-body)]])
            [:tr [:td 
                (spacer 8)]]
            [:tr [:td
              [:p.digest-post-footer 
                (str
                 (:name (:publisher entry))
                 " in "
                 (:board-name entry)
                 (board-access entry)
                 (when (:comment-count-label entry)
                   (str " " (:comment-count-label entry))))
                (when (:new-comment-label entry)
                  [:span.new-comments
                    (str "(" (:new-comment-label entry) ")")])]]]]]]]))

(defn- posts-with-board-name [board]
  (let [board-name (:name board)]
    (assoc board :posts (map #(assoc % :board-name board-name) (:posts board)))))

(defn- posts-for-board [board]
  (let [pretext (:name board)
        posts (:posts board)]
    (concat [{:type :board :name pretext}] posts)))

(defn digest-title [org-name]
  (format digest-title-daily (or org-name "Carrot")))

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
  (let [boards (map posts-with-board-name (:boards digest))
        posts (mapcat posts-for-board boards)
        digest-url (get-digest-url digest)
        boards (map posts-with-board-name (:boards digest))
        all-posts (mapcat :posts boards)
        sorted-posts (sort-by (juxt :follow-up :board-name :published-at) all-posts)
        follow-up-posts (filter :follow-up sorted-posts)
        non-follow-up-posts (filter (comp not :follow-up) sorted-posts)
        user {:user-id (:user-id digest)
              :name (str (:first-name digest) " " (:last-name digest))}]
    [:td {:class "small-12 large-12" :valign "middle" :align "center"}
      [:center
        (spacer 40)
        (when (seq follow-up-posts)
          [:table
            {:cellpadding "0"
             :cellspacing "0"
             :border "0"
             :class "digest-content follow-up"}
            [:tr
              [:td
                [:label.digest-group-title
                  "FOLLOW-UP"]]]
            [:tr
              [:td
                (for [p follow-up-posts]
                  [:table
                    {:cellpadding "0"
                     :cellspacing "0"
                     :border "0"
                     :class "row digest-posts-container"}
                    [:tr
                      [:td {:class "small-12 large-12 columns"}
                        (digest-post-block user p)]]])]]])
        (when (seq follow-up-posts)
          (spacer 32))
        (when (seq non-follow-up-posts)
          [:table
            {:cellpadding "0"
             :cellspacing "0"
             :border "0"
             :class "digest-content"}
            [:tr
              [:td
                [:label.digest-group-title
                  "NEW ACTIVITY"]]]
            [:tr
              [:td
                (for [p non-follow-up-posts]
                  [:table
                    {:cellpadding "0"
                     :cellspacing "0"
                     :border "0"
                     :class "row digest-posts-container"}
                    [:tr
                      [:td {:class "small-12 large-12 columns"}
                        (digest-post-block user p)]]])]]])
        (spacer 40)]]))

;; Reminder alert

(defn- first-name [user-map]
  (or (:first-name user-map) (first (s/split (:name user-map) #"\s"))))

(defn reminder-alert-headline [data]
  (str "Hi " (first-name (:assignee (:reminder (:notification data)))) ", it's time to update your team"))

(defn reminder-alert-settings-footer [org-slug frequency]
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
              [:a {:href (profile-url org-slug)}
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
        reminder-data (:reminder (:notification reminder))
        author (:author reminder-data)
        title (:headline reminder-data)
        headline (reminder-alert-headline reminder)
        create-post-url (str (s/join "/" [config/web-url org-slug "all-posts"]) "?new")]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      [:center
        (when logo? (org-logo {:org-name org-name
                               :org-logo-url logo-url
                               :org-logo-width logo-width
                               :org-logo-height logo-height
                               :align "center"
                               :class "small-12 large-12 first last columns"}))
        (when logo? (spacer 32))
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
        (reminder-alert-settings-footer org-slug (:frequency reminder-data))]]))

;; Reminder notification

(defn- reminder-due-date [timestamp]
  (let [d (time-format/parse iso-format timestamp)
        n (time/now)
        same-year? (= (time/year n) (time/year d))
        output-format (if same-year? reminder-date-format reminder-date-format-year)]
    (time-format/unparse output-format d)))

(defn reminder-notification-headline [data]
  (str (first-name (:author (:reminder (:notification data)))) " created a new reminder for you"))

(defn- frequency-string [f]
  (case (s/lower-case f)
    "weekly" "Weekly"
    "biweekly" "Every other week"
    "monthly" "Monthly"
    "Quarterly"))

(defn reminder-notification-subline [reminder-data]
  (str (frequency-string (:frequency reminder-data)) " starting " (reminder-due-date (:next-send reminder-data))))

(defn reminder-notification-settings-footer [data]
  (let [org (:org data)]
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
                [:a {:href (profile-url (:slug org))}
                  "Carrot"]]]]]
        (vspacer 32 "footer-table reminders-bottom-footer" "reminders-footer")]]]))

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
        title (:headline reminder-data)
        headline (reminder-notification-headline reminder)
        subline (reminder-notification-subline reminder-data)
        reminders-url (str (s/join "/" [config/web-url org-slug "all-posts"]) "?reminders")]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      [:center
        (when logo? (org-logo {:org-name org-name
                               :org-logo-url logo-url
                               :org-logo-width logo-width
                               :org-logo-height logo-height
                               :align "center"
                               :class "small-12 large-12 first last columns"}))
        (when logo? (spacer 32))
        (h1 headline "center-align")
        (spacer 65)
        (h2-no-link title "center-align" "reminder-headline")
        (spacer 8)
        (paragraph subline "center-align" "center-align")
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
                             :org-logo-height logo-height
                             :class "small-12 large-12 first last columns"}))
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

(defn follow-up-subject [data]
  (let [msg (keywordize-keys data)
        follow-up-author (-> data :notification :follow-up :author)
        author-name (user/name-for follow-up-author)]
    (format follow-up-subject-text author-name)))

(defn- follow-up-post-block
  ([entry entry-url]
  (let [publisher (:publisher entry)
        headline (post-headline entry true)
        abstract (:abstract entry)
        cleaned-body (if (clojure.string/blank? abstract) (text/truncated-body (:body entry)) abstract)
        has-body (seq cleaned-body)
        publisher-name (-> entry :publisher :name)
        paragraph-text [:span
                         publisher-name " in " (:board-name entry)
                         (board-access entry)]]
    [:table
      {:cellpadding "0"
       :cellspacing "0"
       :border "0"
       :class "row"}
      [:tr
        [:td
          [:div
            {:class "follow-up-post-block"}
            (h2 headline entry-url "")
            (when has-body
              (spacer 8 ""))
            (when has-body
              (post-body cleaned-body))
            (spacer 12 "")
            (paragraph paragraph-text "" "text-left attribution")]]]])))

(defn- follow-up-notification-content [msg]
  (let [notification (:notification msg)
        org (:org notification)
        logo-url (:logo-url org)
        logo-width (:logo-width org)
        logo-height (:logo-height org)
        logo? (not (s/blank? logo-url))
        org-name (:name org)
        follow-up (:follow-up notification)
        follow-up-author (:author follow-up)
        author-name (user/name-for follow-up-author)
        post-data (get-post-data msg)
        message (follow-up-subject msg)
        entry-url (s/join "/" [config/web-url
                               (:slug org)
                               (:board-slug post-data)
                               "post"
                               (:uuid post-data)])]
    [:td {:class "small-12 large-12 columns main-wrapper vertical-padding" :valign "middle" :align "center"}
      (when (:avatar-url follow-up-author)
        [:table {:class "row"}
          [:tr {:class "small-12 large-12 columns"}
            [:th {:class "small-12 large-12 columns"}
              [:table {:class "small-12 large-12 columns"}
                [:tr {:class "small-12 large-12 columns"}
                  [:th {:class "small-12 large-12 columns"}
                    [:img.follow-up-author
                      {:src (user/fix-avatar-url config/filestack-api-key (:avatar-url follow-up-author))}]]]]]]])

      (when (:avatar-url follow-up-author)
        (spacer 24))
      [:table {:class "row"}
        [:tr
          [:th {:class "small-12 large-12 columns"}
            [:h1 {:class "follow-up-header"} message]]]]
      (spacer 16)
      (follow-up-post-block post-data entry-url)
      (spacer 24)
      [:table {:class "row"}
        [:tr
          [:th {:class "small-12 large-12 columns"}
            [:a {:href entry-url
                 :class "follow-up-button-cta"}
              "View post"]]]]
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
        attribution (str (:name publisher) " posted to " (:board-name entry) (board-access entry))
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

(defn- notify-intro [msg]
  (let [notification (:notification msg)
        mention? (:mention? notification)
        comment? (:interaction-id notification)]
    (if mention?
      (if comment?
        (str "You were mentioned in a comment: ")
        (str "You were mentioned in a post: "))
      (str "There is a new comment on your post:"))))

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
        intro (notify-intro msg)
        notification-author (:author notification)
        notification-author-name (:name notification-author)
        notification-author-url (user/fix-avatar-url config/filestack-api-key (:avatar-url notification-author))
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
      (when logo? (org-logo (assoc msg :class "small-12 large-12 first last columns")))
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
  (let [type (:type data)
        digest? (= type :digest)]
    [:body
      (when digest?
        (go-to-posts-script data))
      (case type
        :reset (preheader reset-message)
        :verify (preheader "Welcome to Carrot!")
        :invite (preheader invite-message)
        :board-notification (preheader board-invite-title)
        :share-link (preheader (share-title data))
        :digest (preheader "See the latest updates and news from your team.")
        :notify (preheader (notify-intro data))
        :reminder-notification (preheader (reminder-notification-headline data))
        :reminder-alert (preheader (reminder-alert-headline data))
        :follow-up (preheader "A follow-up was created for you."))
      [:table {:class "body"
               :with "100%"}
        [:tr
          [:td {:valign "middle"
                :align "center"}
            [:center
              (email-header type)
              [:table {:class (str "row " (cond
                                            digest? "digest-email-content"
                                            (= type :follow-up) "follow-up-email-content"
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
                    :follow-up (follow-up-notification-content data))]]
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

(defn follow-up-html [follow-up-data]
  (html (-> follow-up-data
          (assoc :subject (follow-up-subject follow-up-data))
          (assoc :text (follow-up-subject follow-up-data))) :follow-up))

(defn follow-up-text [follow-up-data]
  (let [link (:url follow-up-data)]
    (str (follow-up-subject follow-up-data) ".\n\n"
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

  ;; Follow-up notification
  (def follow-up-data (json/decode (slurp "./opt/samples/follow-up/carrot.json")))
  (spit "./hiccup.html" (content/follow-up-html follow-up-data))
  )
