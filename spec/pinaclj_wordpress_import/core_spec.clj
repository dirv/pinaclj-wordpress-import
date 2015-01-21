(ns pinaclj-wordpress-import.core-spec
  (:require [speclj.core :refer :all]
            [clj-time.core :as t]
            [pinaclj-wordpress-import.core :refer :all])
  (:import (com.google.common.jimfs Jimfs Configuration)
           (java.nio.file Files LinkOption)
           (java.nio.charset StandardCharsets)))

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
  {:post-date-gmt (t/date-time 2015 1 19) :post-type "revision" :post-parent 102})
(def post-b
  {:id 103 :post-date-gmt (t/date-time 2015 1 18)})
(def post-b-rev
  {:post-date-gmt (t/date-time 2015 1 19) :post-type "revision" :post-parent 103})

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
