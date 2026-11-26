(ns com.hjsoft.slideshow.app
  (:require ["react" :as react]
            ["react-dom/client" :as rdom]
            [helix.core :refer [$]]
            [taoensso.telemere :as tel]
            [com.hjsoft.slideshow.core :as core]
            [com.hjsoft.slideshow.ui :refer [app-root]]))

(defonce root (rdom/createRoot (js/document.getElementById "root")))

(defn render
  "Render the root Helix component."
  []
  (tel/log! :info "rendering com/hjsoft/slideshow")
  (.render root ($ react/StrictMode ($ app-root))))

(defn register-service-worker!
  "Register the cache-busted service worker when supported."
  []
  (when (exists? js/navigator.serviceWorker)
    (let [base (core/resolve-base-path (.-pathname js/location))
          sw-url (str base "sw.js")]
      (-> (js/navigator.serviceWorker.register sw-url)
          (.then (fn [reg]
                   (tel/log!
                    :info
                    (str "ServiceWorker registered: "
                         (.-scope reg)))))
          (.catch (fn [err]
                    (tel/log!
                     :warn
                     (str "ServiceWorker failed: " err))))))))

(defn ^:export init
  "Application startup entry point."
  []
  (render)
  (register-service-worker!))

(defn ^:dev/after-load reload!
  "Re-render on shadow-cljs hot reload."
  []
  (render))
