(ns strifescript.views.dashboard
  (:require [clojure.string]
            [strifescript.models :as models]
            [noir.validation :as vali]
            [noir.session :as session])
  (:use [strifescript.views.common]
        [clojure.string :only [replace-first]]
        [noir.core :only [defpage defpartial render pre-route url-for]]
        [noir.response :only [json redirect]]
        [hiccup.core]
        [hiccup.element]
        [hiccup.form]
        [korma.db :only [transaction rollback is-rollback?]]))


(def id-pattern (re-pattern "\\d+"))

(defpartial page-header [h1]
  [:div.page-header [:h1 h1]])

(pre-route "/me*" []
          (when-not (current-user) (redirect "/user/signin")))

(defpage page-dashboard "/me" []
  (let [user (current-user)]
    (layout (page-header (str "Dashboard for " (h (:username user))))
            (breadcrumbs "Dashboard")
            (row
             (span6 (row12 [:h3 "Conflicts"])
                    (row12 [:div (link-to (url-for page-create-conflict) [:i.icon-plus-sign] " New Conflict")])
                    (row12
                     [:ul.unstyled
                      (for [conflict (models/conflicts user)]
                        [:li (link-to
                              (url-for page-conflict {:id (:id conflict)})
                              (h (:name conflict)))])
                      ]))
             (span6
              (row12 [:h3 "User Controls"])
              (row12 (link-to {:class "signout-link"} "/user/signout" "Sign Out"))
              )
             ))))

(pre-route "/conflict*" []
           (when-not (current-user) (redirect "/user/signin")))

(defpartial username-nym-combo [input team-number member-number]
  (let [base (str "team-" team-number "-" member-number "-")
        id-base (str "conflict-" base)
        username-name (str base "username")
        username-id (str id-base "username")
        nym-name (str base "nym")
        nym-id (str id-base "nym")]
    [:div.control-group
     [:div.controls
      (let [username-keyword (keyword username-name)
            username-input [:input.input-medium {:placeholder "username" :type "text" :name username-name :id username-id :value (username-keyword input)}]
            nym-keyword (keyword nym-name)
            nym-input [:input.input-medium {:placeholder "nym" :type "text" :name nym-name :id nym-id :value (nym-keyword input)}]]
        (list
         (if (has-error? username-keyword)
           [:span.control-group.error username-input]
           username-input)
         " as "
         (if (has-error? nym-keyword)
           [:span.control-group.error nym-input]
           nym-input)
         (when-let [errs (seq (concat (vali/get-errors username-keyword) (vali/get-errors nym-keyword)))]
           [:p.control-group.error {:style "margin-bottom: 0px;"}
            (for [err errs] [:span.help-block err])
            ])))]]))

(defpartial new-conflict-form [input]
  (form-to {:class "form-horizontal" :id "conflict-form"} [:post "/conflict"]
           (if-let [errs (vali/get-errors :form)]
             (row12
              [(wrapper-keyword :form)
               [:div.controls (error-item errs)]]))
           (row12
            (field-with-label "Conflict Name" {:name "name" :value (:name input)}))
           (row12
            [:div.alert.alert-block [:h4.alert-heading "Be Aware!"]
             "You can't change the team composition after creating a conflict; if you could do this, it would compromise the secrecy of unrevealed volleys. If you make a mistake setting up the teams, your only option is to start over."]
            ;; [:div.control-group
            ;;  [:label.control-label {:for "conflict-team-count"} "Number of Teams"]
            ;;  [:div.controls
            ;;   [:select.input-medium {:id "conflict-team-count" :name "team-count"} (select-options ["2" "3" "4"] "2")]
            ;;   ]]
            )
           [:div#teams
            (row12
             [:h3.controls "Team 1"]
             (username-nym-combo input 1 1)
             (username-nym-combo input 1 2)
             (username-nym-combo input 1 3)
             )
            (row12
             [:h3.controls "Team 2"]
             (username-nym-combo input 2 1)
             (username-nym-combo input 2 2)
             (username-nym-combo input 2 3)
             )]
           (row12
            [:div.form-actions [:button.btn.btn-success {:type :submit} "Create Conflict"]])))

(defpage page-create-conflict "/conflict" {:as input}
  (layout (page-header "New Conflict")
          (breadcrumbs ["Dashboard" (url-for page-dashboard)] "New Conflict")
          (new-conflict-form input)))

