(ns com.hjsoft.slideshow.core-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.hjsoft.slideshow.core :as core]))

(deftest resolve-base-path-test
  (testing "normalizes pathnames to trailing-slash base paths"
    (is (= "/" (core/resolve-base-path nil)))
    (is (= "/" (core/resolve-base-path "")))
    (is (= "/" (core/resolve-base-path "/index.html")))
    (is (= "/app/" (core/resolve-base-path "/app/")))
    (is (= "/app/" (core/resolve-base-path "/app/index.html")))))

(deftest config-url-roundtrip-test
  (testing "exposes expected default config values"
    (is (= "images/" (:index core/default-config)))
    (is (= 1000 (:fade core/default-config))))
  (testing "encodes and parses slideshow configuration parameters"
    (let [cfg {:index "../photos/my pics/"
               :delay 8
               :refresh 120
               :fade 750
               :order "shuffle"
               :fit "cover"}
          qs (core/encode-config cfg)]
      (is (str/starts-with? qs "?"))
      (is (str/includes? qs "index=..%2Fphotos%2Fmy%20pics%2F"))
      (is (str/includes? qs "fade=750"))
      (is (= {:mode :slideshow :config cfg}
             (core/parse-location-search qs)))))
  (testing "defaults to config mode when index is missing or edit=1"
    (is (= {:mode :config :config core/default-config}
           (core/parse-location-search nil)))
    (is (= {:mode :config :config core/default-config}
           (core/parse-location-search "")))
    (is (= {:mode :config
            :config (assoc core/default-config :delay 10)}
           (core/parse-location-search "?delay=10")))
    (is (= {:mode :config
            :config {:index "images/"
                     :delay 7
                     :refresh 60
                     :fade 1000
                     :order "sequential"
                     :fit "contain"}}
           (core/parse-location-search
            "?edit=1&index=images%2F&delay=7"))))
  (testing "preserves 0ms fade and falls back on invalid values"
    (is (= {:mode :slideshow
            :config {:index "pics/"
                     :delay 5
                     :refresh 60
                     :fade 0
                     :order "sequential"
                     :fit "contain"}}
           (core/parse-location-search "?index=pics%2F&fade=0")))
    (is (= {:mode :slideshow
            :config {:index "pics/"
                     :delay 5
                     :refresh 60
                     :fade 1000
                     :order "sequential"
                     :fit "contain"}}
           (core/parse-location-search
            (str "?index=pics%2F&delay=-2&refresh=2"
                 "&fade=-5&order=bogus&fit=none"))))
    (is (= 1000
           (get-in (core/parse-location-search
                    "?index=pics%2F&fade=abc")
                   [:config :fade])))))

(deftest validate-config-test
  (testing "validates and normalizes configuration values"
    (is (= {:valid? true
            :config {:index "images/"
                     :delay 10
                     :refresh 45
                     :fade 600
                     :order "shuffle"
                     :fit "cover"}}
           (core/validate-config {:index "  images/  "
                                  :delay "10"
                                  :refresh "45"
                                  :fade "600"
                                  :order "shuffle"
                                  :fit "cover"})))
    (is (= 0
           (get-in (core/validate-config {:index "images/"
                                          :delay "5"
                                          :refresh "60"
                                          :fade "0"
                                          :order "sequential"
                                          :fit "contain"})
                   [:config :fade]))))
  (testing "rejects blank index and invalid numbers"
    (let [res (core/validate-config {:index "   "
                                     :delay "0"
                                     :refresh "4"
                                     :fade "-1"
                                     :order "sequential"
                                     :fit "contain"})]
      (is (false? (:valid? res)))
      (is (contains? (:errors res) :index))
      (is (contains? (:errors res) :delay))
      (is (contains? (:errors res) :refresh))
      (is (contains? (:errors res) :fade)))
    (let [res2 (core/validate-config {:index "pics/"
                                      :delay "-3"
                                      :refresh "xyz"
                                      :fade "abc"
                                      :order "sequential"
                                      :fit "contain"})]
      (is (false? (:valid? res2)))
      (is (contains? (:errors res2) :delay))
      (is (contains? (:errors res2) :refresh))
      (is (contains? (:errors res2) :fade)))
    (let [res3 (core/validate-config {:index "pics/"
                                      :delay "abc"
                                      :refresh "0"
                                      :fade "100"
                                      :order "sequential"
                                      :fit "contain"})]
      (is (false? (:valid? res3)))
      (is (contains? (:errors res3) :delay))
      (is (contains? (:errors res3) :refresh)))))

