(ns oc.email.content
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [hiccup.core :as h]
            [clojure.walk :refer (keywordize-keys)]
            [oc.email.config :as config]
            [oc.lib.utils :as utils]
            [oc.lib.iso4217 :as iso4217]))

(def quarterly-period (f/formatter "YYYY-MM"))
(def monthly-period (f/formatter "YYYY-MM"))
(def weekly-period (f/formatter "YYYY-MM-DD"))

(def quarterly-date (f/formatter "MMM YYYY"))
(def monthly-date (f/formatter "MMM YYYY"))
(def weekly-date (f/formatter "D MMM YYYY"))

(defn- logo [snapshot]
  [:table {:class "row header"} 
    [:tr
      [:th {:class "small-12 large-12 first last columns"} 
        [:table
          [:tr
            [:th
              [:center {:data-parsed ""}
                [:img {:class "float-center logo"
                       :align "center"
                       :style "background-color: #ffffff;border: solid 1px rgba(78, 90, 107, 0.2);"
                       :height "50px"
                       :width "50px"
                       :src (:logo snapshot)
                       :alt (str (:name snapshot) " logo")}]]]]]]]])

(defn- company-name [snapshot]
  [:table {:class "row header"}
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:p {:class "text-center company-name"} (:name snapshot)]]]])

(defn- title [snapshot]
  [:table {:class "row header"}
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:p {:class "text-center title"} (:title snapshot)]]]])

(defn- message [snapshot]
  [:table {:class "row note"}
    [:tr
      [:th {:class "small-12 large-12 first last columns note"}
        (:note snapshot)]]])

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

(defn- topic-image [image-url]
  [:tr
    [:th
      [:img {:src image-url}]]])

(defn- content-topic [snapshot topic-name topic topic-url]
  (let [body? (not (s/blank? (:body topic)))
        image-url (:image-url topic)]
    [:table {:class "row topic"}
      (when image-url (topic-image image-url))
       [:tr
        [:th {:class "small-12 large-12 columns first last"}
          (spacer 24)
          [:p {:class "topic-title"} (s/upper-case (:title topic))]
          (spacer 1)
          [:p {:class "topic-headline"} (:headline topic)]
          (when body? (spacer 2))
          (when body? (:body topic))
          (spacer 20)]
        [:th {:class "expander"}]]]))

(defn- with-currency
  "Combine the value with the currency indicator, if available."
  [currency value]
  (let [currency-key (keyword currency)
        currency-entry (iso4217/iso4217 currency-key)
        currency-symbol (if currency-entry (:symbol currency-entry) false)
        currency-text (if currency-entry (:text currency-entry) false)]
    (if currency-symbol 
      (str currency-symbol value)
      (if currency-text
        (str value " " currency-text)
        (str value)))))

(defn- with-format [format-symbol value]
  (cond
    (= format-symbol "%") (str value "%")
    (not (nil? format-symbol)) (with-currency format-symbol value)
    :else value))

(defn- metric
  ([label value] (metric label value false))
  ([label value format-symbol]
  (let [final-value (with-format format-symbol value)]
    [:table {:class "metric"}
      [:tr
        [:td
          [:p {:class "metric"} final-value]
          [:p {:class "label"} label]]]])))

(defn- parse-period [interval value]
  (case interval
    "quarterly" (f/parse quarterly-period value)
    "weekly" (f/parse weekly-period value)
    (f/parse monthly-period value)))

(defn- format-period [interval period]
  (s/upper-case 
    (case interval
      "quarterly" (f/unparse quarterly-date period)
      "weekly" (f/unparse weekly-date period)
      (f/unparse monthly-date period))))

