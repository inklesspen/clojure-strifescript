(ns strifescript.views.welcome
  (:require [clojure.string]
            [strifescript.models :as models]
            [noir.validation :as vali]
            [noir.session :as session])
  (:use [strifescript.views.common]
        [noir.core :only [defpage defpartial render pre-route url-for]]
        [noir.response :only [json redirect]]
        [hiccup.core]
        [hiccup.element]
        [hiccup.form]
        [korma.db :only [transaction rollback is-rollback?]]))

(defn valid-registration? [{:keys [username password confirm-password code]}]
  (vali/rule (models/free-username? username)
             [:username "That username is already taken. Please choose a different one."])
  (let [msg "Password and confirmation must match!"]
    (vali/rule (= password confirm-password) [:confirm-password msg])
    (vali/rule (= password confirm-password) [:password msg]))
  (vali/rule (= (clojure.string/lower-case code) "kestral") [:code "That is not a valid registration code."])
  (not (vali/errors? :username :password :code)))

(defpartial signin-fields [{:keys [username]}]
  (field-with-label "Username" {:id "signin-username" :value username})
  (field-with-label "Password" {:id "signin-password" :type :password}))

(defpartial signin-form [input]
  (form-to {:class "form-horizontal"} [:post "/user/signin"]
           [:div.control-group
            [:div.controls
             [:p.help-block "Sign in:"]]]
           (if-let [errs (vali/get-errors :form)]
             [(wrapper-keyword :form)
              [:div.controls (error-item errs)]])
           (signin-fields input)
           [:div.form-actions [:button.btn.btn-primary {:type :submit} "Sign in"]]))

(defpage "/user/signin" {:as input}
  (layout
   (row12 "Thanks for trying out StrifeScript")
   (row
    (spanoffset6 (signin-form input)))))

(defpage [:post "/user/signin"] {:as input}
  (let [user (models/get-user (:username input))
        valid-login (if (nil? user) false (models/valid-password? user (:password input)))]
    (if valid-login
      (do
        (session/put! :username (:username user))
        (redirect "/me"))
      (do
        (doseq [field [:username :password]] (vali/set-error field "Username and/or password is invalid."))
        (render "/user/signin" input)))))

(defpage "/user/signout" []
  (layout
   (row12 "Thanks for trying out StrifeScript!")
   (row
    (spanoffset6
     (form-to {:class "form-horizontal"} [:post "/user/signout"]
              [:div.form-actions [:button.btn.btn-primary {:type :submit} "Sign out"]])
     ))))

(defpage [:post "/user/signout"] {:as input}
  (session/remove! :username)
  (redirect "/"))

(defpartial register-fields [{:keys [username password code]}]
  (field-with-label "Username" {:id "register-username" :value username})
  (field-with-label "Password"
    (if-conj (not (has-error? :password))
             {:id "register-password" :type :password} {:value password}))
  (field-with-label "Confirm Password"
    (if-conj (not (has-error? :password))
             {:id "register-confirm-password" :type :password} {:value password}))
  (field-with-label "Registration code" {:id "register-code" :value code :name "code"}))
  
(defpartial register-form [{:as input}]
  (form-to {:class "form-horizontal"} [:post "/user/register"]
           [:div.control-group
            [:div.controls
             [:p.help-block "Register:"]]]
           (register-fields input)
           [:div.form-actions [:button.btn.btn-primary {:type "submit"} "Register"]]))


(defpage "/user/register" {:as input}
  (layout
   (row
    (span12 "Thanks for trying out StrifeScript"))
   (row
    (span6)(span6 (register-form input)))))

(defpage [:post "/user/register"] {:as input}
  (transaction
   (if (valid-registration? input)
     (do
       (models/add-user (:username input) (:password input))
       (render [:post "/user/signin"] input))
     (do (rollback)
         (render "/user/register" input)))))

(defpage "/" []
         (layout
          [:div.hero-unit
           [:h1 "StrifeScript is a RPG conflict assistant."]
           [:p (link-to "http://www.burningwheel.org/?page_id=2" "Burning Wheel")
            " and " (link-to "http://www.mouseguard.net/books/role-playing-game/" "Mouse Guard")
            " use a conflict resolution system that can be difficult to handle properly over"
            " the internet. StrifeScript helps GMs and players coordinate their actions to"
            " resolve conflicts faster and with confidence in the system's mechanics."]
           [:p "This is an extremely early version of StrifeScript, so expect rough edges."]]
          [:div.row-fluid
           (span6 (signin-form {}))
           (span6 (register-form {}))
           ]))