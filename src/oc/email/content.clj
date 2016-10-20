(ns oc.email.content
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [hiccup.core :as h]
            [clojure.walk :refer (keywordize-keys)]
            [oc.email.config :as config]
            [oc.lib.data.utils :as utils]))

(def month-formatter (f/formatter "MMM"))

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

(def tagline "OpenCompany is the simplest way to keep everyone on the same page.")

(defn- logo [logo company-name]
  [:table {:class "row header"} 
    [:tr
      [:th {:class "small-12 large-12 first last columns"} 
        [:table
          [:tr
            [:th
              [:center {:data-parsed ""}
                [:img {:class "float-center logo"
                       :align "center"
                       :style "background-color: #ffffff;border: solid 1px rgba(78, 90, 107, 0.2);max-height: 50px;max-width: 50px;"
                       :src logo
                       :alt (str company-name " logo")}]]]]]]]])

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
      [:center
        [:img {:src image-url}]]]])

(defn- content-topic [snapshot topic-name topic topic-url]
  (let [title (:title topic)
        title? (not (s/blank? title))
        headline (:headline topic)
        headline? (not (s/blank? headline))
        body (:body topic)
        body? (not (s/blank? body))
        image-url (:image-url topic)
        image-url? (not (s/blank? image-url))]
    (when (or image-url? title? headline? body?)
      [:table {:class "row topic"}
        (when image-url?
          (topic-image image-url))
        (when (or title? headline? body?)
          [:tr
            [:th {:class "small-12 large-12 columns first last"}
              (spacer 24)
              (when title?
                [:p {:class "topic-title"} (s/upper-case title)])
              (when title? (spacer 1))
              (when headline?
                [:p {:class "topic-headline"} headline])
              (when body? (spacer 2))
              (when body? body)
              (spacer 20)]
            [:th {:class "expander"}]])])))

(defn- metric
  ([label value] (metric label value :nuetral))
  ([label value css-class]
  [:table {:class "metric"}
    [:tr
      [:td
        [:p [:span {:class (str "metric " (name css-class))} value]
            [:span {:class "label"} label]]]]]))

(defn- format-delta
  "Create a display fragment for a delta value."
  
  ([delta prior-date]
  (let [pos (when (pos? delta) "+")]
    (str "(" pos (if (zero? delta) "no change" (utils/with-size-label delta)) "% since " prior-date ") ")))
  
  ([currency delta prior-date]
  (str "(" (if (zero? delta) "no change" (utils/with-currency currency (utils/with-size-label delta) true))
    " since " prior-date ") ")))

