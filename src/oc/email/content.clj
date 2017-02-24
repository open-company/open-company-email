(ns oc.email.content
  (:require [clojure.string :as s]
            [clj-time.format :as format]
            [hiccup.core :as h]
            [clojure.walk :refer (keywordize-keys)]
            [oc.email.config :as config]
            [oc.email.lib.sparkline :as sl]
            [oc.lib.data.utils :as utils]))

(def month-formatter (format/formatter "MMM"))

(def iso-format (format/formatters :date-time)) ; ISO 8601
(def link-format (format/formatter "YYYY-MM-dd")) ; Format for date in URL of stakeholder-update links

(def doc-type "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")

(def tagline "OpenCompany is the simplest way to keep everyone on the same page.")

(def reset-message "Someone (hopefully you) requested a new password for your OpenCompany account.")
(def reset-instructions "If you didn't request a password reset, you can ignore this email.")

(defn- logo [logo-url org-name]
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
                       :src logo-url
                       :alt (str org-name " logo")}]]]]]]]])

(defn- oc-logo []
  [:table {:class "row header"} 
    [:tr
      [:th {:class "small-12 large-12 first last columns"} 
        [:table
          [:tr
            [:th
              [:center {:data-parsed ""}
                [:img {:class "float-center logo"
                       :align "center"
                       :style "background-color: #ffffff;max-height: 71px;max-width: 71px;"
                       :src "https://open-company-assets.s3.amazonaws.com/open-company.png"
                       :alt "OpenCompany logo"}]]]]]]]])

(defn- org-name [update]
  [:table {:class "row header"}
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:p {:class "text-center org-name"} (:org-name update)]]]])

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

(defn- content-entry [update topic-slug entry last-entry?]
  (let [title (:title entry)
        title? (not (s/blank? title))
        headline (:headline entry)
        headline? (not (s/blank? headline))
        body (:body entry)
        body? (not (s/blank? body))
        image-url (:image-url entry)
        image-url? (not (s/blank? image-url))
        entry-class (str "row entry" (when last-entry? " last"))]
    (when (or image-url? title? headline? body?)
      [:table {:class entry-class}
        [:tr
          [:th {:class "small-12 large-12 columns first last"}
            (spacer 18)
            (when title?
              [:p {:class "entry-title"} (s/upper-case title)])
            (when headline?
              [:p {:class "entry-headline"} headline])
            (when image-url?
              [:img {:class "entry-image" :src image-url}])
            (when body? body)
            (when body? (spacer 10))]
          [:th {:class "expander"}]]])))

(defn- metric
  ([value label sub-label] (metric value label sub-label :nuetral))
  ([value label sub-label css-class]
  [:table {:class "metric"}
    [:tr
      [:td
        [:span {:class (str "metric " (name css-class))} value]
        [:span {:class "label"} label]]]
    [:tr
      [:td
         [:p [:span {:class "sublabel"} sub-label]]]]]))

