(ns strifescript.models
  (:require [strifescript.models.entities :as entities]
            [strifescript.models.array-support :as array-support])
  (:use [korma.core]
        [korma.db :only [transaction rollback is-rollback?]])
  (:import (org.mindrot.jbcrypt BCrypt)
;;           (java.lang.IllegalStateException) ;; Why is this necessary to import!?
           [strifescript.models.entities User Belligerent Conflict Team Exchange]))

(declare user-exists?)

(defn- ensure-single-result [coll]
  (case (count coll)
    0 (throw (IllegalStateException. "No row was found."))
    1 coll
    (throw (IllegalStateException. "Multiple rows were found."))))

(defn- one-result [query]
  (first (ensure-single-result (-> query (limit 2) (select)))))

(defn free-username?
  "Returns true if the given username is available, false otherwise. Should only be called within the context of a transaction, for obvious reasons."
  [username]
  (not (user-exists? username)))

(defn user-exists? [username]
  (= 1 (count (select entities/users (where {:username username})))))

(defn add-user
  [username password]
  (:id (insert entities/users (values {:username username :password_hash (BCrypt/hashpw password (BCrypt/gensalt))}))))

(defn get-user
  [username]
  (first (select entities/users (where {:username username}))))

(defn get-user-by-id [user-id]
  (first (select entities/users (where {:id user-id}))))

(defn valid-password?
  [user candidate-password]
  (let [hash (:password-hash user)]
    (= hash (BCrypt/hashpw candidate-password hash))))


(defn add-team
  [name conflict-id members]
  (def team-id (:id (insert entities/teams (values {:conflict_id conflict-id :name name}))))
  (doseq [member members] (let [user-id (:id (get-user (:username member)))]
                            (insert entities/belligerents (values {:user_id user-id :team_id team-id :nym (:nym member)}))
                          ))
  team-id)

(defn add-conflict
  [name teams]
  (def conflict-id (:id (insert entities/conflicts (values {:name name}))))
  (dorun (map-indexed (fn [idx members] (let [team-name (str "Team " (inc idx))] (add-team team-name conflict-id members))) teams))

  conflict-id)

(defn add-exchange [conflict-id]
  (transaction
   (let [next-number (inc (:count (first (select "exchanges" (fields (raw "COUNT(*)")) (where {:conflict_id conflict-id})))))]
     (:id (insert entities/exchanges (values {:conflict_id conflict-id :ordering next-number})))
     )
   ))

(defn get-script [exchange-id team-id]
  (first (select entities/scripts
                 (where {:exchange_id exchange-id :team_id team-id}))))

;; (defn get-other-scripts [exchange-id team-id]
;;   (select entities/scripts (where {:exchange_id exchange-id
;;                                    :team_id [not= team-id]}))
;;   )

(defn add-script [exchange-id team-id actions]
  (transaction
   (insert entities/scripts
           (values {:exchange_id exchange-id
                    :team_id team-id
                    :actions (array-support/make-sql-array "text" actions)
                    :actions_revealed 0}))))

(defn advance-script [exchange-id team-id reveal-level]
  (update entities/scripts (set-fields {:actions_revealed reveal-level}) (where {:exchange_id exchange-id :team_id team-id})))

(defn all-scripts-entered? [exchange]
  ;; this is pretty ridiculous
  (let [qstring "select team_count = entered_count as all_entered from (select count(id) as team_count from teams where conflict_id = ?) as a, (select count(team_id) as entered_count from scripts where exchange_id = ?) as b"]
    (:all_entered (first (exec-raw [qstring [(:conflict-id exchange) (:id exchange)]] :results)))))

(defn exchange-name [exchange]
  (str "Exchange #" (:ordering exchange)))

(defn exchange-resolved? [exchange]
  (let [qstring "select team_count = resolved_count as all_resolved from (select count(id) as team_count from teams where conflict_id = ?) as a, (select count(team_id) as resolved_count from scripts where exchange_id = ? and actions_revealed = 3) as b"]
    (:all_resolved (first (exec-raw [qstring [(:conflict-id exchange) (:id exchange)]] :results))))
  )

(defn- teams-for-user-id [user-id]
  (-> (select* entities/teams)
      (where {:id [in (subselect entities/belligerents
                                     (fields :team_id)
                                     (where {:user_id user-id}))]}))
  )

(defn get-team
  [user conflict]
  (one-result (-> (teams-for-user-id (:id user)) (where {:conflict_id (:id conflict)}))))

(defmulti belligerents class)
(defmethod belligerents User [user]
  (select entities/belligerents (where {:user_id (:id user)})))

(defmethod belligerents Team [team]
  (select entities/belligerents (where {:team_id (:id team)})))


(defmulti teams class)
(defmethod teams User [user]
  (-> (teams-for-user-id (:id user))
      (select))
)

(defmethod teams Conflict [conflict]
  (select entities/teams
          (where {:conflict_id (:id conflict)})
          (order :id :ASC)))


(defn- conflict-ids-for-user [user-id]
  (subselect entities/teams (fields :conflict_id)
             (where {:id [in (subselect entities/belligerents (fields :team_id) (where {:user_id user-id}))]})
             )
  )


(defn- userconflicts [user_id]
  (-> (select* entities/conflicts)
      (where {:id [in
                   (conflict-ids-for-user user_id)]})))

(defmulti conflicts class)
(defmethod conflicts User [user]
  (select (userconflicts (:id user))))

(defn get-conflict [user conflict_id]
  (first (select (-> (userconflicts (:id user))
                     (where {:id conflict_id})))))

(defmulti exchanges class)
(defmethod exchanges Conflict [conflict]
  (select entities/exchanges
          (where {:conflict_id (:id conflict)})
          (order :ordering :ASC)))

(defn get-exchange [user conflict-id exchange-id]
  (first (select entities/exchanges (where {:id exchange-id :conflict_id conflict-id}) (where {:conflict_id [in (conflict-ids-for-user (:id user))]})))
  )

(defmulti scripts class)
(defmethod scripts Exchange [exchange]
  (select entities/scripts
          (where {:exchange_id (:id exchange)})
          (order :team_id :ASC)))
