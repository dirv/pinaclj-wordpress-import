(ns pinaclj-wordpress-import.core-spec
  (:require [speclj.core :refer :all]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [pinaclj-wordpress-import.core :refer :all])
  (:import (com.google.common.jimfs Jimfs Configuration)
           (java.nio.file Files LinkOption)
           (java.nio.charset StandardCharsets)))

(defn- to-record [post]
  {:id (:id post)
   :post_date_gmt (tf/unparse (tf/formatters :mysql) (:post-date-gmt post))
   :post_title (:post-title post)
   :post_content (:post-content post)
   :post_status (:post-status post)
   :post_type (:post-type post)})

(def sample-post
  {:id 101
   :post-title "Test"
   :post-date-gmt (t/date-time 2015 1 18 9 0 0 0)
   :post-content "Testing"
   :post-status "publish"
   :post-type "post"})

(def later-post
  {:post-title "Test"
   :post-date-gmt (t/date-time 2015 1 18 10 0 0 0)
  :post-content "Testing 2"
  :post-status "inherit"
  :post-type "revision"
  :post-parent 101 })

(describe "latest-post"
  (it "returns the latest post"
    (should= later-post (latest-post [sample-post later-post]))))


(describe "post-id"
 (it "uses id for published post"
   (should= 101 (post-id sample-post)))
  (it "uses post-parent for inherited revisions"
    (should= 101 (post-id later-post))))

(def post-a
  {:id 102 :post-date-gmt (t/date-time 2015 1 18)})
(def post-a-rev
  {:id 104 :post-date-gmt (t/date-time 2015 1 19) :post-type "revision" :post-parent 102})
(def post-b
  {:id 103 :post-date-gmt (t/date-time 2015 1 18)})
(def post-b-rev
  {:id 105 :post-date-gmt (t/date-time 2015 1 19) :post-type "revision" :post-parent 103})

(describe "latest-posts"
  (it "returns latest of all posts"
    (should== [post-a-rev post-b-rev] (latest-posts [post-a post-a-rev post-b post-b-rev]))))

(describe "to page"
  (it "outputs published-at date"
    (should-contain "published-at: 2015-01-18T10:00:00.000Z\n" (to-page later-post))),
  (it "outputs title"
    (should-contain "title: Test\n" (to-page later-post)))
  (it "outputs page content after break"
    (should-contain "\n\nTesting 2\n" (to-page later-post))))


(def test-fs
  (Jimfs/newFileSystem (Configuration/unix)))

(defn create-file-system []
  (let [fs test-fs]
    (get-path fs "/test")
    fs))

(defn read-all-lines [path]
  (Files/readAllLines path StandardCharsets/UTF_8))

(defn content [path]
  (clojure.string/join "\n" (read-all-lines path)))

(describe "write-page"
  (it "writes pages to disk"
    (let [fs (create-file-system)
          post sample-post]
      (write-page fs (:id post) (to-page post))
      (should-contain "Testing" (content (get-page-path fs "101"))))))

(def db
  {:classname "org.h2.Driver"
   :subprotocol "h2:mem"
   :subname "test"
   :create true})

(defn- set-up-db [db-conn]
  (sql/db-do-commands db-conn
                      (sql/create-table-ddl :wp_posts
                                            [:id :int "PRIMARY KEY"]
                                            [:post_title :clob]
                                            [:post_content :clob]
                                            [:post_date_gmt :datetime]
                                            [:post_status "varchar(20)"]
                                            [:post_type "varchar(20)"]))
  (doseq [post [post-a post-a-rev post-b post-b-rev]]
    (sql/insert! db-conn :wp_posts (to-record post))))

(defn- remove-nils [post]
  (into {} (filter second post)))

(describe "read-db"
  (it "reads all posts in database table"
      (sql/with-db-connection [db-conn db]
        (set-up-db db-conn)
        (should= 4 (count (read-db db-conn)))))
  (it "restores expected record structure"
      (sql/with-db-connection [db-conn db]
        (set-up-db db-conn)
        (should= post-a (remove-nils (first (read-db db-conn)))))))
