(ns strifescript.models.korma-setup
  (:use [korma.db])
  (:require [clojure.string :as string]
            [org.bovinegenius.exploding-fish :as uri]))

(let [dburi (uri/uri (System/getenv "DATABASE_URL"))
      dbhost (:host dburi)
      dbport (get dburi :port 5432)
      db (. (:path dburi) substring 1)
      user-info (string/split (:user-info dburi) #":")
      dbuser (first user-info)
      dbpassword (second user-info)]

  (defdb conn (postgres {:db db
                         :user dbuser
                         :password dbpassword
                         ;;OPTIONAL KEYS
                         :host dbhost
                         :port dbport
                         :delimiters "" ;; remove delimiters
                         :naming {:keys string/lower-case
                                  ;; set map keys to lower
                                  :fields string/upper-case}})))
                                ;; but field names are upper