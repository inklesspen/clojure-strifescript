(ns strifescript.server
  (:require [noir.server :as server]
            [strifescript.models.korma-setup :as db]
            [ring.middleware.session.cookie :as cookie-session])
  (:import (javax.xml.bind DatatypeConverter)))

(server/load-views-ns 'strifescript.views)

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))
        cookie-secret (DatatypeConverter/parseHexBinary (System/getenv "COOKIE_SECRET"))]
    (server/start port {:mode mode
                        :ns 'strifescript
                        :session-store (cookie-session/cookie-store {:key cookie-secret})})))

