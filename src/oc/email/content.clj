(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.format :as format]
            [hiccup.core :as h]
            [oc.email.config :as config]))

(def max-logo 32)

(def profile-url (str config/web-url "/profile"))

(def iso-format (format/formatters :date-time)) ; ISO 8601
(def attribution-format (format/formatter "MMMM d"))

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

;; ----- Copy -----

(def tagline "Better informed, less noise.")
(def carrot-explainer "Carrot is the company digest that keeps everyone aligned around what matters most.")

(def sent-by-text "Sent by Carrot")

(def invite-message "invited you to join your team on Carrot")

(def share-message "sent you a post")
(def share-cta "View the post")

(def board-invite-message "invited you to a private section on Carrot")
(def anonymous-board-invite-message "You've been invited to a private section on Carrot")
(def board-invite-explainer "Private sections of the digest are only available to invited team members.")

(def reset-message "Someone (hopefully you) requested a new password for your Carrot account")
(def reset-instructions "Click the link below to reset your password.")
(def reset-button-text "Reset Password")
(def reset-ignore "If you didn't request a password reset, you can safely ignore this email.")

(def verify-message "Someone (hopefully you) created a Carrot account")
(def verify-instructions "Click the link below to verify your email address.")
(def verify-button-text "Verify Email")
(def verify-ignore "If you didn't create a Carrot account, you can safely ignore this email.")

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
                  [:center 
                    [:img {:class "float-center logo"
                           :align "center"
                           :style (str "background-color: #fff; max-height: " max-logo "px; max-width: " max-logo "px;")
                           dimension size
                           :src logo-url
                           :alt (str org-name " logo")}]]]]]]]])))

(defn- carrot-footer-logo []
  [:tr
    [:th {:class "small-12 large-12 first last columns"} 
      [:table
        [:tr
          [:th
            [:center
              [:img {:width 13
                     :height 24
                     :src "https://open-company.s3.amazonaws.com/carrot-logo-grey-min.png"
                     :alt "Carrot logo"}]]]]]]])

(defn- spacer-table [pixels css-class]
  [:table {:class "spacer"}
    [:tr
      [:th {:class css-class
            :height (str "font-size:" pixels "px")
            :style (str "font-size:" pixels "px;line-height:" pixels "px;")} " "]
      [:th {:class "expander"}]]])

(defn- tr-spacer
  ([pixels] (tr-spacer pixels ""))
  ([pixels css-class]
  [:tr [:th (spacer-table pixels css-class)]]))

(defn- spacer
  ([pixels] (spacer pixels ""))
  ([pixels outer-css-class] (spacer pixels outer-css-class ""))
  ([pixels outer-css-class inner-css-class]
  [:table {:class (str "row " outer-css-class)}
    [:tr
      [:th {:class "small-1 large-2 first columns"}]
      [:th {:class "small-10 large-8 columns"}
        (spacer-table pixels inner-css-class)]
      [:th {:class "small-1 large-2 last columns"}]
      [:th {:class "expander"}]]]))

(defn- paragraph
  ([content] (paragraph content ""))
  ([content css-class] (paragraph content css-class "text-center"))
  ([content css-class content-css-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-1 large-2 first columns"}]
      [:th {:class "small-10 large-8 columns"}
        [:p {:class content-css-class} content]]
      [:th {:class "small-1 large-2 last columns"}]]]))

(defn- h1 [content]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-2 large-2 first columns"}]
      [:th {:class "small-8 large-8 columns"}
        [:h1 {:class "text-center"} content]]
      [:th {:class "small-2 large-2 last columns"}]
      [:th {:class "expander"}]]])

(defn- h2 
  ([content css-class] (h2 content css-class "text-center"))
  ([content css-class h2-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-1 large-2 first columns"}]
      [:th {:class "small-10 large-8 columns"}
        [:h2 {:class h2-class} content]]
      [:th {:class "small-1 large-2 last columns"}]
      [:th {:class "expander"}]]]))