(deftest parse-image-index-test
  (testing "parses Apache mod_autoindex HTML and deduplicates images"
    (let [html (str "<!DOCTYPE HTML><html><body>"
                    "<img src=\"/icons/image2.gif\" alt=\"[IMG]\">"
                    "<a href=\"?C=N;O=D\">Name</a>"
                    "<a href=\"/\">Parent Directory</a>"
                    "<a href=\"subdir/\">subdir/</a>"
                    "<a href=\"notes.txt\">notes.txt</a>"
                    "<!-- <a href=\"hidden.jpg\">hidden.jpg</a> -->"
                    "<a href=\"cat.jpg\">cat.jpg</a>"
                    "<a href=\"DOG.PNG\">DOG.PNG</a>"
                    "<a href=\"cat.jpg\">cat.jpg</a>"
                    "<a href=\"my%20pic.webp\">my pic.webp</a>"
                    "<a href=\"a&amp;b.gif\">a&amp;b.gif</a>"
                    "</body></html>")
          base "http://localhost:3000/images/"]
      (is (= ["http://localhost:3000/images/cat.jpg"
              "http://localhost:3000/images/DOG.PNG"
              "http://localhost:3000/images/my%20pic.webp"
              "http://localhost:3000/images/a&b.gif"]
             (core/parse-image-index html base)))))
  (testing "parses Nginx autoindex and Python http.server HTML"
    (let [nginx (str "<html><body><h1>Index of /pics/</h1><hr><pre>"
                     "<a href=\"../\">../</a>\n"
                     "<a href=\"alpha.jpeg\">alpha.jpeg</a>\n"
                     "<a href=\"readme.md\">readme.md</a>\n"
                     "</pre><hr></body></html>")
          python (str "<ul><li><a href=\"../\">../</a></li>"
                      "<li><a href=\"beta.avif\">beta.avif</a></li>"
                      "<li><a href=\"sub/\">sub/</a></li></ul>")]
      (is (= ["http://localhost/pics/alpha.jpeg"]
             (core/parse-image-index nginx "http://localhost/pics/")))
      (is (= ["http://localhost/pics/beta.avif"]
             (core/parse-image-index python "http://localhost/pics/")))))
  (testing "resolves relative and absolute index base URLs"
    (let [page "http://localhost:3000/slideshow/"
          html "<a href=\"a.jpg\">a.jpg</a>"]
      (is (= ["http://localhost:3000/slideshow/images/a.jpg"]
             (core/parse-image-index
              html
              (core/normalize-index-base-url "images/" page))))
      (is (= ["http://localhost:3000/slideshow/images/a.jpg"]
             (core/parse-image-index
              html
              (core/normalize-index-base-url "images" page))))
      (is (= ["http://localhost:3000/photos/a.jpg"]
             (core/parse-image-index
              html
              (core/normalize-index-base-url "../photos/" page))))
      (is (= ["http://localhost:3000/gallery/a.jpg"]
             (core/parse-image-index
              html
              (core/normalize-index-base-url "/gallery/index.html"
                                             page))))))
  (testing "parses JSON directory and array indexes"
    (let [nginx-json (str "[{\"name\":\"sub\",\"type\":\"directory\"},"
                          "{\"name\":\"one.jpg\",\"type\":\"file\"},"
                          "{\"name\":\"readme.md\",\"type\":\"file\"}]")
          arr-json "[\"a.png\",\"b.avif\"]"
          base "http://localhost:3000/images/"]
      (is (= ["http://localhost:3000/images/one.jpg"]
             (core/parse-image-index nginx-json base)))
      (is (= ["http://localhost:3000/images/a.png"
              "http://localhost:3000/images/b.avif"]
             (core/parse-image-index arr-json base))))))

