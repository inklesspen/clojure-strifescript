(ns strifescript.scripts.generate-cookie-secret
  (:import javax.xml.bind.DatatypeConverter
           java.security.SecureRandom))


(defn -main [& m]
  (let [seed (byte-array 16)]
    (.nextBytes (SecureRandom/getInstance "SHA1PRNG") seed)
    (println (str "COOKIE_SECRET=" (DatatypeConverter/printHexBinary seed)))))
