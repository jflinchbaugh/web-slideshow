(ns com.hjsoft.slideshow.ui-test
  (:require [cljs.test :refer [async deftest is testing use-fixtures]]
            [clojure.string :as str]
            [helix.core :refer [$]]
            ["jsdom" :refer [JSDOM]]
            [com.hjsoft.slideshow.ui :as ui]))

(defonce ^:private _init-jsdom
  (let [dom (JSDOM. "<!DOCTYPE html><html><body></body></html>"
                    #js {:url "http://localhost/"})]
    (js/goog.object.set js/goog.global "window" (.-window dom))
    (js/goog.object.set js/goog.global "document"
                        (.. dom -window -document))
    (js/Object.defineProperty
     js/goog.global "navigator"
     #js {:value (.. dom -window -navigator)
          :configurable true
          :writable true})))

(use-fixtures :each
  {:before (fn []
             (.replaceState js/window.history nil "" "/")
             (let [rtl (js/require "@testing-library/react")]
               ((.-cleanup rtl))))
   :after (fn []
            (let [rtl (js/require "@testing-library/react")]
              ((.-cleanup rtl))))})

(defn- instant-preload
  "Preload stub that resolves immediately."
  [_]
  (js/Promise.resolve true))

(defn- make-mock-fetch
  "Create a fetch stub serving body-atom HTML responses."
  [body-atom]
  (fn [url _opts]
    (js/Promise.resolve
     #js {:ok true
          :status 200
          :url url
          :text (fn [] (js/Promise.resolve @body-atom))})))

(defn- wait-for
  "Poll predicate-fn until true or timeout."
  [pred-fn done-fn]
  (let [rtl (js/require "@testing-library/react")]
    (-> ((.-waitFor rtl)
         (fn []
           (when-not (pred-fn)
             (throw (js/Error. "Condition not met yet")))))
        (.then (fn [_] (done-fn)))
        (.catch (fn [err]
                  (is (nil? err))
                  (done-fn))))))

