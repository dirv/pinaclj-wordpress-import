(ns pinaclj-wordpress-import.core
  (:require [clojure.java.jdbc :as sql]
            [clj-time.coerce :as tc])
  (:import (java.nio.file FileSystems Files LinkOption StandardOpenOption OpenOption)
           (java.io BufferedReader)))

(defn latest-post [posts]
  (first (reverse (sort-by :post-date-gmt posts))))

(defn post-id [post]
  (if (= "revision" (:post-type post))
    (:post-parent post)
  (:id post)))

(defn latest-posts [posts]
  (map #(latest-post (val %)) (group-by post-id posts)))

(defn to-published-at [post]
  (str "published-at: " (:post-date-gmt post)))

(defn to-title [post]
  (str "title: " (:post-title post)))

(defn- to-url [post]
  (str "url: " (:post-url post)))

(defn to-page [post]
  (str (to-published-at post) "\n"
       (to-title post) "\n"
       (to-url post) "\n"
       "\n"
       (:post-content post)
       "\n"))

(defn get-path [fs path-str]
  (.getPath fs path-str (into-array String [])))

(defn get-page-path [fs id]
  (get-path fs (str id ".pina")))

(defn- create-file [path content]
  (Files/write path
               (.getBytes content)
               (into-array OpenOption [StandardOpenOption/CREATE])))

(defn write-page [fs id page]
  (create-file (get-page-path fs id) page))

(defn assoc-url [post urls]
  (assoc post :post-url (get urls (post-id post))))

(defn- url-map-entry [url-record]
  {(get url-record :object_id) (get url-record :url)})

(defn url-map [url-records]
  (into {} (map url-map-entry url-records)))

(defn- to-post [record]
  {:id (:id record)
   :post-date-gmt (tc/from-sql-time (:post_date_gmt record))
   :post-title (:post_title record)
   :post-content (:post_content record)
   :post-status (:post_status record)
   :post-parent (:post_parent record)
   :post-type (:post_type record)})

(def queries
  {:all-posts "select * from wp_posts"
   :post-urls "select object_id, url from wp_urls where object_type='post';" })

(defn- query [id db-conn row-fn]
  (sql/query db-conn [(get queries id)] :row-fn row-fn))

(defn read-db [db-conn]
  (let [url-map (url-map (query :post-urls db-conn identity))]
    (vec (map #(assoc-url % url-map) (query :all-posts db-conn to-post)))))

(defn filename [post]
  (if (nil? (:post-url post))
    (str (:id post))
    (subs (:post-url post) (+ (.lastIndexOf (:post-url post) "/") 1))))

(defn do-import [fs db-conn]
  (doseq [post (latest-posts (read-db db-conn))]
    (write-page fs (filename post) (to-page post))))

