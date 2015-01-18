(ns pinaclj-wordpress-import.core)

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


