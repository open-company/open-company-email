(ns oc.lib.utils
  (:require [clojure.string :as s]))

(defn in?
  "true if seq contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn fix-runway [runway]
  (if (neg? runway)
    (Math/abs runway)
    0))

(defn remove-trailing-zero
  "Remove the last zero(s) in a numeric string only after the dot.
   Remote the dot too if it is the last char after removing the zeros"
   [string]
   (cond

    (and (not= (.indexOf string ".") -1) (= (last string) "0"))
    (remove-trailing-zero (subs string 0 (dec (count string))))

    (= (last string) ".")
    (subs string 0 (dec (count string)))

    :else
    string))

(defn pluralize [n]
  (if (> n 1)
    "s"
    ""))

(defn get-rounded-runway [runway-days & [flags]]
  (let [abs-runway-days (Math/abs runway-days)]
    (cond
      ; days
      (< abs-runway-days 7)
      (let [days (int abs-runway-days)]
        (str days " day" (pluralize days)))
      ; weeks
      (< abs-runway-days (* 30 3))
      (let [weeks (int (/ abs-runway-days 7))]
        (str weeks " week" (pluralize weeks)))
      ; months
      (< abs-runway-days (* 30 12))
      (if (in? flags :round)
        (let [months (int (/ abs-runway-days 30))
              fixed-months (if (in? flags :remove-trailing-zero)
                             (remove-trailing-zero (str months))
                             (str months))]
          (str fixed-months " month" (pluralize months)))
        (let [months (quot abs-runway-days 30)]
          (str months " month" (pluralize months))))
      ; years
      :else
      (if (in? flags :round)
        (let [years (int (/ abs-runway-days (* 30 12)))
              fixed-years (if (in? flags :remove-trailing-zero)
                            (remove-trailing-zero (str years))
                            (str years))]
          (str fixed-years " year" (pluralize years)))
        (let [years (quot abs-runway-days (* 30 12))]
          (str years " year" (pluralize years)))))))

(defn remove-trailing
  ""
  [value]
  {:pre [(string? value)]
   :post [(string? value)]}
  (if-not (or (and (s/ends-with? value "0") (.contains value "."))
              (s/ends-with? value "."))
    value
    (remove-trailing (subs value 0 (- (count value) 1)))))

(defn truncate-decimals
  "Round and truncate to a float value to at most the specified number of decimal places,
  leaving no trailing 0's to the right of the decimal."
  [value decimals]
  {:pre [(number? value) (pos? decimals) (integer? decimals)]
   :post [(string? %)]}
  (let [exp (Math/pow 10 decimals)]
    (remove-trailing (format (str "%." decimals "f") (float (/ (Math/round (* exp value)) exp))))))

(defn with-size-label [orig-value]
  (when orig-value
    (let [neg (neg? orig-value)
          value (Math/abs orig-value)
          short-value (cond
                        ; 100M
                        (>= value 100000000)
                        (str (truncate-decimals(int (/ value 1000000)) 2) "M")
                        ; 10.0M
                        (>= value 10000000)
                        (str (truncate-decimals (/ value 1000000) 1) "M")
                        ; 1.00M
                        (>= value 1000000)
                        (str (truncate-decimals (/ value 1000000) 2) "M")
                        ; 100K
                        (>= value 100000)
                        (str (truncate-decimals (int (/ value 1000)) 2) "K")
                        ; 10.0K
                        (>= value 10000)
                        (str (truncate-decimals (/ value 1000) 1) "K")
                        ; 1.00K
                        (>= value 1000)
                        (str (truncate-decimals (/ value 1000) 2) "K")
                        ; 100
                        (>= value 100)
                        (str (truncate-decimals (int value) 2))
                        ; 10.0
                        (>= value 10)
                        (str (truncate-decimals value 1))
                        ; 1.00
                        :else
                        (str (truncate-decimals value 2)))]
      (if neg
        (str "-" short-value)
        short-value))))