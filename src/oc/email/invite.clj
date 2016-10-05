(ns oc.email.invite
  (:require [clojure.string :as s]
            [hiccup.core :as h]
            [clojure.walk :refer (keywordize-keys)]))

(defn text [msg]
  (str (:subject msg) ".\n\n"
       "OpenCompany is the simplest way to keep everyone on the same page.\n\n"
       "Open the link below to check it out.\n\n"
       (:token-link msg) "\n\n"))

(defn html [msg]
  )