(ns com.hjsoft.slideshow.dev-server-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [com.hjsoft.slideshow.dev-server :as dev-server])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- delete-tree!
  "Recursively delete a temporary directory."
  [^java.io.File f]
  (when (.isDirectory f)
    (doseq [child (.listFiles f)]
      (delete-tree! child)))
  (.delete f))

(defn- make-temp-root!
  "Create a temporary public root with sample photos and index.html."
  []
  (let [root (.toFile (Files/createTempDirectory
                       "slideshow-dev-test"
                       (make-array FileAttribute 0)))
        photos (io/file root "photos")
        sub (io/file photos "sub")]
    (.mkdirs sub)
    (spit (io/file root "index.html") "<html><body>App</body></html>")
    (spit (io/file photos "cat 1.jpg") "jpg")
    (spit (io/file photos "a&b.png") "png")
    (spit (io/file photos ".hidden") "secret")
    root))

(deftest directory-index-listing-test
  (let [root (make-temp-root!)]
    (try
      (let [root-path (.getAbsolutePath root)]
        (testing "renders Apache-style HTML listing for directory"
          (let [resp (dev-server/handle {:uri "/photos/"
                                         :http-roots [root-path]
                                         :http-config {}})
                headers (:headers resp)
                body (:body resp)]
            (is (= 200 (:status resp)))
            (is (str/starts-with? (get headers "content-type" "")
                                  "text/html"))
            (is (= "no-store" (get headers "cache-control")))
            (is (str/includes? body "<a href=\"../\">"))
            (is (str/includes? body "<a href=\"sub/\">"))
            (is (str/includes? body "<a href=\"cat%201.jpg\">"))
            (is (str/includes? body "<a href=\"a%26b.png\">"))
            (is (str/includes? body "a&amp;b.png"))
            (is (not (str/includes? body ".hidden")))))
        (testing "redirects directory path missing trailing slash"
          (let [resp (dev-server/handle {:uri "/photos"
                                         :http-roots [root-path]
                                         :http-config {}})]
            (is (= 301 (:status resp)))
            (is (= "/photos/" (get-in resp [:headers "location"])))))
        (testing "delegates to push-state when index.html exists"
          (let [resp (dev-server/handle {:uri "/"
                                         :headers {"accept" "text/html"}
                                         :http-roots [root-path]
                                         :http-config {}})]
            (is (= 200 (:status resp)))
            (is (str/includes? (:body resp) "App"))))
        (testing "blocks path traversal outside http root"
          (let [resp (dev-server/handle {:uri "/../"
                                         :headers {"accept" "text/html"}
                                         :http-roots [root-path]
                                         :http-config {}})]
            (is (not (str/includes? (:body resp) "Index of"))))))
      (finally
        (delete-tree! root)))))
