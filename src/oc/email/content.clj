(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.format :as format]
            [hiccup.core :as h]
            [oc.lib.text :as text]
            [oc.email.config :as config]))

(def max-logo 32)

(def profile-url (str config/web-url "/profile"))

(def iso-format (format/formatters :date-time)) ; ISO 8601
(def attribution-format (format/formatter "MMMM d"))

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

(def tagline "Better informed, less noise.")
(def carrot-explainer "Carrot is the company digest that keeps everyone aligned around what matters most.")

(def sent-by-text "Sent by Carrot")

(def invite-message "invited you to join your team on Carrot")

(def board-invite-message "You've been invited to a private section on Carrot")
(def board-invite-explainer "Private sections of the digest are only available to invited team members.")

(def reset-message "Someone (hopefully you) requested a new password for your Carrot account")
(def reset-instructions "Click the link below to reset your password.")
(def reset-button-text "Reset Password")
(def reset-ignore "If you didn't request a password reset, you can safely ignore this email.")

(def verify-message "Someone (hopefully you) created a Carrot account")
(def verify-instructions "Click the link below to verify your email address.")
(def verify-button-text "Verify Email")
(def verify-ignore "If you didn't create a Carrot account, you can safely ignore this email.")

(defn- logo
  "Company logo that includes Carrot branding."
  [logo-url org-name]
  [:table {:class "row header"} 
    [:tr
      [:th {:class "small-1 large-2 first columns"}]
      [:th {:class "small-10 large-8 columns"}
        [:table
          [:tr
            [:th
              [:img {:class "float-left logo"
                     :width 183
                     :height 113
                     :style {:max-width "initial"}
                     :src "https://open-company-assets.s3.amazonaws.com/email_left_bubbles.png"}]]
            [:th
              [:center
                [:img {:class "float-center logo"
                       :style "background-color: #ffffff;max-height: 120px;max-width: 213px;"
                       :src logo-url
                       :alt (str org-name " logo")}]]]
            [:th
              [:img {:class "float-left logo"
                     :width 180
                     :height 121
                     :style {:max-width "initial"}
                     :src "https://open-company-assets.s3.amazonaws.com/email_right_bubbles.png"}]]]]]
      [:th {:class "small-1 large-2 last columns"}]]])

(defn- minimal-logo
  "Centered company alone."
  [{org-name :org-name logo-url :logo-url logo-height :logo-height logo-width :logo-width}]
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

(defn- carrot-logo []
  [:table {:class "row header"} 
    [:tr
      [:th {:class "small-12 large-12 first last columns"} 
        [:table
          [:tr
            [:th
              [:center
                [:img {:class "float-center logo"
                       :style "background-color: #ffffff;max-height: 71px;max-width: 71px;"
                       :src "https://open-company-assets.s3.amazonaws.com/carrot-logo.png"
                       :alt "Carrot logo"}]]]]]]]])

(defn- carrot-digest-logo []
  [:tr
    [:th {:class "small-12 large-12 first last columns"}
      [:table
        [:tr
          [:th
            [:center
              [:img {:width "22", :height "39", :src "https://open-company.s3.amazonaws.com/carrot-grey-logo-min.png", :alt "Carrot logo"}]]]
          [:th {:class "expander"}]]]]])

(defn- digest-banner [frequency]
  (let [weekly? (= (keyword frequency) :weekly)
        banner-src (if weekly?
                      "https://open-company.s3.amazonaws.com/weekly-digest-header-min.png"
                      "https://open-company.s3.amazonaws.com/daily-digest-header-min.png")
        banner-alt (if weekly?
                      "Your weekly digest"
                      "Your daily digest")]
    [:table {:class "row banner"}
      [:tr
        [:th {:class "small-12 large-12 first last columns"}
          [:table
            [:tr
              [:th
                [:img {:src banner-src, :alt banner-alt}]]
              [:th {:class "expander"}]]]]]]))

(defn- message [update]
  [:table {:class "row note"}
    [:tr
      [:th {:class "small-12 large-12 first last columns note"}
        (:note update)]]])

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
  ([content css-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-1 large-2 first columns"}]
      [:th {:class "small-10 large-8 columns"}
        [:p {:class "text-center"} content]]
      [:th {:class "small-1 large-2 last columns"}]]]))

(defn- h1 [content]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-2 large-2 first columns"}]
      [:th {:class "small-8 large-8 columns"}
        [:h1 {:class "text-center"} content]]
      [:th {:class "small-2 large-2 last columns"}]
      [:th {:class "expander"}]]])