(defn- growth-metric [growth-metric metadata currency]
  (let [slug (:slug growth-metric)
        metadatum (first (filter #(= (:slug %) slug) metadata))
        unit (:unit metadatum)
        interval (:interval metadatum)
        unit (:unit metadatum)
        metric-name (:name metadatum)
        period (when interval (parse-period interval (:period growth-metric)))
        date (when (and interval period) (format-period interval period))
        label (str metric-name " - " date)
        value (:value growth-metric)
        format-symbol (case unit "%" "%" "currency" currency nil)]
    (when (and interval (number? value))
      (metric label value format-symbol))))

(defn- latest-period-for-metric
  "Given the specified metric, return a sequence of all the periods in the data for that metric."
  [metric data]
  (when-let [periods (vec (filter #(= (:slug %) (:slug metric)) data))]
    (last (sort-by :period periods))))

(defn growth-metrics [topic currency]
  (let [data (:data topic)
        metadata (:metrics topic)
        latest-period (map #(latest-period-for-metric % data) metadata)]
    [:table {:class "growth-metrics"}
      [:tr
        (into [:td]
          (map #(growth-metric % metadata currency) latest-period))]]))

(defn- finance-metrics [topic currency]
  (let [finances (last (sort-by :period (:data topic)))
        period (f/parse monthly-period (:period finances))
        date (s/upper-case (f/unparse monthly-date period))
        cash (:cash finances)
        cash? (utils/not-zero? cash)
        revenue (:revenue finances)
        revenue? (utils/not-zero? revenue)
        costs (:costs finances)
        costs? (utils/not-zero? costs)
        cash-flow? (and revenue? costs?)
        cash-flow (- revenue costs)
        runway? (and cash? costs? (or (not revenue?) (> costs revenue)))
        runway (when runway? (utils/calc-runway cash cash-flow))]
    [:table {:class "finances-metrics"}
      [:tr
        [:td
          (when cash? (metric (str "CASH - " date) (utils/with-size-label cash) currency))
          (when revenue? (metric (str "REVENUE - " date) (utils/with-size-label revenue) currency))
          (when costs? (metric (str "COSTS - " date) (utils/with-size-label costs) currency))
          (when cash-flow? (metric (str "CASH FLOW - " date) (utils/with-size-label cash-flow) currency))
          (when runway? (metric (str "RUNWAY - " date) (utils/get-rounded-runway runway)))]]]))

(defn- data-topic [snapshot topic-name topic topic-url]
  (let [currency (:currency snapshot)
        data? (seq (:data topic))
        body? (s/blank? (:body topic))
        view-charts? (> (count (:data topic)) 1)]
    [:table {:class "row topic"}
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          (spacer 24)
          [:p {:class "topic-title"} (s/upper-case (:title topic))]
          (spacer 1)
          [:p {:class "topic-headline"} (:headline topic)]
          (when data? (spacer 15))
          (when data?
            (if (= topic-name "finances")
              (finance-metrics topic currency)
              (growth-metrics topic currency)))
          (when body? (spacer 20))
          (when body? [:p (:body topic)])
          (when view-charts? (spacer 20))
          (when view-charts? [:a {:class "topic-read-more", :href topic-url} "VIEW CHARTS"])
          (spacer 30)]
        [:th {:class "expander"}]]]))

(defn- topic [snapshot topic-name topic]
  (let [company-slug (:company-slug snapshot)
        snapshot-slug (:slug snapshot)
        topic-url (s/join "/" [config/web-url company-slug "updates" snapshot-slug (str topic-name "?src=email")])]
    (if (:data topic)
      (data-topic snapshot topic-name topic topic-url)
      (content-topic snapshot topic-name topic topic-url))))

(defn- content [snapshot]
  (let [logo? (not (s/blank? (:logo snapshot)))
        title? (not (s/blank? (:title snapshot)))]
    [:td
      (spacer 30 "header")
      (company-name snapshot)
      (when title? (spacer 10 "header"))
      (when title? (title snapshot))
      (spacer 22 "header")
      [:table
        [:tr
          (into [:td] 
            (interleave
              (map #(topic snapshot % (snapshot (keyword %))) (:sections snapshot))
              (repeat (spacer 15 "header"))))]]]))

(defn- note [snapshot]
  [:table {:class "note"}
    [:tr
      [:td
        (spacer 20 "note")
        (message snapshot)
        (spacer 22 "note")]]])

(defn- footer []
  [:table {:class "footer"}
    [:tr
      [:td
        [:center
          [:table {:class "container"}
            [:tr
              [:td
                (spacer 31 "footer")
                [:table {:class "row footer"}
                  [:tr
                    [:th {:class "small-1 large-2 first columns"}]
                    [:th {:class "small-11 large-8 columns"} 
                      [:p {:class "text-center"}
                        "Powered by "
                        [:a {:href "http://opencompany.com/"} "OpenCompany"]]]
                    [:th {:class "small-1 large-2 last columns"}]]]
                (spacer 28 "footer")]]]]]]])

(defn- body [snapshot]
  [:body
    [:table {:class "body"}
      [:tr
        [:td {:class "float-center", :align "center", :valign "top"}
          (when-not (s/blank? (:note snapshot)) (note snapshot))
          [:center
            [:table {:class "container"}
              [:tr
                (content snapshot)]]]
          (footer)]]]])

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
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
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
  (def snapshot (json/decode (slurp "./opt/samples/green-labs.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "green-labs"))))

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def snapshot (json/decode (slurp "./opt/samples/buff.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "buff"))))

  (def snapshot (json/decode (slurp "./opt/samples/new.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note "") (assoc :company-slug "new"))))

  (def snapshot (json/decode (slurp "./opt/samples/bago.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note "") (assoc :company-slug "bago"))))

  (def snapshot (json/decode (slurp "./opt/samples/bago-no-symbol.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note "") (assoc :company-slug "bago"))))

  (def snapshot (json/decode (slurp "./opt/samples/growth-options.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note "") (assoc :company-slug "growth-options"))))

  )