(defn- button [button-text url css-class button-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-1 large-2 columns first"}]
      [:th {:class "small-10 large-8 columns"}
        [:a {:href url}
          [:table {:class button-class}
            [:tr
              [:th
                [:span {:class "text-center button-text"}
                  button-text]]]]]]
      [:th {:class "small-1 large-2 columns last"}]
      [:th {:class "expander"}]]])

(defn- cta-button [cta-text url]
  (button cta-text url "body-block" "cta-button"))

(defn- you-receive [interval]
  [:tr
    [:th {:class "small-12 large-12 first last columns"}
      [:table
        [:tr
          [:th
            [:p {:class "text-center"} "You receive " [:b interval] " digests."]]
          [:th {:class "expander"}]]]]])

(def sent-by
  [:tr
    [:th {:class "small-12 large-12 first last columns"}
    [:table
      [:tr
        [:th
          [:p {:class "text-center"} [:b {} sent-by-text]]]
        [:th {:class "expander"}]]]]])

(defn- change-to [interval]
  (let [new-interval (if (= (keyword interval) :daily) "weekly" "daily")]
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
      [:table
        [:tr
          [:th
            [:p {:class "text-center"}
              [:a {:href profile-url} (str "Change to " new-interval "?")]
              " ∙ "
              [:a {:href profile-url} "Turn off digests"]]]
          [:th {:class "expander"}]]]]]))

;; Comments not shown in digests at the moment
; (defn- comment-attribution [comment-count comment-authors]
;   (let [attribution (text/attribution 2 comment-count "comment" comment-authors)]
;     [:tr
;       [:th {:class "small-1 large-1 first columns"}]
;       [:th {:class "small-10 large-10 columns"}
;         [:table
;           [:tr
;             [:th
;               [:p {:class "attribution"} attribution]]
;             [:th {:class "expander"}]]]]
;       [:th {:class "small-1 large-1 last columns"}]]))

(defn- transactional-footer []
  [:table {:class "row footer"}
    (carrot-footer-logo)
    (tr-spacer 10)
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:table
          [:tr
            [:th
              [:p {:class "text-center"}
                sent-by-text]]]]]]
    (tr-spacer 10)])

;; ----- Digest -----

(defn- digest-footer [digest]
  [:table {:class "row footer"}
    (tr-spacer 40)
    (carrot-footer-logo)
    (tr-spacer 17)
    sent-by
    (tr-spacer 17)
    (you-receive (:digest-frequency digest))
    (change-to (:digest-frequency digest))
    (tr-spacer 40)])

(defn- post [entry]
  [(spacer 36 "body-block" "body-spacer")
   (paragraph (str (-> entry :publisher :name) " posted in " (:board-name entry)) "body-block" "attribution")
   (spacer 15 "body-block" "body-spacer")
   (h2 (:headline entry) "body-block" "post-title")
   (spacer 15 "body-block" "body-spacer")
   (button "View the post" "" "body-block" "post-button")])

