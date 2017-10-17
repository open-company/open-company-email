(ns oc.email.content
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.format :as format]
            [hiccup.core :as h]
            [oc.email.config :as config]))

(def iso-format (format/formatters :date-time)) ; ISO 8601
(def link-format (format/formatter "YYYY-MM-dd")) ; Format for date in URL of stakeholder-update links

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

(def tagline "Grow together with announcements, updates, and stories that bring teams closer.")

(def reset-message "Someone (hopefully you) requested a new password for your Carrot account.")
(def reset-instructions "Click the link below to reset your password.")
(def reset-button-text "RESET PASSWORD ➞")
(def reset-ignore "If you didn't request a password reset, you can ignore this email.")

(def verify-message "Someone (hopefully you) created a Carrot account.")
(def verify-instructions "Click the link below to verify your email address.")
(def verify-button-text "VERIFY EMAIL ➞")
(def verify-ignore "If you didn't create a Carrot account, you can ignore this email.")

(defn- logo [logo-url org-name]
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

(defn- message [update]
  [:table {:class "row note"}
    [:tr
      [:th {:class "small-12 large-12 first last columns note"}
        (:note update)]]])

(defn- spacer
  ([pixels] (spacer pixels ""))
  ([pixels class-name]
  [:table {:class (str "row " class-name)}
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:table
          [:tr
            [:th
              [:table {:class "spacer"}
                [:tr
                  [:td {:height (str "font-size:" pixels "px")
                        :style (str "font-size:" pixels "px;line-height:" pixels "px;")} " "]]]]
            [:th {:class "expander"}]]]]]]))

(defn- paragraph [content]
  [:table {:class "row"}
    [:tr
      [:th {:class "small-1 large-2 first columns"}]
      [:th {:class "small-10 large-8 columns"}
        [:p {:class "text-center"} content]]
      [:th {:class "small-1 large-2 last columns"}]]])

(defn- cta-button [cta url]
  [:table {:class "row"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          [:table
            [:tbody
              [:tr
                [:th
                  [:center
                    [:table {:class "button oc radius large float-center"}
                      [:tbody
                        [:tr
                          [:td
                            [:table
                              [:tbody
                                [:tr
                                  [:td
                                    [:a {:href url} cta]]]]]]]]]]]
                [:th {:class "expander"}]]]]]]]])

(defn- invite-content [invite]
  (let [logo-url (:logo-url invite)
        logo? (not (s/blank? logo-url))
        org-name (:org-name invite)
        first-name (if (s/blank? (:first-name invite)) "there" (:first-name invite))]
    [:td
      (when logo? (spacer 20))
      (when logo? (logo logo-url org-name))
      (spacer 15)
      (paragraph (str "Hi " first-name "! " (:text invite)))
      (spacer 15)
      (cta-button "OK, Let's get started ➞" (:token-link invite))
      (spacer 30)
      (paragraph tagline)]))

(defn- token-prep [token-type msg]
  {:first-name (if (s/blank? (:first-name msg)) "there" (:first-name msg))
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
  (let [message (token-prep token-type msg)]
    [:td
      (spacer 15)
      (paragraph (str "Hi " (:first-name message) "! " (:message message)))
      (spacer 15)
      (cta-button (:button-text message) (:link message))
      (spacer 15)
      (paragraph (:ignore message))
      (spacer 15)
      (carrot-logo)
      (paragraph tagline)]))

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
                      :invite (invite-content data))]]])]]]]))