(defn- h2 [content css-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-1 large-2 first columns"}]
      [:th {:class "small-10 large-8 columns"}
        [:h2 {:class "text-center"} content]]
      [:th {:class "small-1 large-2 last columns"}]
      [:th {:class "expander"}]]])

(defn- cta-button [cta url css-class]
  [:table {:class (str "row " css-class)}
    [:tr
      [:th {:class "small-1 large-2 columns first"}]
      [:th {:class "small-10 large-8 columns"}
        [:a {:href url}
          [:table {:class "cta-button"}
            [:tr
              [:th
                [:span {:class "text-center button-text"}
                  cta]]]]]]
      [:th {:class "small-1 large-2 columns last"}]
      [:th {:class "expander"}]]])

; (defn- digest-footer [digest]
;   [:table {:class "row footer"}
;     (tr-spacer 40)
;     (carrot-digest-logo)
;     (tr-spacer 17)
;     sent-by
;     (tr-spacer 17)
;     (you-receive (:digest-frequency digest))
;     (change-to (:digest-frequency digest))
;     (tr-spacer 40)])

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

(defn- board-notification-content [notice]
  (let [logo-url (:logo-url notice)
        logo? (not (s/blank? logo-url))
        board-url (:board-url notice)
        board-name (:board-name notice)
        org-name (:org-name notice)]
    [:td
      (spacer 40)
      (when logo? (minimal-logo notice))
      (when logo? (spacer 35))
      (h1 board-invite-message)
      (spacer 31)
      (spacer 35 "body-block top" "body-spacer")
      (h2 board-name "body-block")
      (spacer 28 "body-block" "body-spacer")
      (paragraph board-invite-explainer "body-block")
      (spacer 35 "body-block" "body-spacer")
      (cta-button (str "View " board-name) board-url "body-block")
      (spacer 40 "body-block bottom" "body-spacer")
      (spacer 33)
      (transactional-footer)]))

(defn- invite-content [invite]
  (let [logo-url (:logo-url invite)
        logo? (not (s/blank? logo-url))
        org-name (:org-name invite)
        from (if (s/blank? (:from invite)) "Someone" (:from invite))]
    [:td
      (spacer 40)
      (when logo? (minimal-logo invite))
      (when logo? (spacer 35))
      (h1 (str from " " invite-message))
      (spacer 31)
      (spacer 35 "body-block top" "body-spacer")
      (h2 org-name "body-block")
      (spacer 28 "body-block" "body-spacer")
      (paragraph carrot-explainer "body-block")
      (spacer 35 "body-block" "body-spacer")
      (cta-button (str "Join " org-name " on Carrot") (:token-link invite) "body-block")
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
        logo-url (:logo-url msg)
        logo? (not (s/blank? logo-url))
        org-name (:org-name msg)]
    [:td
      (spacer 40)
      (when logo? (minimal-logo msg))
      (when logo? (spacer 35))
      (spacer 35 "body-block top" "body-spacer")
      (h2 (:message message) "body-block")
      (spacer 28 "body-block" "body-spacer")
      (paragraph (:ignore message) "body-block")
      (spacer 35 "body-block" "body-spacer")
      (cta-button (:button-text message) (:token-link msg) "body-block")
      (spacer 40 "body-block bottom" "body-spacer")
      (spacer 33)
      (transactional-footer)]))

(defn- share-link-content [entry]
  (let [org-name (:org-name entry)
        org-name? (not (s/blank? org-name))
        headline (:headline entry)
        headline? (not (s/blank? headline))
        link-title (if headline? headline (str org-name " Post"))
        org-slug (:org-slug entry)
        secure-uuid (:secure-uuid entry)
        origin-url config/web-url
        update-url (s/join "/" [origin-url org-slug "post" secure-uuid])]
    [:table {:class "note"}
      [:tr
        [:td 
          [:table {:class "row note"}
            [:tr
              [:th {:class "small-12 large-12 first last columns note"}
                "Check out the latest"
                (when org-name? (str " from " org-name))
                ": " [:a {:href update-url} link-title]]]]]]]))

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

(defn- comment-attribution [comment-count comment-authors]
  (let [attribution (text/attribution 2 comment-count "comment" comment-authors)]
    [:tr
      [:th {:class "small-1 large-1 first columns"}]
      [:th {:class "small-10 large-10 columns"}
        [:table
          [:tr
            [:th
              [:p {:class "attribution"} attribution]]
            [:th {:class "expander"}]]]]
      [:th {:class "small-1 large-1 last columns"}]]))