(deftest config-form-initial-render-and-validation-test
  (testing "renders defaults and validates blank index URL"
    (let [rtl (js/require "@testing-library/react")
          render (.-render rtl)
          screen (.-screen rtl)
          fire-event (.-fireEvent rtl)
          view (render ($ ui/app-root {:initial-search ""}))]
      (try
        (let [idx-in (.getByLabelText screen #"Image Index URL")
              fade-in (.getByLabelText screen #"Fade Time \(ms\)")]
          (is (= "images/" (.-value idx-in)))
          (is (= "1000" (.-value fade-in)))
          (is (some? (.getByLabelText screen #"Slide Delay")))
          (is (some? (.getByLabelText screen
                                      #"Index Refresh Interval")))
          (is (some? (.getByLabelText screen #"Playback Order")))
          (is (some? (.getByLabelText screen #"Image Fit")))
          (.change fire-event idx-in #js {:target #js {:value ""}})
          (.click fire-event
                  (.getByRole screen "button"
                              #js {:name "Start Slideshow"}))
          (is (some? (.getByText screen
                                 #"Image index URL is required"))))
        (finally
          ((js/goog.object.get view "unmount")))))))

(deftest config-submit-encodes-and-redirects-test
  (testing "encodes config to URL and starts slideshow"
    (async done
      (let [rtl (js/require "@testing-library/react")
            render (.-render rtl)
            screen (.-screen rtl)
            fire-event (.-fireEvent rtl)
            navigated (atom nil)
            body (atom "<a href=\"one.jpg\">one.jpg</a>")
            view (render ($ ui/app-root
                            {:initial-search ""
                             :on-navigate (fn [u] (reset! navigated u))
                             :fetch-fn (make-mock-fetch body)
                             :preload-fn instant-preload}))]
        (.change fire-event
                 (.getByLabelText screen #"Image Index URL")
                 #js {:target #js {:value "images/"}})
        (.change fire-event
                 (.getByLabelText screen #"Slide Delay")
                 #js {:target #js {:value "7"}})
        (.change fire-event
                 (.getByLabelText screen #"Index Refresh Interval")
                 #js {:target #js {:value "30"}})
        (.change fire-event
                 (.getByLabelText screen #"Fade Time \(ms\)")
                 #js {:target #js {:value "400"}})
        (.change fire-event
                 (.getByLabelText screen #"Playback Order")
                 #js {:target #js {:value "shuffle"}})
        (.change fire-event
                 (.getByLabelText screen #"Image Fit")
                 #js {:target #js {:value "cover"}})
        (.click fire-event
                (.getByRole screen "button"
                            #js {:name "Start Slideshow"}))
        (wait-for
         (fn []
           (some? (.queryByRole screen "img")))
         (fn []
           (try
             (is (str/includes? (or @navigated "") "index=images%2F"))
             (is (str/includes? (or @navigated "") "delay=7"))
             (is (str/includes? (or @navigated "") "refresh=30"))
             (is (str/includes? (or @navigated "") "fade=400"))
             (is (str/includes? (or @navigated "") "order=shuffle"))
             (is (str/includes? (or @navigated "") "fit=cover"))
             (let [img (.getByRole screen "img")]
               (is (= "http://localhost/images/one.jpg"
                      (.getAttribute img "src"))))
             (finally
               ((js/goog.object.get view "unmount"))
               (done)))))))))

(deftest bookmarked-url-starts-slideshow-and-refreshes-test
  (testing "loads bookmarked slideshow, crossfades, refreshes"
    (async done
      (let [rtl (js/require "@testing-library/react")
            render (.-render rtl)
            screen (.-screen rtl)
            fire-event (.-fireEvent rtl)
            body (atom (str "<a href=\"a.jpg\">a.jpg</a>"
                            "<a href=\"b.jpg\">b.jpg</a>"))
            search (str "?index=photos%2F&delay=5&refresh=60"
                        "&fade=250&order=sequential&fit=contain")
            view (render ($ ui/app-root
                            {:initial-search search
                             :fetch-fn (make-mock-fetch body)
                             :preload-fn instant-preload}))]
        (wait-for
         (fn []
           (and (some? (.queryByText screen "1 / 2"))
                (some? (.queryByRole screen "img"))))
         (fn []
           (let [doc-style (.-style (.-documentElement js/document))
                 img (.getByRole screen "img")]
             (is (= "250ms"
                    (.getPropertyValue doc-style "--fade-duration")))
             (is (= "http://localhost/photos/a.jpg"
                    (.getAttribute img "src"))))
           (.click fire-event
                   (.getByRole screen "button" #js {:name #"Next"}))
           (is (some? (.queryByText screen "2 / 2")))
           (let [in-img (.getByRole screen "img")
                 out-img (.querySelector js/document
                                         ".slideshow-image--outgoing")]
             (is (= "http://localhost/photos/b.jpg"
                    (.getAttribute in-img "src")))
             (is (.contains (.-classList in-img)
                            "slideshow-image--incoming"))
             (is (some? out-img))
             (is (= "http://localhost/photos/a.jpg"
                    (.getAttribute out-img "src"))))
           (.click fire-event
                   (.getByRole screen "button" #js {:name #"Previous"}))
           (is (some? (.getByText screen "1 / 2")))
           (.click fire-event
                   (.getByRole screen "button" #js {:name "Pause"}))
           (is (some? (.getByRole screen "button" #js {:name "Resume"})))
           (reset! body (str "<a href=\"a.jpg\">a.jpg</a>"
                             "<a href=\"b.jpg\">b.jpg</a>"
                             "<a href=\"c.jpg\">c.jpg</a>"))
           (.click fire-event
                   (.getByRole screen "button" #js {:name "Refresh"}))
           (wait-for
            (fn []
              (some? (.queryByText screen "1 / 3")))
            (fn []
              (try
                (.click fire-event
                        (.getByRole screen "button"
                                    #js {:name "Configure"}))
                (let [idx-in (.getByLabelText screen
                                              #"Image Index URL")
                      del-in (.getByLabelText screen #"Slide Delay")
                      fade-in (.getByLabelText screen
                                               #"Fade Time \(ms\)")]
                  (is (= "photos/" (.-value idx-in)))
                  (is (= "5" (.-value del-in)))
                  (is (= "250" (.-value fade-in))))
                (finally
                  ((js/goog.object.get view "unmount"))
                  (done)))))))))))

(deftest wake-lock-lifecycle-test
  (testing "acquires, releases, and re-acquires screen wake lock"
    (async done
      (let [rtl (js/require "@testing-library/react")
            render (.-render rtl)
            screen (.-screen rtl)
            fire-event (.-fireEvent rtl)
            had-wl? (js/Object.prototype.hasOwnProperty.call
                     js/navigator "wakeLock")
            orig-wl (js/goog.object.get js/navigator "wakeLock")
            had-vis? (js/Object.prototype.hasOwnProperty.call
                      js/document "visibilityState")
            orig-vis (js/Object.getOwnPropertyDescriptor
                      js/document "visibilityState")
            requests (atom [])
            releases (atom 0)
            rel-cb (atom nil)
            sentinel
            #js {:released false
                 :release (fn []
                            (swap! releases inc)
                            (js/Promise.resolve nil))
                 :addEventListener (fn [evt cb]
                                     (when (= evt "release")
                                       (reset! rel-cb cb)))}
            mock-wl #js {:request (fn [t]
                                    (reset! rel-cb nil)
                                    (swap! requests conj t)
                                    (js/Promise.resolve sentinel))}
            restore!
            (fn []
              (if had-wl?
                (js/Object.defineProperty
                 js/navigator "wakeLock"
                 #js {:value orig-wl
                      :configurable true
                      :writable true})
                (js/Reflect.deleteProperty js/navigator "wakeLock"))
              (if (and had-vis? orig-vis)
                (js/Object.defineProperty
                 js/document "visibilityState" orig-vis)
                (js/Reflect.deleteProperty js/document
                                           "visibilityState")))]
        (js/Object.defineProperty
         js/navigator "wakeLock"
         #js {:value mock-wl
              :configurable true
              :writable true})
        (js/Object.defineProperty
         js/document "visibilityState"
         #js {:value "visible"
              :configurable true})
        (let [body (atom "<a href=\"a.jpg\">a.jpg</a>")
              search "?index=photos%2F&delay=5&refresh=60&fade=0"
              view (render ($ ui/app-root
                              {:initial-search search
                               :fetch-fn (make-mock-fetch body)
                               :preload-fn instant-preload}))]
          (wait-for
           (fn []
             (and (= 1 (count @requests))
                  (some? @rel-cb)
                  (some? (.queryByRole screen "img"))))
           (fn []
             (is (= ["screen"] @requests))
             (.click fire-event
                     (.getByRole screen "button" #js {:name "Pause"}))
             (wait-for
              (fn [] (= 1 @releases))
              (fn []
                (.click fire-event
                        (.getByRole screen "button"
                                    #js {:name "Resume"}))
                (wait-for
                 (fn [] (and (= 2 (count @requests)) (some? @rel-cb)))
                 (fn []
                   (is (= ["screen" "screen"] @requests))
                   (let [Evt (.-Event js/window)]
                     (when-let [cb @rel-cb]
                       (cb (Evt. "release")))
                     (.dispatchEvent js/document
                                     (Evt. "visibilitychange")))
                   (wait-for
                    (fn [] (and (= 3 (count @requests)) (some? @rel-cb)))
                    (fn []
                      (try
                        (is (= ["screen" "screen" "screen"]
                               @requests))
                        (.click fire-event
                                (.getByRole screen "button"
                                            #js {:name "Configure"}))
                        (is (= 2 @releases))
                        (finally
                          ((js/goog.object.get view "unmount"))
                          (restore!)
                          (done))))))))))))))))

(deftest precaches-images-before-crossfade-test
  (testing "waits for preload/decode before fading in new images"
    (async done
      (let [rtl (js/require "@testing-library/react")
            render (.-render rtl)
            screen (.-screen rtl)
            fire-event (.-fireEvent rtl)
            preloaded-urls (atom [])
            resolvers (atom {})
            deferred-preload
            (fn [url]
              (swap! preloaded-urls conj url)
              (js/Promise.
               (fn [resolve _]
                 (swap! resolvers assoc url resolve))))
            body (atom (str "<a href=\"a.jpg\">a.jpg</a>"
                            "<a href=\"b.jpg\">b.jpg</a>"
                            "<a href=\"c.jpg\">c.jpg</a>"))
            search (str "?index=photos%2F&delay=5&refresh=60"
                        "&fade=250&order=sequential&fit=contain")
            view (render ($ ui/app-root
                            {:initial-search search
                             :fetch-fn (make-mock-fetch body)
                             :preload-fn deferred-preload}))
            url-a "http://localhost/photos/a.jpg"
            url-b "http://localhost/photos/b.jpg"
            url-c "http://localhost/photos/c.jpg"]
        (wait-for
         (fn []
           (and (some? (.queryByText screen "Loading image\u2026"))
                (contains? @resolvers url-a)
                (contains? @resolvers url-b)
                (contains? @resolvers url-c)))
         (fn []
           (is (nil? (.queryByRole screen "img")))
           ((get @resolvers url-a) true)
           (wait-for
            (fn [] (some? (.queryByRole screen "img")))
            (fn []
              (is (= url-a
                     (.getAttribute (.getByRole screen "img") "src")))
              (.click fire-event
                      (.getByRole screen "button" #js {:name #"Next"}))
              (is (= url-a
                     (.getAttribute (.getByRole screen "img") "src")))
              ((get @resolvers url-b) true)
              (wait-for
               (fn []
                 (= url-b
                    (.getAttribute (.getByRole screen "img") "src")))
               (fn []
                 (let [in-img (.getByRole screen "img")
                       out-img (.querySelector
                                js/document
                                ".slideshow-image--outgoing")]
                   (is (.contains (.-classList in-img)
                                  "slideshow-image--incoming"))
                   (is (some? out-img))
                   (is (= url-a (.getAttribute out-img "src"))))
                 ((get @resolvers url-c) true)
                 (let [prev-count (count (filter (fn [u] (= u url-c))
                                                 @preloaded-urls))]
                   (js/Promise.resolve nil)
                   (.then
                    (js/Promise.resolve nil)
                    (fn [_]
                      (.click fire-event
                              (.getByRole screen "button"
                                          #js {:name #"Next"}))
                      (try
                        (is (= url-c
                               (.getAttribute
                                (.getByRole screen "img")
                                "src")))
                        (is (= prev-count
                               (count (filter (fn [u] (= u url-c))
                                              @preloaded-urls))))
                        (finally
                          ((js/goog.object.get view "unmount"))
                          (done))))))))))))))))