(deftest merge-image-list-test
  (testing "preserves current image in sequential mode on refresh"
    (is (= {:images ["a.jpg" "b.jpg" "c.jpg"] :index 1}
           (core/merge-image-list
            ["a.jpg" "b.jpg"] 1
            ["a.jpg" "b.jpg" "c.jpg"] "sequential")))
    (is (= {:images ["b.jpg" "c.jpg"] :index 0}
           (core/merge-image-list
            ["a.jpg" "b.jpg"] 1
            ["b.jpg" "c.jpg"] "sequential")))
    (is (= {:images ["a.jpg" "c.jpg"] :index 1}
           (core/merge-image-list
            ["a.jpg" "b.jpg"] 1
            ["a.jpg" "c.jpg"] "sequential")))
    (is (= {:images [] :index 0}
           (core/merge-image-list
            ["a.jpg" "b.jpg"] 1 [] "sequential"))))
  (testing "handles shuffle mode with deterministic rand-int-fn"
    (let [zero-rand (fn [_] 0)
          init (core/merge-image-list
                [] 0 ["a.jpg" "b.jpg" "c.jpg"] "shuffle" zero-rand)
          updated (core/merge-image-list
                   (:images init) 1
                   ["b.jpg" "c.jpg" "d.jpg"] "shuffle" zero-rand)
          kept-url (nth (:images init) 1)]
      (is (= 3 (count (:images init))))
      (is (= 0 (:index init)))
      (is (= #{"b.jpg" "c.jpg" "d.jpg"} (set (:images updated))))
      (if (contains? #{"b.jpg" "c.jpg"} kept-url)
        (is (= kept-url (nth (:images updated) (:index updated))))
        (is (< (:index updated) (count (:images updated)))))))
  (testing "reshuffles on refresh in shuffle mode even with same images"
    (let [zero-rand (fn [_] 0)
          same (core/merge-image-list
                ["d.jpg" "a.jpg" "c.jpg" "b.jpg"] 2
                ["a.jpg" "b.jpg" "c.jpg" "d.jpg"] "shuffle" zero-rand)
          with-new (core/merge-image-list
                    ["a.jpg" "b.jpg" "c.jpg"] 1
                    ["a.jpg" "b.jpg" "c.jpg" "d.jpg"]
                    "shuffle" zero-rand)]
      (is (= ["b.jpg" "c.jpg" "d.jpg" "a.jpg"] (:images same)))
      (is (= "c.jpg" (nth (:images same) (:index same))))
      (is (= ["b.jpg" "c.jpg" "d.jpg" "a.jpg"] (:images with-new)))
      (is (= 0 (:index with-new))))))

(deftest stepping-and-filename-test
  (testing "wraps next and prev indices cleanly"
    (is (= 0 (core/next-index 0 0)))
    (is (= 1 (core/next-index 0 3)))
    (is (= 0 (core/next-index 2 3)))
    (is (= 0 (core/prev-index 0 0)))
    (is (= 2 (core/prev-index 0 3)))
    (is (= 1 (core/prev-index 2 3))))
  (testing "extracts decoded filename from image URL"
    (is (= "my cat.jpg"
           (core/filename-from-url
            "http://localhost/photos/my%20cat.jpg")))))

(deftest preload-window-test
  (testing "returns deduplicated window of image URLs to pre-cache"
    (is (= [] (core/preload-window [] 0 3)))
    (is (= ["b" "c" "d" "a"]
           (core/preload-window ["a" "b" "c" "d" "e"] 1 2)))
    (is (= ["a" "b"]
           (core/preload-window ["a" "b"] 0 3)))
    (is (= ["a"]
           (core/preload-window ["a"] 0 3)))))