(defn- post-attribution [publisher published-at frequency]
  (let [attribution (if (= (keyword frequency) :daily)
                      (:name publisher)
                      (str (str (:name publisher) " on " (->> published-at
                                                            (format/parse iso-format)
                                                            (format/unparse attribution-format)))))]
    [:tr
      [:th {:class "small-1 large-1 first columns"}]
      [:th {:class "small-10 large-10 columns"}
        [:table
          [:tr
            [:th
              [:p {:class "attribution"} attribution]]
            [:th {:class "expander"}]]]]
      [:th {:class "small-1 large-1 last columns"}]]))

(defn- post-link [headline url]
  [:tr
    [:th {:class "small-1 large-1 first columns"}]
    [:th {:class "small-10 large-10 columns"}
      [:table
        [:tr
          [:th
            [:a {:class "post-link"
                 :href url}
              headline]]
          [:th {:class "expander"}]]]]
    [:th {:class "small-1 large-1 last columns"}]])

(defn- board-name [bname]
  [:tr
    [:th {:class "small-1 large-1 first columns"}]
    [:th {:class "small-10 large-10 columns"}
      [:table
        [:tr
          [:th
            [:h2 bname]]
          [:th {:class "expander"}]]]]
    [:th {:class "small-1 large-1 last columns"}]])

(defn- post [data frequency]
  (let [comment-count (:comment-count data)
        comments? (pos? comment-count)]
    [(tr-spacer 27)
     (post-link (:headline data) (:url data))
     (tr-spacer 11)
     (post-attribution (:publisher data) (:published-at data) frequency)
     (when comments? (tr-spacer 3))
     (when comments? (comment-attribution comment-count (:comment-authors data)))]))

(defn- board [data frequency]
  [:table {:class "row board"}
    (tr-spacer 33)
    (board-name (:name data))
    (apply concat ; flattens 1-level
      (let [posts (:posts data)]
        (map #(post % frequency) posts)))
    (tr-spacer 39)])

(defn- digest-content [digest]
  [:td
    (spacer 40)
    (minimal-logo digest)
    (spacer 32)
    (digest-banner (:digest-frequency digest))
    (let [boards (:boards digest)]
      (map #(board % (:digest-frequency digest)) boards))
    ;(digest-footer digest)
    ])

(defn- note 
  [update trail-space?]
  [:table {:class "note"}
    [:tr
      [:td
        (spacer 15 "note")
        (message update)
        (when trail-space? (spacer 22 "note"))]]])

(defn- body [data]
  (let [type (:type data)
        trail-space? (not= type :share-link)]
    [:body
      [:table {:class "body"}
        [:tr
          [:td {:class "float-center", :align "center", :valign "top"}
            (when-not (s/blank? (:note data)) (note data trail-space?))
            (when (and (s/blank? (:note data)) (= type :share-link)) (spacer 15 "note"))
            (if (= type :share-link)
              (share-link-content data)     
              [:center
                [:table {:class "container"}
                  [:tr
                    (case type
                      :reset (token-content type data)
                      :verify (token-content type data)
                      :invite (invite-content data)
                      :board-notification (board-notification-content data)
                      :digest (digest-content data))]]])]]]]))

(defn- head [data]
  (let [type (:type data)
        css (if (= type :digest) "oc-digest.css" "oc-transactional.css")]
    [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
      [:head 
        [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
        [:meta {:name "viewport", :content "width=device-width"}]
        [:link {:rel "stylesheet", :href "resources/css/foundation.css"}]
        [:link {:rel "stylesheet", :href (str "resources/css/" css)}]
        [:title]]
      (body data)]))

(defn- html [data type]
  (str doc-type (h/html (head (assoc (keywordize-keys data) :type type)))))

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
  (def share-request (json/decode (slurp "./opt/samples/share/green-labs.json")))
  (spit "./hiccup.html" (content/share-link-html (assoc share-request :note note)))

  (def share-request (json/decode (slurp "./opt/samples/share/new.json")))
  (spit "./hiccup.html" (content/share-link-html (assoc share-request :note "")))

  (def share-request (json/decode (slurp "./opt/samples/updates/bago.json")))
  (spit "./hiccup.html" (content/share-link-html (assoc share-request :note "")))

  
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