(defn parse-team-field-name [name-keyword]
  (if-let [[_ team member kind] (re-find #"team-(\d+)-(\d+)-((?:username)|(?:nym))" (name name-keyword))]
    {:team (Integer/parseInt team) :member (Integer/parseInt member) :kind kind :field name-keyword}))

(defn valid-conflict? [input]
  ;; verify we have a name
  (vali/rule (vali/min-length? (:name input) 1) [:name "Please name this conflict."])
  (let [parsed-keys (map #(conj % {:value ((:field %) input)}) (keep #(if-let [parsed (parse-team-field-name %)] parsed) (keys input)))
        username-maps (filter #(= (:kind %) "username") parsed-keys)
        duplicate-usernames (set (map key (remove (comp #{1} val) (frequencies (map :value username-maps)))))]
    (doseq [username-map username-maps]
      ;; verify all usernames specified are valid usernames
      (vali/rule (models/user-exists? (:value username-map)) [(:field username-map) "That username does not exist."])
      ;; verify the username isn't on multiple teams
      (vali/rule (not (duplicate-usernames (:value username-map))) [(:field username-map) "Users cannot be on multiple teams."])
      ;; verify each username has a corresponding nym
      (let [nym-key (keyword (replace-first (name (:field username-map)) "username" "nym"))
            nym-value (nym-key input)]
        (vali/rule (vali/min-length? nym-value 1) [nym-key "Please specify a nym for this user."])))
    (when-not (vali/errors?)
      {:name (:name input) :teams
       (let [team-grouped (group-by :team parsed-keys)
             teams (map team-grouped (sort (keys team-grouped)))]
         (for [team teams]
           (let [member-grouped (group-by :member team)
                 members (map member-grouped (sort (keys member-grouped)))]
             (for [member members]
               (let [kind-grouped (group-by :kind member)]
                 (apply assoc {} (apply concat (map #(vector (keyword (first %)) (:value (first (second %)))) kind-grouped))))))))}
      )))

(defpage [:post "/conflict"] {:as input}
  (transaction
   (if-let [validated (valid-conflict? input)]
     (do
       (let [conflict-id (models/add-conflict (:name validated) (:teams validated))]
         (redirect (url-for page-conflict {:id conflict-id}))))
     (do
       (rollback)
       (render page-create-conflict input)))))

(defpage page-conflict [:get "/conflicts/:id" :id id-pattern] {:keys [id]}
  (let [id (Integer/parseInt id)
        user (current-user)
        conflict (models/get-conflict user id)
        team (models/get-team user conflict)]
    (if (nil? conflict)
      (redirect "/me")
      (layout [:div.page-header [:h1 (h (:name conflict))]]
              (breadcrumbs ["Dashboard" (url-for page-dashboard)] (str "Conflict: " (h (:name conflict))))
              (row
               (span6
                (row12 [:h3 "Exchanges"])
                (let [exchanges (reverse (models/exchanges conflict))]
                  (list
                   (if (every? models/exchange-resolved? exchanges)
                     (row12 [:div (link-to (url-for page-create-exchange {:id id}) [:i.icon-plus-sign] " New Exchange")]))
                   (row12
                    [:ul.unstyled
                     (for [exchange exchanges]
                       (let [script-entered (not (nil? (models/get-script (:id exchange) (:id team))))
                             url-params {:conflict-id id :exchange-id (:id exchange)}]
                         [:li (link-to (if script-entered (url-for page-show-exchange-progress url-params) (url-for page-set-exchange-script url-params))  (models/exchange-name exchange))
                          (if (models/exchange-resolved? exchange) (list " " (pill-badge "success" "ok")))]))]))))
               (span6
                (row12 [:h3 "Teams"])
                (row12
                 [:ul.unstyled
                  (for [team (models/teams conflict)]
                    [:li [:b (h (:name team))]
                     [:ul
                      (for [belligerent (models/belligerents team)]
                        [:li (h (:nym belligerent)) " (" (h (:username (models/get-user-by-id (:user-id belligerent)))) ")"])]])])))))))

(defn ordinal [n]
  (let [hr (mod n 100)
        tr (mod n 10)] 
    (if (= 10 (- hr tr))
      (str n "th")
      (case tr
        1 (str n "st")
        2 (str n "nd")
        3 (str n "rd")
        (str n "th")
        ))))

(defpage page-create-exchange [:get "/conflicts/:id/exchange" :id id-pattern] {:keys [id]}
  (let [id (Integer/parseInt id)
        user (current-user)
        conflict (models/get-conflict user id)]
    (if (nil? conflict)
      (redirect "/me")
      (layout [:div.page-header [:h1 "New Exchange in " (h (:name conflict))]]
              (breadcrumbs ["Dashboard" (url-for page-dashboard)] [(str "Conflict: " (h (:name conflict))) (url-for page-conflict {:id id})] "New Exchange")
              (row12 "This will be the " (ordinal (inc (count (models/exchanges conflict)))) " exchange in this conflict.")
              (row12
               (form-to {:class "form-horizontal"} [:post (str "/conflicts/" id "/exchange")]
                        [:div.form-actions [:button.btn.btn-success {:type :submit} "Create Exchange"]]
                        )
               )
              )
      )))

(defpage [:post "/conflicts/:id/exchange" :id id-pattern] {:keys [id]}
  (let [id (Integer/parseInt id)
        user (current-user)
        conflict (models/get-conflict user id)]
    (if (nil? conflict)
      (redirect "/me")
      (do (if (every? models/exchange-resolved? (models/exchanges conflict))
            (models/add-exchange (:id conflict)))
          (redirect (url-for page-conflict {:id id}))))))

(defpartial set-actions-form [url input]
  (form-to {:class "form-horizontal" :id "actions-form"} [:post url]
           (if-let [errs (vali/get-errors :form)]
             (row12
              [(wrapper-keyword :form)
               [:div.controls (error-item errs)]]))
           (row12
            [:div.alert.alert-block [:h4.alert-heading "Be Aware!"]
             "You can't change your selected actions once you lock them in, so be sure you get it right."])
           (row12
            (field-with-label "Action #1" {:name "action-1"}))
           (row12
            (field-with-label "Action #2" {:name "action-2"}))
           (row12
            (field-with-label "Action #3" {:name "action-3"}))

           (row12
            [:div.form-actions [:button.btn.btn-success {:type :submit} "Set Actions"]])))

(defn render-new-script-page [exchange conflict team post-url]
  (layout
   [:div.page-header [:h1 "Set Actions for " (models/exchange-name exchange) " " [:small (h (str "(Conflict: \"" (:name conflict) "\") (Team: \"" (:name team) "\")"))]]]
   (breadcrumbs ["Dashboard" (url-for page-dashboard)] [(str "Conflict: " (h (:name conflict))) (url-for page-conflict {:id (:id conflict)})] (models/exchange-name exchange))
   (set-actions-form post-url {})))

(defn render-teams-submitted-page [exchange conflict my-team]
  (let [all-teams (models/teams conflict)
        all-scripts (models/scripts exchange)
        scripts-by-team (zipmap (map :team-id all-scripts) all-scripts)]
    (layout
     [:div.page-header [:h1 "Still awaiting actions for some teams for " (models/exchange-name exchange)]]
     (row (span6
           [:table.table.table-condensed
            [:thead [:tr [:th "Team Name"]]]
            [:tbody
             (for [team all-teams]
               [:tr
                [:td (h (:name team))]
                (let [missing-script (nil? (scripts-by-team (:id team)))]
                  [:td (green-red-pill-badge (not missing-script))])])]])))))

(defn render-exchange-advancement-page [exchange conflict my-team]
  (let [all-teams (models/teams conflict)
        all-scripts (models/scripts exchange)
        reveal-level (apply min (map :actions-revealed all-scripts))
        scripts-by-team (zipmap (map :team-id all-scripts) all-scripts)]
    (layout
     [:div.page-header [:h1 "Actions for " (models/exchange-name exchange)]]
     (breadcrumbs ["Dashboard" (url-for page-dashboard)] [(str "Conflict: " (h (:name conflict))) (url-for page-conflict {:id (:id conflict)})] (models/exchange-name exchange))
     (row (span6
           [:table.table.table-striped.table-condensed
            [:thead [:tr [:th "Team Name"] [:th "First Action"] [:th "Second Action"] [:th "Third Action"]]]
            [:tbody
             (for [team all-teams]
               (let [team-script (scripts-by-team (:id team))
                     is-my-team (= (:id team) (:id my-team))]
                 [:tr
                  [:td (let [team-name (h (:name team))] (if is-my-team [:b team-name] team-name))]
                  (for [action-count (range 1 4)]
                    (let [action-text (h (nth (:actions team-script) (dec action-count)))]
                      [:td (if (>= reveal-level action-count)
                             action-text
                             (if is-my-team
                               action-text
                               (green-red-pill-badge (>= (:actions-revealed team-script) action-count))))])
                    )
                  ]))]]))
     (row (span6
           (let [my-team-revealed (:actions-revealed (scripts-by-team (:id my-team)))
                 advance-allowed (and (< reveal-level 3) (= reveal-level my-team-revealed))]
             (form-to {:class "form-horizontal" :id "conflict-form"} [:post (str "/conflicts/" (:id conflict) "/exchange/" (:id exchange) "/advance")]
                      [:input {"name" "reveal" "type" "hidden" "value" (inc my-team-revealed)}]
                      [(element-keyword "button" (if-conj (not advance-allowed) ["btn" "btn-large" "btn-primary"] "disabled")) (if (not advance-allowed) {"disabled" "disabled"} {}) (if advance-allowed "Advance" "Already Advanced")]))
           ))
     )))

(defpage page-show-exchange-progress [:get "/conflicts/:conflict-id/exchange/:exchange-id" :conflict-id id-pattern :exchange-id id-pattern] {:keys [conflict-id exchange-id]}
  (let [conflict-id (Integer/parseInt conflict-id)
        exchange-id (Integer/parseInt exchange-id)
        user (current-user)
        exchange (models/get-exchange user conflict-id exchange-id)]
    
    (if (nil? exchange)
      (redirect "/me")
      (let [conflict (models/get-conflict user conflict-id)
            team (models/get-team user conflict)
            script (models/get-script (:id exchange) (:id team))]
        (if (nil? script)
          (redirect (url-for page-set-exchange-script {:conflict-id conflict-id :exchange-id exchange-id}))
          (if (models/all-scripts-entered? exchange)
            (render-exchange-advancement-page exchange conflict team)
            (render-teams-submitted-page exchange conflict team)
            )
          )
        )
      )
    )
  )

(defpage [:post "/conflicts/:conflict-id/exchange/:exchange-id/advance" :conflict-id id-pattern :exchange-id id-pattern] {:keys [conflict-id exchange-id reveal]}
  (let [reveal-level (Integer/parseInt reveal)
        conflict-id (Integer/parseInt conflict-id)
        exchange-id (Integer/parseInt exchange-id)
        user (current-user)
        exchange (models/get-exchange user conflict-id exchange-id)]
    (if (nil? exchange)
      (redirect page-dashboard)
      (let [conflict (models/get-conflict user conflict-id)
            team (models/get-team user conflict)
            script (models/get-script (:id exchange) (:id team))]
        (if (nil? script)
          (redirect (url-for page-conflict {:id conflict-id}))
          (do
            (if (<= reveal-level 3)
              (models/advance-script (:id exchange) (:id team) reveal-level))
            (redirect (str "/conflicts/" (:id conflict) "/exchange/" (:id exchange)))))))))

(defpage page-set-exchange-script [:get "/conflicts/:conflict-id/exchange/:exchange-id/script" :conflict-id id-pattern :exchange-id id-pattern] {:keys [conflict-id exchange-id]}
  (let [conflict-id (Integer/parseInt conflict-id)
        exchange-id (Integer/parseInt exchange-id)
        user (current-user)
        exchange (models/get-exchange user conflict-id exchange-id)]
    (if (nil? exchange)
      (redirect "/me")
      (let [conflict (models/get-conflict user conflict-id)
            team (models/get-team user conflict)
            script (models/get-script (:id exchange) (:id team))]
        (if (nil? script)
          (render-new-script-page exchange conflict team (url-for page-set-exchange-script {:conflict-id conflict-id :exchange-id exchange-id}))
          "derp"
          )
        )
      )
    )
  )



(defpage [:post "/conflicts/:conflict-id/exchange/:exchange-id/script" :conflict-id id-pattern :exchange-id id-pattern] {:keys [conflict-id exchange-id] :as input}
  (let [conflict-id (Integer/parseInt conflict-id)
        exchange-id (Integer/parseInt exchange-id)
        user (current-user)
        exchange (models/get-exchange user conflict-id exchange-id)]
    (if (nil? exchange)
      (redirect "/me")
      (let [conflict (models/get-conflict user conflict-id)
            team (models/get-team user conflict)
            input (dissoc input :conflict-id :exchange-id)]
        (let [result (models/add-script (:id exchange) (:id team) [(:action-1 input) (:action-2 input) (:action-3 input)])
              url-params {:conflict-id (:id conflict) :exchange-id (:id exchange)}
              url (url-for page-show-exchange-progress url-params)]
          (redirect url))))))
