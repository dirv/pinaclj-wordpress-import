(ns pinaclj-wordpress-import.core
  (:require [clojure.java.jdbc :as sql]
            [clj-time.coerce :as tc])
  (:import (java.nio.file FileSystems Files LinkOption StandardOpenOption OpenOption)))

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

(defn get-page-url [post urls]
  (:url (first (filter #(= (:object_id %) (post-id post)) urls))))

(defn- as-bytes [st]
  (bytes (byte-array (map byte st))))

(defn- create-file [path content]
  (Files/write path
               (as-bytes content)
               (into-array OpenOption [StandardOpenOption/CREATE])))

(defn write-page [fs id page]
  (create-file (get-page-path fs id) page))

(defn assoc-url [post urls]
  (assoc post :post-url (get-page-url post urls)))

(defn- to-post [record]
  {:id (:id record)
   :post-date-gmt (tc/from-sql-time (:post_date_gmt record))
   :post-title (:post_title record)
   :post-content (:post_content record)
   :post-status (:post_status record)
   :post-type (:post_type record)})

(defn read-db [db-conn]
  (let [wp_posts (sql/query db-conn ["select * from wp_posts"])
        wp_urls (sql/query db-conn ["select object_id, url, object_type from wp_urls where object_type='post';"])]
    (map #(assoc-url (to-post %) wp_urls) wp_posts)))

(defn do-import [fs db-conn]
  (doseq [post (read-db db-conn)]
    (write-page fs (:id post) (to-page post))))

