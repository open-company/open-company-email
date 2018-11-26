(ns oc.email.auth
  "Uses a magic token to get a valid user token from the auth service"
  (:require [org.httpkit.client :as http]
            [oc.lib.jwt :as jwt]
            [oc.email.config :as config]))

(defn- magic-token
  [user-id]
  (jwt/generate {:user-id user-id
                 :super-user true
                 :name "Slack Email"
                 :auth-source :services}
    config/passphrase))

(def request-token-url
  (str config/auth-server-url "/users/refresh/"))

(defn get-options
  [token]
  {:headers {"Content-Type" "application/vnd.open-company.auth.v1+json"
             "Authorization" (str "Bearer " token)}})

(defn user-token [user-id]
  (let [token-request
        @(http/get request-token-url
                   (get-options (magic-token user-id)))]
    (when (= 201 (:status token-request))
      (:body token-request))))