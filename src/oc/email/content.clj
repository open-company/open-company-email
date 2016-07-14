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

(defn- logo [snapshot]
  [:table {:class "row header"} 
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns"} 
          [:table
            [:tbody
              [:tr
                [:th
                  [:center {:data-parsed ""}
                    [:img {:class "float-center logo"
                           :align "center"
                           :style "background-color: #ffffff;border: solid 1px rgba(78, 90, 107, 0.2);"
                           :height "50px"
                           :width "50px"
                           :src (:logo snapshot)
                           :alt (str (:name snapshot) " logo")}]]]]]]]]]])

(defn- company-name [snapshot]
  [:table {:class "row header"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns"}
          [:p {:class "text-center company-name"} (:name snapshot)]]]]])

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
        [:th {:class "small-12 large-12 first last columns note"}
          (emojify (:note snapshot))]]]])

(defn- attribution [snapshot]
  (let [author (get-in snapshot [:author :name])]
    [:table {:class "row note"}
      [:tbody
        [:tr
          [:th {:class "small-12 large-12 first last columns note"}
            [:p {:class "note"} (str "— " author)]]]]]))

(defn- spacer
  ([pixels] (spacer pixels ""))
  ([pixels class-name]
  [:table {:class (str "row " class-name)}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns"}
          [:table
            [:tbody
              [:tr
                [:th
                  [:table {:class "spacer"}
                    [:tbody
                      [:tr
                        [:td {:height (str "font-size:" pixels "px")
                              :style (str "font-size:" pixels "px;line-height:" pixels "px;")} " "]]]]]
                [:th {:class "expander"}]]]]]]]]))

(defn- image [image-url]
  [:tr
    [:th
      [:img {:src image-url}]]])

(defn- topic [snapshot topic-name topic]
  (let [body? (not (s/blank? (:body topic)))
        company-slug (:company-slug snapshot)
        snapshot-slug (:slug snapshot)
        image-url (:image topic)
        topic-url (s/join "/" [config/web-url company-slug "updates" snapshot-slug topic-name])]
    (if (:data topic)
      [:span]
      [:table {:class "row topic"}
        [:tbody
          (when image-url (image image-url))
           [:tr
            [:th {:class "small-12 large-12 columns first last"}
              (spacer 24)
              [:p {:class "topic-title"} (s/upper-case (:title topic))]
              (spacer 1)
              [:p {:class "topic-headline"} (emojify (:headline topic))]
              (spacer 2)
              (emojify (:body topic))
              (when body? (spacer 20))
              (when body? [:a {:class "topic-read-more" :href topic-url} "READ MORE"])
              (spacer 30)]
            [:th {:class "expander"}]]]])))

(defn- content [snapshot]
  [:td
    (spacer 60 "header")
    (logo snapshot)
    (spacer 17 "header")
    (company-name snapshot)
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
  [:table {:class "message"}
    [:tbody
      [:tr
        [:td
          (spacer 55 "note")
          (note snapshot)
          (spacer 16 "note")
          (attribution snapshot)
          (spacer 57 "note")]]]])

(defn- body [snapshot]
  [:body
    [:table {:class "body"}
      [:tbody
        [:tr
          [:td {:class "float-center", :align "center", :valign "top"}
            (when-not (s/blank? (:note snapshot)) (message snapshot))
            [:center
              [:table {:class "container"}
                [:tbody
                  [:tr
                    (content snapshot)]]]]]]]]])

(defn- head [snapshot]
  [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
    [:head 
      [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
      [:meta {:name "viewport", :content "width=device-width"}]
      [:title (str (:name snapshot) " Update")]
      [:link {:rel "stylesheet", :href "resources/css/foundation.css"}]
      [:link {:rel "stylesheet", :href "resources/css/opencompany.css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Domine", :rel "stylesheet", :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Open+Sans", :rel "stylesheet", :type "text/css"}]]
    (body snapshot)])

(defn html [snapshot]
  (str
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
    (h/html (head (keywordize-keys snapshot)))))

(comment
  
  ;; For REPL testing

  (require '[oc.email.content :as content] :reload)

  ;; Recreate hiccup from various HTML fragments

  (defn clean-html [data]
    (-> data
      (s/replace "  " "")
      (s/replace "\n" "")
      (s/replace "\t" "")))

  (def data (clean-html (slurp "./resources/head.html")))
  (-> (hickory/parse data) hickory/as-hiccup first)

  (def data (clean-html (slurp "./resources/body.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3))

  (def data (clean-html (slurp "./resources/spacer.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/note.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/attribution.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-htnml (slurp "./resources/logo.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/name.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/title.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (spit "./hiccup.html" (email/html {}))

  ;; Generate test email HTML content from various snapshots

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def snapshot (json/decode (slurp "./opt/samples/buffer.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "buffer"))))

  (def note "All there is to know about OpenCompany.")
  (def snapshot (json/decode (slurp "./opt/samples/open.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "open"))))

  (def note "")
  (def snapshot (json/decode (slurp "./opt/samples/open.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "buffer"))))

  )