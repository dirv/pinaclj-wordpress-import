(ns pinaclj-wordpress-import.core-spec
  (:require [speclj.core :refer :all]
            [clj-time.core :as t]
            [pinaclj-wordpress-import.core :refer :all]))

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

