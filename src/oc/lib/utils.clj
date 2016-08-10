(ns oc.lib.utils)

(defn in?
  "true if seq contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn fix-runway [runway]
  (if (neg? runway)
    (Math/abs runway)
    0))

(defn remove-trailing-zero [string]
  "Remove the last zero(s) in a numeric string only after the dot.
   Remote the dot too if it is the last char after removing the zeros"
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