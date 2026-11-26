(ns com.hjsoft.slideshow.dev-server
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [shadow.http.push-state :as push-state])
  (:import [java.io File]
           [java.net URI URLEncoder]))

(defn escape-html
  "Escape HTML special characters in text."
  [s]
  (-> (or s "")
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#39;")))

(defn encode-segment
  "URL-encode a single path segment using %20 for spaces."
  [s]
  (str/replace (URLEncoder/encode (or s "") "UTF-8") "+" "%20"))

(defn decode-uri-path
  "Safely extract and decode the path component from a request URI."
  [uri]
  (try
    (or (.getPath (URI. (or uri "/"))) "/")
    (catch Exception _
      (or uri "/"))))

(defn resolve-dir
  "Resolve uri to a directory under http-roots without index-name."
  [roots uri index-name]
  (let [decoded (decode-uri-path uri)
        rel-path (str/replace decoded #"^/+" "")]
    (when-not (str/blank? rel-path)
      (some
       (fn [root]
         (when-not (str/starts-with? root "classpath:")
           (let [root-dir (.getCanonicalFile (io/file root))
                 candidate (.getCanonicalFile (io/file root-dir rel-path))
                 prefix (str (.getPath root-dir) File/separator)]
             (when (and (str/starts-with? (.getPath candidate) prefix)
                        (.isDirectory candidate)
                        (not (.exists (io/file candidate index-name))))
               candidate))))
       roots))))

(defn render-index-html
  "Render an Apache-style HTML directory listing for dir."
  [uri-path ^File dir]
  (let [entries (->> (or (.listFiles dir) (make-array File 0))
                     (remove (fn [^File f]
                               (str/starts-with? (.getName f) ".")))
                     (sort-by (fn [^File f]
                                [(not (.isDirectory f))
                                 (str/lower-case (.getName f))])))
        safe-title (escape-html uri-path)
        links (map (fn [^File f]
                     (let [name-str (.getName f)
                           suffix (if (.isDirectory f) "/" "")
                           href (str (encode-segment name-str) suffix)
                           label (str (escape-html name-str) suffix)]
                       (str "<a href=\"" href "\">" label "</a>\n")))
                   entries)]
    (str "<!DOCTYPE HTML>\n"
         "<html>\n<head>\n<meta charset=\"utf-8\">\n"
         "<title>Index of " safe-title "</title>\n"
         "</head>\n<body>\n"
         "<h1>Index of " safe-title "</h1>\n"
         "<hr><pre>\n"
         "<a href=\"../\">Parent Directory</a>\n"
         (apply str links)
         "</pre><hr>\n</body>\n</html>\n")))

(defn handle
  "Serve Apache-style directory listings, else delegate to push-state."
  [{:keys [uri query-string http-roots http-config] :as req}]
  (let [index-name (get http-config :push-state/index "index.html")
        dir (resolve-dir http-roots uri index-name)]
    (if dir
      (if-not (str/ends-with? (or uri "") "/")
        (let [qs (when-not (str/blank? query-string)
                   (str "?" query-string))]
          {:status 301
           :headers {"location" (str uri "/" qs)}
           :body ""})
        {:status 200
         :headers {"content-type" "text/html; charset=utf-8"
                   "cache-control" "no-store"}
         :body (render-index-html (decode-uri-path uri) dir)})
      (push-state/handle req))))