(defn- growth-metric [periods metadata currency]
  (let [growth-metric (first periods)
        slug (:slug growth-metric)
        metadatum (first (filter #(= (:slug %) slug) metadata))
        unit (:unit metadatum)
        interval (:interval metadatum)
        unit (:unit metadatum)
        metric-name (:name metadatum)
        period (when interval (utils/parse-period interval (:period growth-metric)))
        date (when (and interval period) (utils/format-period interval period))
        value (:value growth-metric)
        ;; Check for older periods contiguous to most recent
        contiguous-periods (when (seq periods) (utils/contiguous (map :period periods) (keyword interval)))
        prior-contiguous? (>= (count contiguous-periods) 2)
        ;; Info on prior period
        prior-metric (when prior-contiguous?
                        (first (filter #(= (:period %) (second contiguous-periods)) periods)))
        prior-period (when (and interval prior-metric) (utils/parse-period interval (:period prior-metric)))
        prior-date (when (and interval prior-period) (utils/format-period interval prior-period))
        formatted-prior-date (when prior-date (s/join " " (butlast (s/split prior-date #" ")))) ; drop the year
        prior-value (when prior-metric (:value prior-metric))
        metric-delta (when (and value prior-value) (- value prior-value))
        metric-delta-percent (when metric-delta (* 100 (float (/ metric-delta prior-value))))
        formatted-metric-delta (when metric-delta-percent (format-delta metric-delta-percent formatted-prior-date))
        ;; Format output
        label (str metric-name " " formatted-metric-delta "- " date)
        format-symbol (case unit "%" "%" "currency" currency nil)]
    (when (and interval (number? value))
      (metric label (utils/with-format format-symbol value)))))

(defn- periods-for-metric
  "Given the specified metric, return a sequence of all the periods in the data for that metric, sorted by most recent."
  [metric data]
  (when-let [periods (vec (filter #(= (:slug %) (:slug metric)) data))]
    (reverse (sort-by :period periods))))

(defn growth-metrics [topic currency]
  (let [data (:data topic)
        metadata (:metrics topic)
        periods (map #(periods-for-metric % data) metadata)]
    [:table {:class "growth-metrics"}
      [:tr
        (into [:td]
          (map #(growth-metric % metadata currency) periods))]]))

(defn- finance-metrics [topic currency]
  (let [sorted-finances (reverse (sort-by :period (:data topic)))
        ;; Most recent finances
        finances (first sorted-finances)
        period (f/parse utils/monthly-period (:period finances))
        date (s/upper-case (f/unparse utils/monthly-date period))
        ;; Check for older periods contiguous to most recent
        contiguous-periods (utils/contiguous (map :period sorted-finances))
        prior-contiguous? (>= (count contiguous-periods) 2)
        ;; Info on prior period
        prior-finances (when prior-contiguous?
                        (first (filter #(= (:period %) (second contiguous-periods)) sorted-finances)))
        prior-period (when prior-finances (f/parse utils/monthly-period (:period prior-finances)))
        prior-date (when prior-period (s/upper-case (f/unparse month-formatter prior-period)))
        ;; Info on cash
        cash (:cash finances)
        cash? (utils/not-zero? cash)
        formatted-cash (when cash? (utils/with-currency currency (utils/with-size-label cash)))
        prior-cash (when prior-finances (:cash prior-finances))
        cash-delta (when (and cash? prior-cash) (- cash prior-cash))
        formatted-cash-delta (when cash-delta (format-delta currency cash-delta prior-date))
        ;; Info on revenue
        revenue (:revenue finances)
        revenue? (utils/not-zero? revenue)
        formatted-revenue (when revenue? (utils/with-currency currency (utils/with-size-label revenue)))
        prior-revenue (when prior-finances (:revenue prior-finances))
        revenue-delta (when (and revenue? prior-revenue) (- revenue prior-revenue))
        revenue-delta-percent (when revenue-delta (* 100 (float (/ revenue-delta prior-revenue))))
        formatted-revenue-delta (when revenue-delta-percent (format-delta revenue-delta-percent prior-date))
        ;; Info on costs/expenses
        costs (:costs finances)
        costs? (utils/not-zero? costs)
        formatted-costs (when costs? (utils/with-currency currency (utils/with-size-label costs)))
        prior-costs (when prior-finances (:costs prior-finances))
        costs-delta (when (and costs? prior-costs) (- costs prior-costs))
        costs-delta-percent (when costs-delta (* 100 (float (/ costs-delta prior-costs))))
        formatted-costs-delta (when costs-delta-percent (format-delta costs-delta-percent prior-date))        
        ;; Info on runway (calculated) 
        cash-flow (- (or revenue 0) (or costs 0))
        runway? (and cash? costs? (or (not revenue?) (> costs revenue)))
        runway (when runway? (utils/calc-runway cash cash-flow))]
    
    [:table {:class "finances-metrics"}
      [:tr
        (let [cost-label (if revenue? "Expenses" "Burn")]
          [:td
            (when revenue? (metric (str "Revenue " formatted-revenue-delta "- " date)
                                   formatted-revenue
                                   :pos))
            (when (and cash? (not revenue?)) (metric (str "Cash " formatted-cash-delta "- " date)
                                            formatted-cash
                                            :neutral))
            (when costs? (metric (str cost-label " " formatted-costs-delta "- " date)
                         formatted-costs
                         :neg))
            (when (and cash? revenue?) (metric (str "Cash " formatted-cash-delta "- " date)
                                               formatted-cash
                                               :nuetral))
            (when runway? (metric (str "Runway - " date)
                                  (utils/get-rounded-runway runway)))])]]))

(defn- data-topic [snapshot topic-name topic topic-url]
  (let [currency (:currency snapshot)
        data? (seq (:data topic))
        body? (not (s/blank? (:body topic)))
        view-charts? (> (count (:data topic)) 1)]
    [:table {:class "row topic"}
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          (when-not (s/blank? (:title topic))
            (spacer 24))
          (when-not (s/blank? (:title topic))
            [:p {:class "topic-title"} (s/upper-case (:title topic))])
          (spacer 1)
          [:p {:class "topic-headline"} (:headline topic)]
          (when body? (spacer 2))
          (when body? (:body topic))
          (when data? (spacer 20))
          (when data?
            (if (= topic-name "finances")
              (finance-metrics topic currency)
              (growth-metrics topic currency)))
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

(defn- paragraph [content]
  [:table {:class "row"}
    [:tbody
      [:tr
        [:th {:class "small-1 large-2 first columns"}]
        [:th {:class "small-10 large-8 columns"}
          [:p {:class "text-center"} content]]
        [:th {:class "small-1 large-2 last columns"}]
        [:th {:class "expander"}]]]])

(defn- snapshot-content [snapshot]
  (let [title? (not (s/blank? (:title snapshot)))]
    [:td
      (spacer 30 "header")
      (when title? (title snapshot))
      (when title? (spacer 22 "header"))
      [:table
        [:tr
          (into [:td] 
            (interleave
              (map #(topic snapshot % (snapshot (keyword %))) (:sections snapshot))
              (repeat (spacer 15 "header"))))]]]))

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
  (let [logo-url (:logo invite)
        logo? (not (s/blank? logo-url))
        company-name (:company-name invite)]
    [:td
      (when logo? (spacer 20))
      (when logo? (logo logo-url company-name))
      (spacer 15)
      (paragraph (str "Hi there! " (:subject invite)))
      (spacer 15)
      (cta-button "OK! LET'S GET STARTED ➞" (:token-link invite))
      (spacer 30)
      (paragraph tagline)]))

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
                        [:a {:href "https://opencompany.com/"} "OpenCompany"]]]
                    [:th {:class "small-1 large-2 last columns"}]]]
                (spacer 28 "footer")]]]]]]])

(defn- body [data]
  (let [type (:type data)]
    [:body
      [:table {:class "body"}
        [:tr
          [:td {:class "float-center", :align "center", :valign "top"}
            (when-not (s/blank? (:note data)) (note data))
            [:center
              [:table {:class "container"}
                [:tr
                  (case type
                    :snapshot (snapshot-content data)
                    :invite (invite-content data))]]]
            (when (= type :snapshot) (footer))]]]]))

(defn- head [data]
  (let [type (:type data)
        title (if (= type :snapshot) (str (:name data) " Update") (str (:company-name data) " Invite"))
        css (if (= type :snapshot) "oc-snapshot.css" "oc-invite.css")]
    [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
      [:head 
        [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
        [:meta {:name "viewport", :content "width=device-width"}]
        [:title title]
        [:link {:rel "stylesheet", :href "resources/css/foundation.css"}]
        [:link {:rel "stylesheet", :href (str "resources/css/" css)}]
        [:link {:href "http://fonts.googleapis.com/css?family=Domine", :rel "stylesheet", :type "text/css"}]
        [:link {:href "http://fonts.googleapis.com/css?family=Open+Sans", :rel "stylesheet", :type "text/css"}]]
      (body data)]))

(defn- html [data type]
  (str doc-type (h/html (head (assoc (keywordize-keys data) :type type)))))

(defn invite-subject [invite]
  (let [msg (keywordize-keys invite)
        company-name (:company-name msg)
        from (:from msg)
        prefix (if (s/blank? from) "You've been invited" (str from " invited you"))
        company (if (s/blank? company-name) "" (str company-name " on "))]
    (str prefix " to join " company "OpenCompany.")))

(defn snapshot-html [snapshot]
  (html snapshot :snapshot))

(defn invite-html [invite]
  (html (assoc invite :subject (invite-subject invite)) :invite))

(defn invite-text [invite]
  (let [link (:token-link (keywordize-keys invite))]
    (str (invite-subject invite) ".\n\n"
         tagline "\n\n"
         "Open the link below to check it out.\n\n"
         link "\n\n")))

(comment
  
  ;; For REPL testing and content development

  (require '[oc.email.content :as content] :reload)

  ;; Recreate hiccup from various HTML fragments

  (defn clean-html [data]
    (-> data
      (s/replace "  " "")
      (s/replace "\n" "")
      (s/replace "\t" "")))

  (def data (clean-html (slurp "./resources/snapshot/head.html")))
  (-> (hickory/parse data) hickory/as-hiccup first)

  (def data (clean-html (slurp "./resources/snapshot/body.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3))

  (def data (clean-html (slurp "./resources/snapshot/spacer.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/snapshot/note.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-htnml (slurp "./resources/snapshot/logo.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/snapshot/name.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/snapshot/title.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/snapshot/topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/snapshot/data-topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/snapshot/footer.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/invite/paragraph.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/invite/cta-button.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (spit "./hiccup.html" (email/html {}))

  ;; Generate test email HTML content from various snapshots

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def snapshot (json/decode (slurp "./opt/samples/snapshots/green-labs.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note note) (assoc :company-slug "green-labs"))))

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def snapshot (json/decode (slurp "./opt/samples/snapshots/buff.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note note) (assoc :company-slug "buff"))))

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/new.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note "") (assoc :company-slug "new"))))

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/bago.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note "") (assoc :company-slug "bago"))))

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/bago-no-symbol.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note "") (assoc :company-slug "bago"))))

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/growth-options.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note "") (assoc :company-slug "growth-options"))))

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/blanks-test.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note "") (assoc :company-slug "blanks-test"))))

  (def snapshot (json/decode (slurp "./opt/samples/snapshots/sparse.json")))
  (spit "./hiccup.html" (content/snapshot-html (-> snapshot (assoc :note "") (assoc :company-slug "sparse"))))

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

  )