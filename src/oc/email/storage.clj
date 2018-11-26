(ns oc.email.storage
  "Get list of sections and post data from the storage service."
  (:require [clojure.walk :refer (keywordize-keys)]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [oc.email.config :as config]
            [oc.email.auth :as auth]))

(def default-on-error [{:name "General" :slug "general"}])

(defn- get-post-options
  [token]
  {:headers {"Authorization" (str "Bearer " token)}})

(defn- storage-request-org-url
  [org]
  ;; /orgs/:org-slug
  (str config/storage-server-url "/orgs/" org))

(defn- get-data
  [request-url token]
  (let [response (http/get request-url (get-post-options token))
        status (:status response)
        success? (= status 200)]
    (timbre/trace "HTTP GET Response:\n" response)
    (if success?
      (-> (:body response) json/parse-string keywordize-keys)
      (timbre/error "HTTP GET failed (" status "):" response))))

(defn post-data-for
  "
  Given a token, team id, and a post id retreive the post information from the storage service.
  "
  [jwtoken team-ids board-slug post-id]
  (if-let [body (get-data (str config/storage-server-url) jwtoken)]
    (do
      (timbre/debug "Storage slash data:" (-> body :collection :items))
      (let [orgs (-> body :collection :items)
            org (first (filter #(team-ids (:team-id %)) orgs))]
        (if org
          (let [org-data (get-data (str config/storage-server-url
                                        "/orgs/"
                                        (:slug org)) jwtoken)
                data (get-data (str config/storage-server-url
                                    "/orgs/"
                                    (:slug org)
                                    "/boards/"
                                    board-slug
                                    "/entries/"
                                    post-id) jwtoken)]
            (-> data
                (assoc :org-uuid (:uuid org-data))
                (assoc :org-slug (:slug org-data))))
          (do
            (timbre/warn "Unable to retrieve board data for:" team-ids "in:" body)
            default-on-error))))
    (do
      (timbre/warn "Unable to retrieve org data.")
      default-on-error)))

(defn get-post-data [payload]
   (let [board-uuid (:board-id payload)
         notification (:notification payload)
         team (:team-id (:org payload))]
     (timbre/debug payload)
     (timbre/debug (auth/user-token (:user-id payload)))))
     "crazy stuff"))
;;         user-token (auth/user-token (:user-id payload)))
;;         teams (set [team])
;;         board-list (storage/board-list-for teams user-token)
;;         board (first (filter #(= board-uuid (:board-uuid %)) board-list))]
;;     (post-data-for user-token teams (:slug board) (:entry-id notification))))

