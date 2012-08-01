(ns strifescript.views.common
  (:require [clojure.string]
            [strifescript.models :as models]
            [noir.validation :as vali]
            [noir.session :as session])
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]]
        [hiccup.element :only [link-to mail-to]]))

(defpartial row [& content] [:div.row-fluid content])
(defpartial span12 [& content] [:div.span12 content])
(defpartial span6 [& content] [:div.span6 content])
(defpartial spanoffset6 [& content] (list (span6) (span6 content)))
(defpartial span3 [& content] [:div.span3 content])
(defpartial row12 [& content] (row (span12 content)))

(defpartial layout [& content]
            (html5
              [:head
               [:title "strifescript"]
               (include-css "/css/bootstrap.min.css")
               (include-css "/css/bootstrap-responsive.min.css")
               (include-js "/js/zepto.min.js")
               (include-js "/js/lodash.min.js")
               (include-js "/js/strifescript.js")]
              [:body
               [:div.container-fluid
                content
                (row12 (mail-to "jon@inklesspen.com" "Contact Maintainer"))]]))

(defn current-user []
  (when-let [session-username (session/get :username)]
    (models/get-user session-username)))

(defn has-error? [field]
  (vali/not-nil? (vali/get-errors field)))

(defpartial error-item [errors]
  (for [error errors] [:span.help-inline error]))

; Maybe TODO: add support for xs like regular conj
(defn if-conj "If test is true, return (conj coll x), else return coll"
  [test coll x]
  (if test (conj coll x) coll))

(defn element-keyword
  ([element-name classes] (keyword (clojure.string/join "." (cons element-name classes))))
  ([element-name id classes] (element-keyword (str element-name "#" id) classes)))

(defn wrapper-keyword [field-name]
  (element-keyword "div" (if-conj (has-error? field-name) ["control-group"] "error")))

(defpartial field-with-label [label attrs]
  (let [proposed-name (. (. label toLowerCase) replace " " "-")
        attrs (conj {:type "text" :name proposed-name :id proposed-name :class "input-xlarge"} attrs)
        name (keyword (:name attrs))
        wrapper (wrapper-keyword name)]
    [wrapper
     [:label.control-label {:for (:id attrs)} label]
     [:div.controls
      [:input attrs]
      (vali/on-error name error-item)]]))


(defpartial breadcrumbs [& links]
  (row12
   [:ul.breadcrumb
    (for [i (range (count links))]
      (let [link (nth links i)
            is-last (= i (dec (count links)))]
        (if is-last
          [:li.active link]
          [:li (link-to (second link) (first link))  [:span.divider "/"]])))]))

(defpartial pill-badge [badge-color icon-name]
  [(element-keyword "span" ["badge" (str "badge-" badge-color)])
                        [(element-keyword "i" ["icon-white" (str "icon-" icon-name)])]])

(defpartial green-red-pill-badge [green]
  (pill-badge (if green "success" "important") (if green "ok" "remove")))