(defn- posts-with-board-name [board]
  (let [board-name (:name board)]
    (assoc board :posts (map #(assoc % :board-name board-name) (:posts board)))))

(defn- digest-content [digest]
  (let [logo-url (:org-logo-url digest)
        logo? (not (s/blank? logo-url))
        weekly? (= "weekly" (:digest-frequency digest))
        org-name (:org-name digest)
        title (if weekly? (str org-name " Weekly Digest") (str org-name " Daily Digest"))
        boards (map posts-with-board-name (:boards digest))
        posts (mapcat :posts boards)]
    [:td
      (spacer 40)
      (when logo? (org-logo digest))
      (when logo? (spacer 35))
      (h1 title)
      (spacer 31)
      (spacer 1 "body-block top" "body-spacer")
      (mapcat post posts)
      (spacer 40 "body-block bottom" "body-spacer")
      (digest-footer digest)]))

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
        invite-message (if (s/blank? from) anonymous-board-invite-message (str (s/trim from) " " board-invite-message))]
    [:td
      (spacer 40)
      (when logo? (org-logo {:org-name org-name
                             :org-logo-url logo-url
                             :org-logo-width logo-width
                             :org-logo-height logo-height}))
      (when logo? (spacer 35))
      (h1 invite-message)
      (spacer 31)
      (spacer 35 "body-block top" "body-spacer")
      (h2 board-name "body-block")
      (spacer 28 "body-block" "body-spacer")
      (paragraph board-invite-explainer "body-block")
      (spacer 35 "body-block" "body-spacer")
      (cta-button (str "View " board-name) board-url)
      (spacer 40 "body-block bottom" "body-spacer")
      (spacer 33)
      (transactional-footer)]))

(defn- invite-content [invite]
  (let [logo-url (:org-logo-url invite)
        logo? (not (s/blank? logo-url))
        org-name (:org-name invite)
        from (if (s/blank? (:from invite)) "Someone" (:from invite))]
    [:td
      (spacer 40)
      (when logo? (org-logo invite))
      (when logo? (spacer 35))
      (h1 (str from " " invite-message))
      (spacer 31)
      (spacer 35 "body-block top" "body-spacer")
      (h2 org-name "body-block")
      (spacer 28 "body-block" "body-spacer")
      (paragraph carrot-explainer "body-block")
      (spacer 35 "body-block" "body-spacer")
      (cta-button (str "Join " org-name " on Carrot") (:token-link invite))
      (spacer 40 "body-block bottom" "body-spacer")
      (spacer 33)
      (transactional-footer)]))

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

(defn- token-content [token-type msg]
  (let [message (token-prep token-type msg)
        logo-url (:org-logo-url msg)
        logo? (not (s/blank? logo-url))
        org-name (:org-name msg)]
    [:td
      (spacer 40)
      (when logo? (org-logo msg))
      (when logo? (spacer 35))
      (spacer 35 "body-block top" "body-spacer")
      (h2 (:message message) "body-block")
      (spacer 28 "body-block" "body-spacer")
      (paragraph (:ignore message) "body-block")
      (spacer 35 "body-block" "body-spacer")
      (cta-button (:button-text message) (:token-link msg))
      (spacer 40 "body-block bottom" "body-spacer")
      (spacer 33)
      (transactional-footer)]))

(defn- share-content [entry]
  (let [logo-url (:org-logo-url entry)
        logo? (not (s/blank? logo-url))
        org-name (:org-name entry)
        org-name? (not (s/blank? org-name))
        headline (:headline entry)
        org-slug (:org-slug entry)
        sharer (:sharer-name entry)
        attribution (str (-> entry :publisher :name) " posted to " (:board-name entry))
        note (:note entry)
        note? (not (s/blank? note))
        from (if (s/blank? sharer) "Someone" sharer)
        secure-uuid (:secure-uuid entry)
        origin-url config/web-url
        entry-url (s/join "/" [origin-url org-slug "post" secure-uuid])]
    [:td
      (spacer 40)
      (when logo? (org-logo entry))
      (when logo? (spacer 35))
      (h1 (str from " " share-message))
      (spacer 31)
      (spacer 35 "body-block top" "body-spacer")
      (h2 headline "body-block")
      (spacer 11 "body-block" "body-spacer")
      (paragraph attribution "body-block" "text-center attribution")
      (when note? (spacer 28 "body-block" "body-spacer"))
      (when note? (paragraph note "body-block"))
      (spacer 35 "body-block" "body-spacer")
      (cta-button share-cta entry-url)
      (spacer 40 "body-block bottom" "body-spacer")
      (spacer 33)
      (transactional-footer)]))

;; ----- General HTML, common to all emails -----

(defn- body [data]
  (let [type (:type data)
        trail-space? (not= type :share-link)]
    [:body
      [:table {:class "body"}
        [:tr
          [:td {:class "float-center", :align "center", :valign "top"}
            [:center
              [:table {:class "container"}
                [:tr
                  (case type
                    :reset (token-content type data)
                    :verify (token-content type data)
                    :invite (invite-content data)
                    :board-notification (board-notification-content data)
                    :share-link (share-content data)
                    :digest (digest-content data))]]]]]]]))

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