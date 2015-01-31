(ns pinaclj-wordpress-import.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.jdbc :as sql]
            [pinaclj-wordpress-import.core :as core])
  (:import (java.nio.file FileSystems))
  (:gen-class))

(def cli-options
  [["-h" "--host <host>" "Hostname" :default "localhost"]
   ["-d" "--database <name>" "Database" :default ""]
   ["-u" "--user <user>" "Username" :default "root"]
   ["-p" "--password <password>" "Password" :default ""]])

(defn- usage [options-summary]
  (->> [""
        "pinaclj-wordpress-import imports WordPress posts into Pinaclj format."
        ""
        "Usage: lein run [options]"
        ""
        "Options:"
        options-summary
        ""]
       (clojure.string/join \newline)))

(defn- db [host db-name user password]
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname (str "//" host "/" db-name)
   :zeroDateTimeBehavior "convertToNull"
   :user user
   :password password})

(defn- do-import [opts]
  (sql/with-db-connection [db-conn (db (:host opts)
                                       (:database opts)
                                       (:user opts)
                                       (:password opts))]
    (core/do-import (FileSystems/getDefault) db-conn)))

(defn main [args]
  (let [{:keys [options summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (println (usage summary))
      :else (do-import options))))

(defn -main [& args]
  (main args))
