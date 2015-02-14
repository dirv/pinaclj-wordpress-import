(ns pinaclj-wordpress-import.core
  (:require [clojure.java.jdbc :as sql]
            [clj-time.coerce :as tc])
  (:import (java.nio.file FileSystems Files LinkOption StandardOpenOption OpenOption)
           (java.nio.file.attribute FileAttribute)
           (java.io BufferedReader)))

(def target-dir "./pages/")

(defn latest-post [posts]
  (first (reverse (sort-by :post-date-gmt posts))))

(defn post-id [post]
  (if (= "revision" (:post-type post))
    (:post-parent post)
  (:id post)))

(defn latest-posts [posts]
  (map #(latest-post (val %)) (group-by post-id posts)))

(defn to-published-at [post]
  (when-not (nil? (:post-url post))
    (str "published-at: " (:post-date-gmt post) "\n")))

(defn to-title [post]
  (str "title: " (:post-title post) "\n"))

(defn- to-url [post]
  (when-not (nil? (:post-url post))
    (str "url: " (:post-url post) "\n")))

(defn- to-tags [post]
  (when (seq (:post-terms post))
        (str "tags: " (clojure.string/join ", " (:post-terms post)) "\n")))

(defn to-page [post]
  (str (to-published-at post)
       (to-title post)
       (to-url post)
       (to-tags post)
       "---\n"
       (:post-content post)
       "\n"))

(defn get-path [fs path-str]
  (.getPath fs (str target-dir path-str) (into-array String [])))

(defn get-page-path [fs id]
  (get-path fs (str id ".pina")))

(defn- create-file [path content]
  (Files/write path
               (.getBytes content)
               (into-array OpenOption [StandardOpenOption/CREATE])))

(defn write-page [fs id page]
  (create-file (get-page-path fs id) page))

(defn assoc-maps [post urls terms]
  (assoc post
         :post-url (get urls (post-id post))
         :post-terms (get terms (post-id post))))

(defn- url-map-entry [url-record]
  {(:object_id url-record) (:url url-record)})

(defn url-map [url-records]
  (into {} (map url-map-entry url-records)))

(defn- term-map-entry [term-record]
  {(:object_id term-record) (:name term-record)})

(defn term-map [term-records]
  (reduce
    (fn [term-map {object_id :object_id term :name}]
        (cond
          (= "Uncategorized" term) term-map)
          (contains? term-map object_id)
            (assoc term-map object_id (conj (get term-map object_id) term))
        :else
            (assoc term-map object_id [term]))
    {}
    term-records))

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
   :post-terms "select tr.object_id, t.name from wp_term_relationships tr join wp_terms t on tr.term_taxonomy_id = t.term_id"
   :post-urls "select object_id, url from wp_urls where object_type='post'" })

(defn- query [id db-conn row-fn]
  (sql/query db-conn [(get queries id)] :row-fn row-fn))

(defn read-db [db-conn]
  (let [url-map (url-map (query :post-urls db-conn identity))
        term-map (term-map (query :post-terms db-conn identity))]
    (map #(assoc-maps % url-map term-map) (query :all-posts db-conn to-post))))

(defn- trim-slash [post-url]
  (subs post-url 0 (dec (count post-url))))

(defn filename [post]
  (if (nil? (:post-url post))
    (str (:id post))
    (let [post-url (trim-slash (:post-url post))]
      (subs post-url (+ (.lastIndexOf post-url "/") 1)))))

(defn create-target-directory [fs]
  (Files/createDirectories (get-path fs "") (into-array FileAttribute [])))

(defn do-import [fs db-conn]
  (create-target-directory fs)
  (doseq [post (latest-posts (read-db db-conn))]
    (write-page fs (filename post) (to-page post))))

