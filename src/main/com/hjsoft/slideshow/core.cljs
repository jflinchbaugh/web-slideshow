(ns com.hjsoft.slideshow.core
  (:require [clojure.string :as str]))

(defn resolve-base-path
  "Normalize a window.location pathname to a trailing-slash base path."
  [pathname]
  (if (str/blank? pathname)
    "/"
    (if (str/ends-with? pathname "/")
      pathname
      (let [idx (str/last-index-of pathname "/")]
        (if idx
          (subs pathname 0 (inc idx))
          "/")))))

(def default-config
  {:index "images/"
   :delay 5
   :refresh 60
   :fade 1000
   :order "sequential"
   :fit "contain"})

(def valid-orders #{"sequential" "shuffle"})
(def valid-fits #{"contain" "cover"})

(def image-extensions
  #{"jpg" "jpeg" "png" "gif" "webp" "avif" "svg" "bmp" "jfif"})

(defn parse-positive-int
  "Parse v as an integer >= min-val, or return nil if invalid."
  [v min-val]
  (cond
    (int? v)
    (when (>= v min-val) v)

    (string? v)
    (let [trimmed (str/trim v)]
      (when (re-matches #"^\d+$" trimmed)
        (let [n (js/parseInt trimmed 10)]
          (when (and (not (js/isNaN n)) (>= n min-val))
            n))))

    :else nil))

(defn- encode-pairs
  "Encode key-value pairs into a query string without leading ?."
  [pairs]
  (str/join
   "&"
   (map (fn [[k v]]
          (str k "=" (js/encodeURIComponent (str v))))
        pairs)))

(defn- config-pairs
  "Return ordered query parameter pairs for a configuration map."
  [cfg]
  (let [merged (merge default-config cfg)]
    [["index" (str/trim (or (:index merged) ""))]
     ["delay" (:delay merged)]
     ["refresh" (:refresh merged)]
     ["fade" (:fade merged)]
     ["order" (:order merged)]
     ["fit" (:fit merged)]]))

(defn encode-config
  "Encode slideshow configuration as a URL query string."
  [cfg]
  (str "?" (encode-pairs (config-pairs cfg))))

(defn encode-edit-config
  "Encode slideshow configuration with edit=1 flag for config view."
  [cfg]
  (str "?" (encode-pairs (cons ["edit" "1"] (config-pairs cfg)))))

(defn- decode-component
  "Safely URL-decode a query parameter or path component."
  [s]
  (try
    (js/decodeURIComponent (str/replace (or s "") "+" " "))
    (catch :default _
      (or s ""))))

(defn parse-query-string
  "Parse a URL search string into a map of string keys to values."
  [search]
  (if (str/blank? search)
    {}
    (let [raw (if (str/starts-with? search "?")
                (subs search 1)
                search)]
      (if (str/blank? raw)
        {}
        (reduce
         (fn [acc part]
           (if (str/blank? part)
             acc
             (let [[k v] (str/split part #"=" 2)]
               (assoc acc (decode-component k) (decode-component v)))))
         {}
         (str/split raw #"&"))))))

(defn parse-location-search
  "Parse window.location.search into {:mode ... :config ...}."
  [search]
  (let [params (parse-query-string search)
        raw-idx (str/trim (or (get params "index") ""))
        idx (if (str/blank? raw-idx)
              (:index default-config)
              raw-idx)
        edit? (= "1" (get params "edit"))
        delay (or (parse-positive-int (get params "delay") 1)
                  (:delay default-config))
        refresh (or (parse-positive-int (get params "refresh") 5)
                    (:refresh default-config))
        fade (or (parse-positive-int (get params "fade") 0)
                 (:fade default-config))
        order-raw (get params "order")
        order (if (contains? valid-orders order-raw)
                order-raw
                (:order default-config))
        fit-raw (get params "fit")
        fit (if (contains? valid-fits fit-raw)
              fit-raw
              (:fit default-config))
        cfg {:index idx
             :delay delay
             :refresh refresh
             :fade fade
             :order order
             :fit fit}
        mode (if (and (not (str/blank? raw-idx)) (not edit?))
               :slideshow
               :config)]
    {:mode mode :config cfg}))

(defn validate-config
  "Validate raw form config fields, returning {:valid? ...}."
  [raw-cfg]
  (let [idx (str/trim (or (:index raw-cfg) ""))
        delay (parse-positive-int (:delay raw-cfg) 1)
        refresh (parse-positive-int (:refresh raw-cfg) 5)
        fade (parse-positive-int (:fade raw-cfg) 0)
        order (or (:order raw-cfg) (:order default-config))
        fit (or (:fit raw-cfg) (:fit default-config))
        errors (cond-> {}
                 (str/blank? idx)
                 (assoc :index "Image index URL is required")

                 (nil? delay)
                 (assoc :delay "Delay must be an integer >= 1")

                 (nil? refresh)
                 (assoc :refresh "Refresh interval must be >= 5")

                 (nil? fade)
                 (assoc :fade "Fade time must be an integer >= 0")

                 (not (contains? valid-orders order))
                 (assoc :order "Invalid playback order")

                 (not (contains? valid-fits fit))
                 (assoc :fit "Invalid image fit"))]
    (if (empty? errors)
      {:valid? true
       :config {:index idx
                :delay delay
                :refresh refresh
                :fade fade
                :order order
                :fit fit}}
      {:valid? false :errors errors})))

(defn normalize-index-base-url
  "Resolve index-url against page-href and ensure directory slash."
  [index-url page-href]
  (let [u (js/URL. index-url page-href)
        path (.-pathname u)]
    (when-not (str/ends-with? path "/")
      (let [last-slash (str/last-index-of path "/")
            seg (if last-slash (subs path (inc last-slash)) path)]
        (when-not (str/includes? seg ".")
          (set! (.-pathname u) (str path "/")))))
    (.-href u)))

(defn image-href?
  "Return true when href points to a supported image file."
  [href]
  (when-not (str/blank? href)
    (let [trimmed (str/trim href)
          lower (str/lower-case trimmed)]
      (when-not (or (str/starts-with? trimmed "#")
                    (str/starts-with? trimmed "?")
                    (str/starts-with? lower "mailto:")
                    (str/starts-with? lower "javascript:")
                    (str/starts-with? lower "data:"))
        (let [clean (-> trimmed
                        (str/replace #"#.*$" "")
                        (str/replace #"\?.*$" ""))]
          (when-not (or (str/blank? clean) (str/ends-with? clean "/"))
            (let [slash-idx (str/last-index-of clean "/")
                  seg (if slash-idx (subs clean (inc slash-idx)) clean)
                  dot-idx (str/last-index-of seg ".")]
              (when (and dot-idx (< (inc dot-idx) (count seg)))
                (let [ext (str/lower-case (subs seg (inc dot-idx)))]
                  (contains? image-extensions ext))))))))))

(defn decode-html-entities
  "Decode common HTML character entities found in href attributes."
  [s]
  (-> (or s "")
      (str/replace "&amp;" "&")
      (str/replace "&lt;" "<")
      (str/replace "&gt;" ">")
      (str/replace "&quot;" "\"")
      (str/replace "&#39;" "'")
      (str/replace "&#x27;" "'")))

(defn extract-html-hrefs
  "Extract href values from anchor tags in HTML, ignoring comments."
  [html]
  (let [no-comments (str/replace (or html "") #"<!--[\s\S]*?-->" "")
        pattern (re-pattern
                 (str "(?i)<a\\b[^>]*\\bhref\\s*=\\s*"
                      "(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"))]
    (map (fn [m]
           (decode-html-entities (or (nth m 1) (nth m 2) (nth m 3))))
         (re-seq pattern no-comments))))

(defn- extract-json-entry-href
  "Extract candidate href from a JSON array item."
  [item]
  (cond
    (string? item)
    item

    (and (object? item)
         (not= "directory" (aget item "type")))
    (or (aget item "href")
        (aget item "url")
        (aget item "src")
        (aget item "name"))

    :else nil))

(defn extract-json-hrefs
  "Extract candidate hrefs from JSON index text, or nil if invalid."
  [text]
  (try
    (let [parsed (js/JSON.parse text)
          items (cond
                  (js/Array.isArray parsed) parsed
                  (and (object? parsed)
                       (js/Array.isArray (aget parsed "images")))
                  (aget parsed "images")
                  (and (object? parsed)
                       (js/Array.isArray (aget parsed "files")))
                  (aget parsed "files")
                  :else nil)]
      (when items
        (keep extract-json-entry-href (array-seq items))))
    (catch :default _
      nil)))

(defn- resolve-url-safe
  "Resolve href against base-url, returning nil on invalid URLs."
  [href base-url]
  (try
    (.-href (js/URL. href base-url))
    (catch :default _
      nil)))

(defn parse-image-index
  "Parse HTML or JSON directory index into deduplicated image URLs."
  [body base-url]
  (let [trimmed (str/trim (or body ""))
        json-hrefs (when (or (str/starts-with? trimmed "[")
                             (str/starts-with? trimmed "{"))
                     (extract-json-hrefs trimmed))
        candidates (or json-hrefs (extract-html-hrefs body))]
    (:urls
     (reduce
      (fn [{:keys [seen urls] :as acc} href]
        (if-not (image-href? href)
          acc
          (if-let [resolved (resolve-url-safe href base-url)]
            (if (contains? seen resolved)
              acc
              {:seen (conj seen resolved)
               :urls (conj urls resolved)})
            acc)))
      {:seen #{} :urls []}
      candidates))))

(defn filename-from-url
  "Extract the decoded filename segment from an image URL."
  [url]
  (let [path (try
               (.-pathname (js/URL. url))
               (catch :default _
                 (-> (or url "")
                     (str/replace #"#.*$" "")
                     (str/replace #"\?.*$" ""))))
        idx (str/last-index-of path "/")
        seg (if idx (subs path (inc idx)) path)]
    (decode-component seg)))

(defn shuffle-coll
  "Return a shuffled vector of coll using rand-int-fn."
  ([coll]
   (shuffle-coll coll rand-int))
  ([coll rand-int-fn]
   (let [v (vec coll)
         n (count v)]
     (loop [i (dec n)
            acc v]
       (if (<= i 0)
         acc
         (let [j (rand-int-fn (inc i))
               vi (nth acc i)
               vj (nth acc j)]
           (recur (dec i)
                  (-> acc
                      (assoc i vj)
                      (assoc j vi)))))))))

(defn- find-index
  "Return index of target in coll, or nil."
  [coll target]
  (when target
    (first (keep-indexed (fn [i item]
                           (when (= item target) i))
                         coll))))

(defn merge-image-list
  "Merge newly fetched image URLs while preserving the active slide."
  ([current-images current-idx fetched-images order]
   (merge-image-list current-images current-idx fetched-images
                     order rand-int))
  ([current-images current-idx fetched-images order rand-int-fn]
   (if (empty? fetched-images)
     {:images [] :index 0}
     (let [current-url (get current-images current-idx)
           fetched-vec (vec fetched-images)
           next-images (if (= order "shuffle")
                         (shuffle-coll fetched-vec rand-int-fn)
                         fetched-vec)
           found-idx (find-index next-images current-url)
           next-idx (if (empty? current-images)
                      0
                      (or found-idx
                          (min (max 0 current-idx)
                               (dec (count next-images)))))]
       {:images next-images :index next-idx}))))

(defn next-index
  "Advance slide index with wrap-around."
  [idx total]
  (if (<= total 0)
    0
    (mod (inc idx) total)))

(defn prev-index
  "Step slide index backward with wrap-around."
  [idx total]
  (if (<= total 0)
    0
    (mod (dec idx) total)))

(defn preload-window
  "Return deduplicated image URLs to pre-cache around idx."
  ([images idx]
   (preload-window images idx 3))
  ([images idx ahead]
   (let [total (count images)]
     (if (zero? total)
       []
       (let [offsets (concat (range 0 (inc ahead)) [-1])]
         (:urls
          (reduce
           (fn [{:keys [seen urls] :as acc} offset]
             (let [url (nth images (mod (+ idx offset) total))]
               (if (contains? seen url)
                 acc
                 {:seen (conj seen url)
                  :urls (conj urls url)})))
           {:seen #{} :urls []}
           offsets)))))))
