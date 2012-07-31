(ns strifescript.models.entities
  (:require [strifescript.models.array-support :as array-support])
  (:use [korma.core])
  (:import (org.mindrot.jbcrypt BCrypt)))

(declare users belligerents teams conflicts exchanges scripts)

(defrecord User [id username password-hash])

(defentity users
  (entity-fields :username :password_hash)
  (transform #(->User (:id %) (:username %) (:password_hash %)))
  (has-many belligerents {:fk :user_id})
  )

(defrecord Belligerent [id user-id team-id nym])

(defentity belligerents
  (entity-fields :nym)
  (transform #(->Belligerent (:id %) (:user_id %) (:team_id %) (:nym %)))
  (belongs-to users {:fk :user_id})
  (belongs-to teams {:fk :team_id})
  )

(defrecord Conflict [id name])

(defentity conflicts
  (entity-fields :name)
  (transform #(->Conflict (:id %) (:name %)))
  (has-many teams {:fk :conflict_id})
  (has-many exchanges {:fk :conflict_id})
  )

(defrecord Team [id name conflict-id])

(defentity teams
  (entity-fields :name)
  (transform #(->Team (:id %) (:name %) (:conflict_id %)))
  (belongs-to conflicts {:fk :conflict_id})
  (has-many belligerents {:fk :team_id})
  (has-many scripts {:fk :team_id})
  )

(defrecord Exchange [id ordering conflict-id])

(defentity exchanges
  (entity-fields :ordering)
  (transform #(->Exchange (:id %) (:ordering %) (:conflict_id %)))
  (belongs-to conflicts {:fk :conflict_id})
  (has-many scripts {:fk :exchange_id})
  )

(defrecord Script [actions actions-revealed exchange-id team-id])

(defentity scripts
  (entity-fields :actions :actions_revealed)
  (transform #(->Script (array-support/read-sql-array (:actions %)) (:actions_revealed %) (:exchange_id %) (:team_id %)))
  (belongs-to exchanges {:fk :exchange_id})
  (belongs-to teams {:fk :team_id})
  )