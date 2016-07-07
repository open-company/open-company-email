(ns oc.email.content
  (:require [clojure.string :as s]
            [hiccup.core :as h]
            [clojure.walk :refer (keywordize-keys)]
            [oc.email.config :as config]))

(defn- escape-accidental-emoticon [text]
  "Replace any :/ with to avoid emojifying links and images."
  (s/replace text #":\/\/" ":this-is-not-an-emoji-silly-library:"))

(defn- unescape-accidental-emoticon [text]
  "Replace any  with :/ to restore links and images."
  (s/replace text #":this-is-not-an-emoji-silly-library:" "://"))

(defn- emojify [text]
  "Replace emoji shortcodes or ASCII with Unicode."
  (-> text
    (escape-accidental-emoticon)
    (emoji4j.EmojiUtils/emojify)
    (unescape-accidental-emoticon)))

(defn- logo-name [snapshot]
  [:table {:class "row header"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns"}
          [:center {:data-parsed ""}
              [:img {:class "logo float-center"
                     :alt (str (:name snapshot) " logo")
                     :style "display: inline; background-color: #ffffff;border: solid 1px rgba(78, 90, 107, 0.2);"
                     :height "50"
                     :width "50"
                     :align "center"
                     :src (:logo snapshot)}]
              [:h4 {:class "name float-center" :style "padding-left: 18px; display: inline;"} (:name snapshot)]]]]]])

(defn- title [snapshot]
  [:table {:class "row header"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns"}
          [:p {:class "text-center title"} (emojify (:title snapshot))]]]]])

(defn- note [snapshot]
  [:table {:class "row note"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns"}
          (emojify (:note snapshot))]]]])

(defn- attribution [snapshot]
  (let [author (get-in snapshot [:author :name])]
    [:table {:class "row note"}
      [:tbody
        [:tr
          [:th {:class "small-12 large-12 first last columns"}
            [:p (str "— " author)]]]]]))

(defn- spacer
  ([pixels] (spacer pixels ""))
  ([pixels class]
  [:table {:class (str "row " class)}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          [:table
            [:tbody
              [:tr
                [:th
                  [:table {:class "spacer"}
                    [:tbody
                      [:tr
                        [:td {:height (str pixels "px")
                              :style (str "font-size:" pixels "px;line-height:" pixels "px;")} " "]]]]]
                [:th {:class "expander"}]]]]]]]]))

(defn- topic [snapshot topic-name topic]
  (let [body? (not (s/blank? (:body topic)))
        company-slug (:company-slug snapshot)
        snapshot-slug (:slug snapshot)
        topic-url (s/join "/" [config/web-url company-slug "updates" snapshot-slug topic-name])]
    (if (:data topic)
      [:table]
      [:table {:class "row topic"}
        [:tbody
          [:tr
            [:th {:class "small-12 large-12 columns first last"}
              (spacer 24)
              [:p {:class "topic-title"} (s/upper-case (:title topic))]
              (spacer 1)
              [:p {:class "topic-headline"} (emojify (:headline topic))]
              (spacer 2)]]
          [:tr
            [:th {:class "small-12 large-12 columns first last topic"}
              (emojify (:body topic))
              (when body? (spacer 20))]]
          [:tr
            [:th {:class "small-12 large-12 columns first last"}
              (when body? [:a {:class "topic-read-more", :href topic-url} "READ MORE"])
              (spacer 30)
              [:th {:class "expander"}]]]]])))

(defn- content [snapshot]
  [:td
    (spacer 60 "header")
    (logo-name snapshot)
    (spacer 17 "header")
    (title snapshot)
    (spacer 42 "header")
    [:table
      [:tbody
        [:tr
          (into [:td] 
            (interleave
              (map #(topic snapshot % (snapshot (keyword %))) (:sections snapshot))
              (repeat (spacer 25 "header"))))]]]])

(defn- message [snapshot]
  [:table
    [:tbody
      [:tr
        (spacer 55 "note")
        (note snapshot)
        (spacer 16 "note")
        (attribution snapshot)
        (spacer 57 "note")]]])

(defn- body [snapshot]
  [:body
    [:table {:class "body"}
      [:tbody
        [:tr
          [:td {:class "center", :align "center", :valign "top"}
            [:center
              (when-not (s/blank? (:note snapshot)) (message snapshot))
              [:table {:class "container"}
                [:tbody
                  [:tr
                    (content snapshot)]]]]]]]]])

(defn- head [snapshot]
  [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
    [:head 
      [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
      [:meta {:name "viewport", :content "width=device-width"}]
      [:link {:rel "stylesheet", :href "resources/css/foundation.css"}] ; Regular use
      [:link {:rel "stylesheet", :href "resources/css/opencompany.css"}] ; Regular use
      ;;[:link {:rel "stylesheet", :href "css/foundation.css"}] ; REPL testing
      ;;[:link {:rel "stylesheet", :href "css/opencompany.css"}] ; REPL testing
      [:link {:href "http://fonts.googleapis.com/css?family=Domine", :rel "stylesheet", :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Open+Sans", :rel "stylesheet", :type "text/css"}]
      (body snapshot)]])

(defn html [snapshot]
  (str
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
    (h/html (head (keywordize-keys snapshot)))))

(comment
  
  ;; For REPL testing

  (require '[oc.email.content :as content] :reload)

  ;; Recreate hiccup from various HTML fragments

  (def data (slurp "./resources/head.html"))
  (-> (hickory/parse data) hickory/as-hiccup first)

  (def data (slurp "./resources/body.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3))

  (def data (slurp "./resources/spacer.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/logo-name.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/title.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/message.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/attribution.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/topic.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (spit "./resources/hiccup.html" (email/html {}))

  ;; Generate test email HTML content from various snapshots

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def snapshot (json/decode (slurp "./resources/snapshots/buffer.json")))
  (spit "./resources/hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "buffer"))))

  (def note "All there is to know about OpenCompany.")
  (def snapshot (json/decode (slurp "./resources/snapshots/open.json")))
  (spit "./resources/hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "open"))))

  (def note "")
  (def snapshot (json/decode (slurp "./resources/snapshots/open.json")))
  (spit "./resources/hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "buffer"))))

  )