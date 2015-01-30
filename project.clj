(defproject pinaclj-wordpress-import "0.1.0-SNAPSHOT"
  :description "Imports WordPress database posts into Pinaclj format"
  :url "http://github.com/dirv/pinaclj-wordpress-import"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [clj-time "0.9.0"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [mysql/mysql-connector-java "5.1.34"]]
  :profiles {:dev {:dependencies [[speclj "3.1.0"]
                                  [com.h2database/h2 "1.4.185"]
                                  [com.google.jimfs/jimfs "1.0"]]}}
  :plugins [[speclj "3.1.0"]]
  :main pinaclj-wordpress-import.cli
  :test-paths ["spec"])
