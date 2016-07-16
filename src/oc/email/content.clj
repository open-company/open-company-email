(ns oc.email.content
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [hiccup.core :as h]
            [clojure.walk :refer (keywordize-keys)]
            [oc.email.config :as config]))

(def monthly-period (f/formatter "YYYY-MM"))

(def finance-date (f/formatter "MMM YYYY"))

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

(defn- topic-image [image-url]
  [:tr
    [:th
      [:img {:src image-url}]]])

(defn- content-topic [snapshot topic-name topic topic-url]
  (let [body? (not (s/blank? (:body topic)))
        image-url (:image-url topic)
        snippet? (not (s/blank? (:snippet topic)))]
    [:table {:class "row topic"}
      [:tbody
        (when image-url (topic-image image-url))
         [:tr
          [:th {:class "small-12 large-12 columns first last"}
            (spacer 24)
            [:p {:class "topic-title"} (s/upper-case (:title topic))]
            (spacer 1)
            [:p {:class "topic-headline"} (emojify (:headline topic))]
            (when snippet? (spacer 2))
            (when snippet? (emojify (:snippet topic)))
            (when body? (spacer 20))
            (when body? [:a {:class "topic-read-more" :href topic-url} "READ MORE"])
            (spacer 30)]
          [:th {:class "expander"}]]]]))

(defn- metric [label value]
  [:table {:class "metric"}
    [:tbody
      [:tr
        [:td
          [:p {:class "metric"} (.format (java.text.NumberFormat/getInstance java.util.Locale/US) (int value))]
          [:p {:class "label"} label]]]]])

(defn- finance-data [topic currency]
  (let [finances (last (sort-by :period (:data topic)))
        period (f/parse monthly-period (:period finances))
        date (s/upper-case (f/unparse finance-date period))
        cash? (:cash finances)
        revenue? (:revenue finances)
        costs? (and (not revenue?) (:costs finances))
        cash-flow? (and revenue? costs?)
        burn-rate? (and cash? (or costs? cash-flow?))]
    [:table {:class "finances-metrics"}
      [:tboody
        [:tr
          [:td
            (when cash? (metric (str "CASH - " date) (:cash finances)))
            (when revenue? (metric (str "REVENUE - " date) (:revenue finances)))
            (when costs? (metric (str "COSTS - " date) (:costs finances)))]]]]))
            ; (when (or cash-flow? burn-rate?)
            ;   [:p {:class "metric"} "$-211K"]
            ;   [:p {:class "label"} "CASH FLOW - OCT 2016"])
            ; (when (and cash? burn-rate?)
            ;   [:p {:class "metric"} "1 year"]
            ;   [:p {:class "label"} "RUNWAY - OCT 2016"])]]]]))

(defn- growth-data [topic currency]
  [:table {:class "growth-metrics"}
    [:tboody
      [:tr
        [:td
          [:p {:class "metric"} "2,841,519"]
          [:p {:class "label"} "Users - OCT 2016"]
          [:p {:class "metric"} "226,445"]
          [:p {:class "label"} "MAU - OCT 2016"]
          [:p {:class "metric"} "55,460"]
          [:p {:class "label"} "Avg DAU - OCT 2016"]
          [:p {:class "metric"} "$7,810,000"]
          [:p {:class "label"} "ARR - OCT 2016"]]]]])

(defn- data-topic [snapshot topic-name topic topic-url]
  (let [currency (:currency snapshot)
        data? (empty? (:data snapshot))
        snippet? (s/blank? (:snippet topic))]
    [:table {:class "row topic"}
      [:tbody
        [:tr
          [:th {:class "small-12 large-12 columns first last"}
            (spacer 24)
            [:p {:class "topic-title"} (s/upper-case (:title topic))]
            (spacer 1)
            [:p {:class "topic-headline"} (emojify (:headline topic))]
            (when data? (spacer 15))
            (when data?
              (if (= topic-name "finances")
                (finance-data topic currency)
                (growth-data topic currency)))
            (when snippet? (spacer 20))
            (when snippet? [:p (:snippet topic)])
            (spacer 20)
            [:a {:class "topic-read-more", :href topic-url} "SEE MORE"]
            (spacer 30)]
          [:th {:class "expander"}]]]]))

(defn- topic [snapshot topic-name topic]
  (let [company-slug (:company-slug snapshot)
        snapshot-slug (:slug snapshot)
        topic-url (s/join "/" [config/web-url company-slug "updates" snapshot-slug topic-name])]
    (if (:data topic)
      (data-topic snapshot topic-name topic topic-url)
      (content-topic snapshot topic-name topic topic-url))))

(defn- content [snapshot]
  (let [logo? (not (s/blank? (:logo snapshot)))]
    [:td
      (spacer 60 "header")
      (when logo? (logo snapshot))
      (when logo? (spacer 17 "header"))
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
                (repeat (spacer 25 "header"))))]]]
      (spacer 60 "header")]))

(defn- message [snapshot]
  [:table {:class "note"}
    [:tbody
      [:tr
        [:td
          (spacer 55 "note")
          (note snapshot)
          (spacer 16 "note")
          (attribution snapshot)
          (spacer 57 "note")]]]])

(defn- footer []
  [:table {:class "footer"}
    [:tbody
      [:tr
        [:td
          [:center
            [:table {:class "container"}
              [:tbody
                [:tr
                  [:td
                    (spacer 51 "footer")
                    [:table {:class "row footer"}
                      [:tbody
                        [:tr
                          [:th {:class "small-2 large-2 first columns"}]
                          [:th {:class "small-8 large-8 columns"} 
                            [:p {:class "text-center"}
                              "Powered by "
                              [:a {:href "http://opencompany.com/"} "OpenCompany"]
                              ", a simple way to share the big picture."]]
                          [:th {:class "small-2 large-2 last columns"}]]]]
                    (spacer 24 "footer")
                    [:table {:class "row footer"}
                      [:tbody
                        [:tr
                          [:th {:class "small-2 large-2 first columns"}]
                          [:th {:class "small-8 large-8 columns"}
                            [:table {:class "button expanded learn"}
                              [:tbody
                                [:tr
                                  [:td
                                    [:table
                                      [:tbody
                                        [:tr
                                          [:td
                                            [:a {:href "https://opencompany.com/"} "LEARN MORE ➞"]]]]]]]]]]
                          [:th {:class "small-2 large-2 last columns"}]]]]
                    (spacer 22 "footer")]]]]]]]]])

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
                    (content snapshot)]]]]
            (footer)]]]]])

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

  (def data (clean-html (slurp "./resources/data-topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/footer.html")))
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