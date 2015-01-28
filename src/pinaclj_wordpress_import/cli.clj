(ns pinaclj-wordpress-import.cli
  (:requre [clojure.tools.cli :refer [parse-opts]]
           [pinaclj-wordpress-import.core :as core])
  (:import (java.nio.file FileSystems))
  (:gen-class))

(def cli-options
  [["-h" "--host <host>" "Hostname" :default "localhost"]
   ["-db" "--database <name>" "Database"]
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
       (string/join \newline)))

(defn- db [host db-name user password]
  {:classname "com.mysql.jdbc.Driver"
   :subpotocol "mysql"
   :subname (str "//" host "/" db-name)
   :user user
   :password password})

(defn- do-import [opts]
  (core/do-import (FileSystems/getDefault)
                  (db (:host opts)
                      (:db-name opts)
                      (:user opts)
                      (:password opts))))

(defn main [args]
  (let [{:keys [options summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (println (usage summary))
      else (do-import options))))

(defn- main [& args]
  (main args))
