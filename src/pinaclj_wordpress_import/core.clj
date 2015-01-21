(ns pinaclj-wordpress-import.core
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

(defn to-page [post]
  (str (to-published-at post) "\n"
       (to-title post) "\n"
       "\n"
       (:post-content post)
       "\n"))

(defn get-path [fs path-str]
  (.getPath fs path-str (into-array String [])))

(defn get-page-path [fs id]
  (get-path fs (str id ".pina")))

(defn- as-bytes [st]
  (bytes (byte-array (map byte st))))

(defn- create-file [path content]
  (Files/write path
               (as-bytes content)
               (into-array OpenOption [StandardOpenOption/CREATE])))

(defn write-page [fs id page]
  (create-file (get-page-path fs id) page))