(ns strifescript.models.array-support
  (:require [clojure.java.jdbc :as jdbc])
  (:import (java.sql Connection)
           (com.mchange.v2.c3p0 C3P0ProxyConnection)))

(def createArrayMethod
  (.getMethod Connection "createArrayOf"
              (into-array Class [String (class (into-array Object []))])))

(defn make-sql-array
  "Creates a java.sql.Array of the specified SQL type with the specified sequence as its contents. Must be executed in the context of a transaction (in order to get a reference to the connection)."
  [sql-type seq]
  (. (jdbc/connection) rawConnectionOperation
     createArrayMethod
     C3P0ProxyConnection/RAW_CONNECTION
     (into-array Object [sql-type, (into-array Object seq)])))

(defn read-sql-array
  [sql-array]
  (seq (. sql-array getArray)))