(defn- format-delta
  "Create a display fragment for a delta value."
  
  ([delta prior-date]
  (let [pos (when (pos? delta) "+")]
    [:span
      (if (zero? delta) "no change" [:span {:class "metric-diff"} pos (utils/with-size-label delta) "%"])
      (str " since " prior-date)]))
  
  ([currency delta prior-date]
  [:span
   (if (zero? delta)
      "no change"
      [:span {:class "metric-diff"} (utils/with-currency currency (utils/with-size-label delta) true)])
      (str " since " prior-date)]))

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
        contiguous-periods (when (seq periods)
                            (take (count (utils/contiguous (map :period periods) (keyword interval))) periods))
        prior-contiguous? (>= (count contiguous-periods) 2)
        sparkline? (>= (count contiguous-periods) 3) ; sparklines are possible at 3 or more
        spark-periods (when sparkline? (reverse (take-while #(number? (:value %)) (take 4 contiguous-periods)))) ; 3-4 periods for potential sparklines
        ;; Info on prior period
        prior-metric (when prior-contiguous? (second contiguous-periods))
        prior-period (when (and interval prior-metric) (utils/parse-period interval (:period prior-metric)))
        prior-date (when (and interval prior-period) (utils/format-period interval prior-period))
        formatted-prior-date (when prior-date (s/join " " (butlast (s/split prior-date #" ")))) ; drop the year
        prior-value (when prior-metric (:value prior-metric))
        metric-delta (when (and value prior-value (not= prior-value 0)) (- value prior-value))
        metric-delta-percent (when metric-delta (* 100 (float (/ metric-delta prior-value))))
        formatted-metric-delta (when metric-delta-percent (format-delta metric-delta-percent formatted-prior-date))
        sparkline-metric (when (>= (count spark-periods) 3) (sl/sparkline-html (map :value spark-periods) :blue))
        ;; Format output
        label [:span [:b metric-name] " " date " " sparkline-metric]
        sub-label [:span formatted-metric-delta]
        formatted-value (case unit
                          "%" (str (utils/with-size-label value) "%")
                          "currency" (utils/with-currency currency (utils/with-size-label value))
                          (utils/with-size-label value))]
    (when (and interval (number? value))
      (metric formatted-value label sub-label))))

(defn- periods-for-metric
  "Given the specified metric, return a sequence of all the periods in the data for that metric, sorted by most recent."
  [metric data]
  (when-let [periods (filterv #(= (:slug %) (:slug metric)) data)]
    (reverse (sort-by :period periods))))

(defn growth-metrics [entry currency]
  (let [data (:data entry)
        metadata (:metrics entry)
        periods (map #(periods-for-metric % data) metadata)]
    [:table {:class "growth-metrics"}
      [:tr
        (into [:td]
          (map #(growth-metric % metadata currency) periods))]]))

(defn- finance-metrics [entry currency]
  (let [sorted-finances (reverse (sort-by :period (:data entry)))
        ;; Most recent finances
        finances (first sorted-finances)
        period (format/parse utils/monthly-period (:period finances))
        date (s/upper-case (format/unparse utils/monthly-date period))
        ;; Check for older periods contiguous to most recent
        contiguous-periods (when (seq sorted-finances)
                            (take (count (utils/contiguous (map :period sorted-finances))) sorted-finances))
        prior-contiguous? (>= (count contiguous-periods) 2)
        sparkline? (>= (count contiguous-periods) 3) ; sparklines are possible (finance data might be sparse though)
        spark-periods (when sparkline? (take 4 contiguous-periods)) ; 3-4 periods for potential sparklines
        ;; Info on prior period
        prior-finances (when prior-contiguous? (second contiguous-periods))
        prior-period (when prior-finances (format/parse utils/monthly-period (:period prior-finances)))
        prior-date (when prior-period (s/upper-case (format/unparse month-formatter prior-period)))
        ;; Info on cash
        cash (:cash finances)
        cash? (utils/not-zero? cash)
        formatted-cash (when cash? (utils/with-currency currency (utils/with-size-label cash)))
        prior-cash (when prior-finances (:cash prior-finances))
        prior-cash? (utils/not-zero? prior-cash)
        cash-delta (when (and cash? prior-cash?) (- cash prior-cash))
        formatted-cash-delta (when cash-delta (format-delta currency cash-delta prior-date))
        ;; Info on revenue
        revenue (:revenue finances)
        revenue? (utils/not-zero? revenue)
        formatted-revenue (when revenue? (utils/with-currency currency (utils/with-size-label revenue)))
        prior-revenue (when prior-finances (:revenue prior-finances))
        prior-revenue? (utils/not-zero? prior-revenue)
        revenue-delta (when (and revenue? prior-revenue?) (- revenue prior-revenue))
        revenue-delta-percent (when revenue-delta (* 100 (float (/ revenue-delta prior-revenue))))
        formatted-revenue-delta (when revenue-delta-percent (format-delta revenue-delta-percent prior-date))
        spark-revenue-periods (when spark-periods (reverse (take-while #(number? (:revenue %)) spark-periods)))
        spark-revenue (when (>= (count spark-revenue-periods) 3)
                        (sl/sparkline-html (map :revenue spark-revenue-periods) :green))
        ;; Info on costs/expenses
        costs (:costs finances)
        costs? (utils/not-zero? costs)
        formatted-costs (when costs? (utils/with-currency currency (utils/with-size-label costs)))
        prior-costs (when prior-finances (:costs prior-finances))
        prior-costs? (utils/not-zero? prior-costs)
        costs-delta (when (and costs? prior-costs?) (- costs prior-costs))
        costs-delta-percent (when costs-delta (* 100 (float (/ costs-delta prior-costs))))
        formatted-costs-delta (when costs-delta-percent (format-delta costs-delta-percent prior-date))        
        spark-costs-periods (when spark-periods (reverse (take-while #(number? (:costs %)) spark-periods)))
        spark-costs (when (>= (count spark-costs-periods) 3) (sl/sparkline-html (map :costs spark-costs-periods) :red))
        cost-label (if revenue? "Expenses" "Burn")
        ;; Info on runway (calculated) 
        cash-flow (- (or revenue 0) (or costs 0))
        runway? (and cash? costs? (or (not revenue?) (> costs revenue)))
        runway (when runway? (utils/calc-runway cash cash-flow))
        formatted-runway (when runway? (str ", " (utils/get-rounded-runway runway) " runway"))]
    [:table {:class "finances-metrics"}
      (when revenue?
        [:tr
          [:td 
            (metric formatted-revenue [:span [:b "Revenue"] " " date " " spark-revenue]
              [:span formatted-revenue-delta])]])
      (when costs? 
        [:tr
          [:td
            (metric formatted-costs [:span [:b cost-label] " " date " " spark-costs]
              [:span formatted-costs-delta])]])
      (when cash?
        [:tr
          [:td
            (metric formatted-cash [:span [:b "Cash"] " " date]
              [:span formatted-cash-delta formatted-runway])]])]))


(defn- data-entry [update topic-slug entry last-entry?]
  (let [currency (:currency update)
        data? (seq (:data entry))
        body? (not (s/blank? (:body entry)))
        view-charts? (> (count (:data entry)) 1)
        title (:title entry)
        title? (not (s/blank? title))
        headline (:headline entry)
        headline? (not (s/blank? headline))
        entry-class (str "row entry" (when last-entry? " last"))]
    [:table {:class entry-class}
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          (when title?
            (spacer 18))
          (when title?
            [:p {:class "entry-title"} (s/upper-case title)])
          (when headline?
            [:p {:class "entry-headline"} headline])
          (when data?
            (if (= topic-slug "finances")
              (finance-metrics entry currency)
              (growth-metrics entry currency)))
          (when body? (:body entry))
          (when body? (spacer 10))]
        [:th {:class "expander"}]]]))

(defn- entry [update topic-slug entry last-topic?]
  (let [org-slug (:org-slug update)
        slug (:slug update)]
    (if (:data entry)
      (data-entry update topic-slug entry last-topic?)
      (content-entry update topic-slug entry last-topic?))))

(defn- paragraph [content]
  [:table {:class "row"}
    [:tbody
      [:tr
        [:th {:class "small-1 large-2 first columns"}]
        [:th {:class "small-10 large-8 columns"}
          [:p {:class "text-center"} content]]
        [:th {:class "small-1 large-2 last columns"}]
        [:th {:class "expander"}]]]])

(defn- update-content [update]
  (let [title? (not (s/blank? (:title update)))]
    [:td
      [:table
        [:tr
          (let [entries (:entries update)]
            (into [:td]
              (map #(entry update (:topic-slug %) % (= (:topic-slug %) (:topic-slug (last entries)))) entries)))]]]))

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
      (paragraph (str "Hi " first-name "! " (:subject invite)))
      (spacer 15)
      (cta-button "OK! LET'S GET STARTED ➞" (:token-link invite))
      (spacer 30)
      (paragraph tagline)]))

(defn- reset-content [reset]
  (let [first-name (if (s/blank? (:first-name reset)) "there" (:first-name reset))]
    [:td
      (spacer 15)
      (paragraph (str "Hi " first-name "! " reset-message))
      (spacer 15)
      (cta-button "RESET PASSWORD ➞" (:token-link reset))
      (spacer 15)
      (paragraph reset-instructions)
      (spacer 15)
      (oc-logo)
      (paragraph tagline)]))

(defn- update-link-content [update]
  (let [org-name (:name update)
        org-name? (not (s/blank? org-name))
        title (:title update)
        title? (not (s/blank? title))
        link-title (if title? title (str org-name " Update"))
        org-slug (:org-slug update)
        slug (:slug update)
        origin-url (:origin-url update)
        created-at (format/parse iso-format (:created-at update))
        update-time (format/unparse link-format created-at)
        update-url (s/join "/" [origin-url org-slug "updates" update-time slug])]
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
                    [:th {:class "small-12 large-12 first columns"} 
                      [:p {:class "text-center"}
                        "Updates by "
                        [:a {:href "https://opencompany.com/"} "OpenCompany"]]]]]
                (spacer 28 "footer")]]]]]]])

(defn- body [data]
  (let [type (:type data)
        trail-space? (not= type :update-link)]
    [:body
      [:table {:class "body"}
        [:tr
          [:td {:class "float-center", :align "center", :valign "top"}
            (when-not (s/blank? (:note data)) (note data trail-space?))
            (when (and (s/blank? (:note data)) (= type :update-link)) (spacer 15 "note"))
            (when (= type :update) (spacer 55 "blank"))
            (if (= type :update-link)
              (update-link-content data)     
              [:center
                [:table {:class "container"}
                  [:tr
                    (case type
                      :update (update-content data)
                      :reset (reset-content data)
                      :invite (invite-content data))]]])
            (when (= type :update) (footer))
            (when (= type :update) (spacer 55 "blank"))]]]]))

(defn- head [data]
  (let [type (:type data)
        css (if (= type :update) "oc-update.css" "oc-transactional.css")]
    [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
      [:head 
        [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
        [:meta {:name "viewport", :content "width=device-width"}]
        [:link {:rel "stylesheet", :href "resources/css/foundation.css"}]
        [:link {:rel "stylesheet", :href (str "resources/css/" css)}]
        [:link {:href "http://fonts.googleapis.com/css?family=Domine", :rel "stylesheet", :type "text/css"}]
        [:link {:href "http://fonts.googleapis.com/css?family=Open+Sans", :rel "stylesheet", :type "text/css"}]]
      (body data)]))

(defn- html [data type]
  (str doc-type (h/html (head (assoc (keywordize-keys data) :type type)))))

(defn invite-subject [invite]
  (let [msg (keywordize-keys invite)
        org-name (:org-name msg)
        from (:from msg)
        prefix (if (s/blank? from) "You've been invited" (str from " invited you"))
        org (if (s/blank? org-name) "" (str org-name " on "))]
    (str prefix " to join " org "OpenCompany.")))

(defn update-link-html [update]
  (html update :update-link))

(defn update-html [update]
  (html update :update))

(defn invite-html [invite]
  (html (assoc invite :subject (invite-subject invite)) :invite))

(defn invite-text [invite]
  (let [link (:token-link (keywordize-keys invite))]
    (str (invite-subject invite) ".\n\n"
         tagline "\n\n"
         "Open the link below to check it out.\n\n"
         link "\n\n")))

(defn reset-html [reset]
  (html reset :reset))

(defn reset-text [reset]
  (let [link (:token-link (keywordize-keys reset))
        first-name (if (s/blank? (:first-name reset)) "there" (:first-name reset))]
    (str "Hi " first-name "! " reset-message "\n\n"
         "Click the link below to reset your password.\n\n"
         link "\n\n"
         reset-instructions "\n\n")))

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

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def update (json/decode (slurp "./opt/samples/updates/green-labs.json")))
  (spit "./hiccup.html" (content/update-html (assoc update :note note)))

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

  (spit "./hiccup.html" (content/reset-html {}))
  (content/reset-text {})

  )