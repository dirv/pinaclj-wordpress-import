(ns pinaclj-wordpress-import.core-spec
  (:require [speclj.core :refer :all]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [pinaclj-wordpress-import.core :refer :all])
  (:import (com.google.common.jimfs Jimfs Configuration)
           (java.nio.file Files LinkOption FileSystems)
           (java.nio.charset StandardCharsets)))

(defn- to-record [post]
  {:id (:id post)
   :post_date_gmt (tf/unparse (tf/formatters :mysql) (:post-date-gmt post))
   :post_title (:post-title post)
   :post_content (:post-content post)
   :post_status (:post-status post)
   :post_parent (:post-parent post)
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
  :post-url "/blog/testing/"
  :post-terms ["dev" "test"]
  :post-parent 101 })

(def draft
  {:post-date-gmt (t/date-time 2015 1 31)
   :post-content "Not yet published" })

(describe "latest-post"
  (it "returns the latest post"
    (should= later-post (latest-post [sample-post later-post]))))

(describe "post-id"
 (it "uses id for published post"
   (should= 101 (post-id sample-post)))
  (it "uses post-parent for inherited revisions"
    (should= 101 (post-id later-post))))

(def post-a
  {:id 102 :post-date-gmt (t/date-time 2015 1 18) :post-content "test"})
(def post-a-rev
  {:id 104 :post-date-gmt (t/date-time 2015 1 19) :post-type "revision" :post-parent 102})
(def post-b
  {:id 105 :post-date-gmt (t/date-time 2015 1 18) :post-content "Original"})
(def post-b-rev
  {:id 103 :post-date-gmt (t/date-time 2015 1 19) :post-type "revision" :post-parent 105
   :post-content "Updated" })
(def multi-line
  {:id 106 :post-date-gmt (t/date-time 2015 1 28) :post-content "One\nTwo\nThree\n"})

(def all-pages
  [post-a post-a-rev post-b post-b-rev multi-line])

(def urls
  [{:object_type "post" :object_id 102 :url "/blog/test1/"}
   {:object_type "post" :object_id 105 :url "/blog/test2/"}
   {:object_type "post" :object_id 106 :url "/blog/multi/"}])

(def terms
  [{:term_id 1 :name "dev"}
   {:term_id 2 :name "test"}
   {:term_id 3 :name "Uncategorized"}])

(def term-taxonomy
  [{:term_taxonomy_id 5 :term_id 1 }
   {:term_taxonomy_id 6 :term_id 2 }
   {:term_taxonomy_id 8 :term_id 3 }])

(def term-relationships
  [{:object_id 102 :term_taxonomy_id 5}
   {:object_id 102 :term_taxonomy_id 6}
   {:object_id 102 :term_taxonomy_id 8}])

(describe "latest-posts"
  (it "returns latest of all posts"
    (should== [post-a-rev post-b-rev] (latest-posts [post-a post-a-rev post-b post-b-rev]))))

(describe "to page"
  (it "outputs published-at date"
    (should-contain "published-at: 2015-01-18T10:00:00.000Z\n" (to-page later-post))),
  (it "outputs title"
    (should-contain "title: Test\n" (to-page later-post)))
  (it "outputs url"
    (should-contain "url: /blog/testing/\n" (to-page later-post)))
  (it "outputs page content after break"
    (should-contain "\n---\nTesting 2\n" (to-page later-post)))
  (it "does not output published-at if not yet published"
    (should-not-contain "published-at:" (to-page draft)))
  (it "does not contain url if not yet published"
    (should-not-contain "url:" (to-page draft)))
  (it "outputs tags"
    (should-contain "tags: dev, test\n" (to-page later-post))))

(describe "assoc-maps"
  (it "associates the url"
    (should= "/blog/test1/" (:post-url (assoc-maps post-a-rev (url-map urls) {})))))

(def test-fs
  (Jimfs/newFileSystem (Configuration/unix)))

(defn create-file-system []
  (get-path test-fs "/test")
  test-fs)

(defn read-all-lines [path]
  (Files/readAllLines path StandardCharsets/UTF_8))

(defn content [path]
  (clojure.string/join "\n" (read-all-lines path)))

(describe "write-page"
  (before
    (create-file-system)
    (create-target-directory test-fs))
  (it "writes pages to disk"
      (write-page test-fs "101" (to-page sample-post))
      (should-contain "Testing" (content (get-path test-fs "101.pina")))))

(def db
  {:classname "org.h2.Driver"
   :subprotocol "h2:mem"
   :subname "test"
   :create true})

(defn- set-up-db [db-conn]
  (sql/execute! db-conn ["SET MODE MySQL"])
  (sql/db-do-commands db-conn
                      (sql/create-table-ddl :wp_posts
                                            [:id :int "PRIMARY KEY"]
                                            [:post_title :varchar]
                                            [:post_content :varchar]
                                            [:post_date_gmt :datetime]
                                            [:post_parent :int]
                                            [:post_status "varchar(20)"]
                                            [:post_type "varchar(20)"])
                      (sql/create-table-ddl :wp_urls
                                            [:url "varchar(255)"]
                                            [:object_id :int]
                                            [:object_type "varchar(20)"])
                      (sql/create-table-ddl :wp_terms
                                            [:term_id :int "PRIMARY KEY"]
                                            [:name :varchar])
                      (sql/create-table-ddl :wp_term_relationships
                                            [:object_id :int]
                                            [:term_taxonomy_id :int])
                      (sql/create-table-ddl :wp_term_taxonomy
                                            [:term_id :int]
                                            [:term_taxonomy_id :int]))
  (doseq [post all-pages]
    (sql/insert! db-conn :wp_posts (to-record post)))
  (doseq [url urls]
    (sql/insert! db-conn :wp_urls url))
  (doseq [term terms]
    (sql/insert! db-conn :wp_terms term))
  (doseq [tr term-relationships]
    (sql/insert! db-conn :wp_term_relationships tr))
  (doseq [tt term-taxonomy]
    (sql/insert! db-conn :wp_term_taxonomy tt)))

(defn- read-all-from-db []
  (sql/with-db-connection [db-conn db]
    (set-up-db db-conn)
    (read-db db-conn)))

(describe "read-db"
  (it "reads all posts in database table"
    (should= (count all-pages) (count (read-all-from-db))))
  (it "restores expected record structure"
    (let [post (first (read-all-from-db))]
      (should= (:id post-a) (:id post))
      (should= (:post-date-gmt post-a) (:post-date-gmt post))
      (should= (:post-content post-a) (:post-content post))))
  (it "associates posts with urls"
    (should= "/blog/test1/" (:post-url (first (read-all-from-db)))))
  (it "associates posts with terms"
    (should= ["dev" "test"] (:post-terms (first (read-all-from-db))))))

(describe "filename"
  (it "uses the last portion of the wordpress url as the filename"
    (should= "testing" (filename later-post)))
  (it "uses the id if no url is found"
    (should= "102" (filename post-a))))

(defn- import-all []
  (sql/with-db-connection [db-conn db]
    (set-up-db db-conn)
    (do-import test-fs db-conn)))

(defn- file-exists [path-str]
  (Files/exists (get-path test-fs path-str)
                (into-array LinkOption [])))

(describe "do-import"
  (before
    (create-file-system)
    (import-all))
  (it "imports all posts"
    (should= true (file-exists "test1.pina")))
  (it "imports the latest revision"
    (should-contain "\nUpdated" (content (get-path test-fs "test2.pina"))))
  (it "imports multi-line entries"
    (should-contain "One\nTwo\nThree\n" (content (get-path test-fs "multi.pina")))))