(defn- head [data]
  (let [type (:type data)
        css "oc-transactional.css"]
    [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
      [:head 
        [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
        [:meta {:name "viewport", :content "width=device-width"}]
        [:link {:rel "stylesheet", :href "resources/css/foundation.css"}]
        [:link {:rel "stylesheet", :href (str "resources/css/" css)}]
        [:link {:href "http://fonts.googleapis.com/css?family=Muli", :rel "stylesheet", :type "text/css"}]
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
    (str prefix " to join " org "Carrot.")))

(defn share-link-html [entry]
  (html entry :share-link))

; (defn- share-link-content [entry]
;   (let [org-name (:org-name entry)
;         org-name? (not (s/blank? org-name))
;         headline (:headline entry)
;         headline? (not (s/blank? headline))
;         link-title (if headline? headline (str org-name " Post"))
;         org-slug (:org-slug entry)
;         secure-uuid (:secure-uuid entry)
;         origin-url config/web-url
;         update-url (s/join "/" [origin-url org-slug "post" secure-uuid])]
;     [:table {:class "note"}
;       [:tr
;         [:td 
;           [:table {:class "row note"}
;             [:tr
;               [:th {:class "small-12 large-12 first last columns note"}
;                 "Check out the latest"
;                 (when org-name? (str " from " org-name))
;                 ": " [:a {:href update-url} link-title]]]]]]]))

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
         tagline "\n\n"
         "Open the link below to check it out.\n\n"
         link "\n\n")))

(defn token-html [token-type msg]
  (html msg token-type))

(defn token-text [token-type msg]
  (let [message (token-prep token-type msg)]
    (str "Hi " (:first-name message) "! " (:message message) "\n\n"
         (:instructions message) "\n\n"
         (:link message) "\n\n"
         (:ignore message))))

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

  (def data (clean-html (slurp "./resources/update/head.html")))
  (-> (hickory/parse data) hickory/as-hiccup first)

  (def data (clean-html (slurp "./resources/update/body.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3))

  (def data (clean-html (slurp "./resources/update/spacer.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/update/note.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-htnml (slurp "./resources/update/logo.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/update/name.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/update/title.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/update/topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/update/data-topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/update/footer.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/invite/paragraph.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/invite/cta-button.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  ;; Generate test email HTML content from various snapshots

  (require '[oc.email.content :as content] :reload)

  (def note "Enjoy the groovy update.")
  (def share-request (json/decode (slurp "./opt/samples/updates/green-labs.json")))
  (spit "./hiccup.html" (content/story-link-html (assoc share-request :note note)))

  (spit "./hiccup.html" (content/snapshot-link-html (assoc update :note note)))

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def update (json/decode (slurp "./opt/samples/updates/buff.json")))
  (spit "./hiccup.html" (content/update-html (assoc update :note note)))

  (def update (json/decode (slurp "./opt/samples/updates/new.json")))
  (spit "./hiccup.html" (content/update-html (assoc update :note "")))

  (def update (json/decode (slurp "./opt/samples/updates/bago.json")))
  (spit "./hiccup.html" (content/update-html (-> snapshot (assoc :note "") (assoc :company-slug "bago"))))

  (def update (json/decode (slurp "./opt/samples/updates/bago-no-symbol.json")))
  (spit "./hiccup.html" (content/update-html (-> snapshot (assoc :note "") (assoc :company-slug "bago"))))

  (def update (json/decode (slurp "./opt/samples/updates/growth-options.json")))
  (spit "./hiccup.html" (content/update-html (assoc update :note "")))

  (def update (json/decode (slurp "./opt/samples/updates/blanks-test.json")))
  (spit "./hiccup.html" (content/update-html (-> snapshot (assoc :note "") (assoc :company-slug "blanks-test"))))

  (def update (json/decode (slurp "./opt/samples/updates/sparse.json")))
  (spit "./hiccup.html" (content/update-html (-> snapshot (assoc :note "") (assoc :company-slug "sparse"))))

  (def invite (json/decode (slurp "./opt/samples/invites/apple.json")))
  (spit "./hiccup.html" (content/invite-html invite))

  (def invite (json/decode (slurp "./opt/samples/invites/microsoft.json")))
  (spit "./hiccup.html" (content/invite-html invite))
  (content/invite-text invite)

  (def invite (json/decode (slurp "./opt/samples/invites/combat.json")))
  (spit "./hiccup.html" (content/invite-html invite))
  (content/invite-text invite)

  (def invite (json/decode (slurp "./opt/samples/invites/sparse-data.json")))
  (spit "./hiccup.html" (content/invite-html invite))
  (content/invite-text invite)

  (spit "./hiccup.html" (content/token-html :reset {:token-link "http://test.it/123"}))
  
  (content/token-text :verify {:token-link "http://test.it/123"})

  )