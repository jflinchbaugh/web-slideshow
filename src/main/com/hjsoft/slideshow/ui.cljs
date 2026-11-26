(ns com.hjsoft.slideshow.ui
  (:require [clojure.string :as str]
            [helix.core :refer [$ <> defnc]]
            [helix.dom :as d]
            [helix.hooks :as hooks]
            [taoensso.telemere :as tel]
            [com.hjsoft.slideshow.core :as core]
            [com.hjsoft.slideshow.version :as version]))

(defnc config-form
  "Configuration form for slideshow URL parameters."
  [{:keys [initial-config on-submit]}]
  (let [cfg (merge core/default-config initial-config)
        [index set-index] (hooks/use-state (:index cfg))
        [delay set-delay] (hooks/use-state (str (:delay cfg)))
        [refresh set-refresh] (hooks/use-state (str (:refresh cfg)))
        [fade set-fade] (hooks/use-state (str (:fade cfg)))
        [order set-order] (hooks/use-state (:order cfg))
        [fit set-fit] (hooks/use-state (:fit cfg))
        [errors set-errors] (hooks/use-state {})]
    (hooks/use-effect
     [initial-config]
     (let [next-cfg (merge core/default-config initial-config)]
       (set-index (:index next-cfg))
       (set-delay (str (:delay next-cfg)))
       (set-refresh (str (:refresh next-cfg)))
       (set-fade (str (:fade next-cfg)))
       (set-order (:order next-cfg))
       (set-fit (:fit next-cfg))
       (set-errors {})))
    (let [on-form-submit
          (fn [e]
            (.preventDefault e)
            (let [res (core/validate-config {:index index
                                             :delay delay
                                             :refresh refresh
                                             :fade fade
                                             :order order
                                             :fit fit})]
              (if (:valid? res)
                (do
                  (set-errors {})
                  (on-submit (:config res)))
                (set-errors (:errors res)))))]
      (d/div
       {:class "app-container"}
       (d/header
        {:class "app-header"}
        (d/div
         {:class "app-brand"}
         (d/img {:class "app-logo"
                 :src "icon.svg"
                 :alt "Slideshow"})
         (d/h1 {:class "app-title"} "Slideshow")))
       (d/main
        (d/section
         {:class "card"}
         (d/h2 {:class "card-title"} "Slideshow Configuration")
         (d/form
          {:class "config-form"
           :noValidate true
           :on-submit on-form-submit}
          (d/div
           {:class "form-field"}
           (d/label {:class "form-label" :htmlFor "cfg-index"}
                    "Image Index URL")
           (d/input {:class "form-input"
                     :id "cfg-index"
                     :type "text"
                     :placeholder "images/"
                     :value index
                     :on-change (fn [e]
                                  (set-index (.. e -target -value)))})
           (d/p {:class "form-help"}
                (str "Relative or absolute URL to an Apache, Nginx, "
                     "or JSON image index."))
           (when-let [err (:index errors)]
             (d/p {:class "field-error" :role "alert"} err)))
          (d/div
           {:class "form-row"}
           (d/div
            {:class "form-field"}
            (d/label {:class "form-label" :htmlFor "cfg-delay"}
                     "Slide Delay (seconds)")
            (d/input {:class "form-input"
                      :id "cfg-delay"
                      :type "number"
                      :min "1"
                      :value delay
                      :on-change (fn [e]
                                   (set-delay (.. e -target -value)))})
            (when-let [err (:delay errors)]
              (d/p {:class "field-error" :role "alert"} err)))
           (d/div
            {:class "form-field"}
            (d/label {:class "form-label" :htmlFor "cfg-refresh"}
                     "Index Refresh Interval (seconds)")
            (d/input {:class "form-input"
                      :id "cfg-refresh"
                      :type "number"
                      :min "5"
                      :value refresh
                      :on-change (fn [e]
                                   (set-refresh
                                    (.. e -target -value)))})
            (when-let [err (:refresh errors)]
              (d/p {:class "field-error" :role "alert"} err)))
           (d/div
            {:class "form-field"}
            (d/label {:class "form-label" :htmlFor "cfg-fade"}
                     "Fade Time (ms)")
            (d/input {:class "form-input"
                      :id "cfg-fade"
                      :type "number"
                      :min "0"
                      :step "100"
                      :value fade
                      :on-change (fn [e]
                                   (set-fade (.. e -target -value)))})
            (when-let [err (:fade errors)]
              (d/p {:class "field-error" :role "alert"} err))))
          (d/div
           {:class "form-row"}
           (d/div
            {:class "form-field"}
            (d/label {:class "form-label" :htmlFor "cfg-order"}
                     "Playback Order")
            (d/select {:class "form-select"
                       :id "cfg-order"
                       :value order
                       :on-change (fn [e]
                                    (set-order (.. e -target -value)))}
                      (d/option {:value "sequential"} "Sequential")
                      (d/option {:value "shuffle"} "Shuffle")))
           (d/div
            {:class "form-field"}
            (d/label {:class "form-label" :htmlFor "cfg-fit"}
                     "Image Fit")
            (d/select {:class "form-select"
                       :id "cfg-fit"
                       :value fit
                       :on-change (fn [e]
                                    (set-fit (.. e -target -value)))}
                      (d/option {:value "contain"}
                                "Contain (fit whole image)")
                      (d/option {:value "cover"}
                                "Cover (fill screen)"))))
          (d/div
           {:class "form-actions"}
           (d/button {:class "btn btn-primary"
                      :type "submit"}
                     "Start Slideshow")))))
       (d/footer
        {:class "build-info"}
        (str "Build: " version/build-date))))))

(defn- default-preload-image
  "Preload and decode an image URL, resolving with {:ok? ... :img ...}."
  [url]
  (if-not (exists? js/Image)
    (js/Promise.resolve {:ok? false :img nil})
    (js/Promise.
     (fn [resolve _]
       (let [img (js/Image.)
             finish! (fn [ok?]
                       (set! (.-onload img) nil)
                       (set! (.-onerror img) nil)
                       (resolve {:ok? ok? :img (when ok? img)}))]
         (set! (.-onload img)
               (fn [_]
                 (if (exists? (.-decode img))
                   (-> (.decode img)
                       (.then (fn [] (finish! true)))
                       (.catch (fn [_] (finish! true))))
                   (finish! true))))
         (set! (.-onerror img)
               (fn [_]
                 (finish! false)))
         (set! (.-src img) url))))))

(defnc slideshow-view
  "Full-screen slideshow display with periodic index refresh."
  [{:keys [config on-configure fetch-fn preload-fn]}]
  (let [[playlist set-playlist] (hooks/use-state {:images [] :index 0})
        [status set-status] (hooks/use-state :loading)
        [error-msg set-error-msg] (hooks/use-state nil)
        [paused? set-paused?] (hooks/use-state false)
        [hud-visible? set-hud-visible?] (hooks/use-state true)
        [fullscreen? set-fullscreen?] (hooks/use-state false)
        [refreshing? set-refreshing?] (hooks/use-state false)
        [fade-state set-fade-state] (hooks/use-state
                                     {:active nil :prev nil :seq 0})
        mounted-ref (hooks/use-ref true)
        hud-timer-ref (hooks/use-ref nil)
        stage-ref (hooks/use-ref nil)
        loaded-urls-ref (hooks/use-ref {})
        warm-order-ref (hooks/use-ref [])
        inflight-ref (hooks/use-ref {})
        fade-ms (or (:fade config) 0)
        images (:images playlist)
        total (count images)
        safe-idx (if (pos? total) (mod (:index playlist) total) 0)
        current-url (get images safe-idx)
        ensure-preloaded!
        (hooks/use-callback
         [preload-fn]
         (fn [url]
           (cond
             (contains? (.-current loaded-urls-ref) url)
             (js/Promise.resolve true)

             (contains? (.-current inflight-ref) url)
             (get (.-current inflight-ref) url)

             :else
             (let [do-preload (or preload-fn default-preload-image)
                   p (-> (do-preload url)
                         (.then
                          (fn [res]
                            (set! (.-current inflight-ref)
                                  (dissoc (.-current inflight-ref) url))
                            (let [ok? (if (map? res)
                                        (not (false? (:ok? res)))
                                        (boolean res))
                                  img (when (map? res) (:img res))]
                              (when ok?
                                (let [m (assoc (.-current loaded-urls-ref)
                                               url (or img true))
                                      ord (conj (.-current warm-order-ref)
                                                url)]
                                  (if (> (count ord) 20)
                                    (let [drop-url (first ord)
                                          m2 (if (contains? m drop-url)
                                               (assoc m drop-url true)
                                               m)]
                                      (set! (.-current loaded-urls-ref) m2)
                                      (set! (.-current warm-order-ref)
                                            (subvec ord 1)))
                                    (do
                                      (set! (.-current loaded-urls-ref) m)
                                      (set! (.-current warm-order-ref)
                                            ord)))))
                              ok?)))
                         (.catch
                          (fn [_]
                            (set! (.-current inflight-ref)
                                  (dissoc (.-current inflight-ref) url))
                            false)))]
               (set! (.-current inflight-ref)
                     (assoc (.-current inflight-ref) url p))
               p))))
        show-hud!
        (hooks/use-callback
         []
         (fn []
           (set-hud-visible? true)
           (when-let [tid (.-current hud-timer-ref)]
             (js/clearTimeout tid))
           (set! (.-current hud-timer-ref)
                 (js/setTimeout
                  (fn []
                    (when (.-current mounted-ref)
                      (set-hud-visible? false)))
                  3000))))
        advance-next
        (hooks/use-callback
         []
         (fn []
           (set-playlist
            (fn [{:keys [images index] :as prev}]
              (if (empty? images)
                prev
                (assoc prev :index (core/next-index index
                                                    (count images))))))))
        step-prev
        (hooks/use-callback
         []
         (fn []
           (set-playlist
            (fn [{:keys [images index] :as prev}]
              (if (empty? images)
                prev
                (assoc prev :index (core/prev-index index
                                                    (count images))))))))
        toggle-fullscreen!
        (hooks/use-callback
         []
         (fn []
           (if (.-fullscreenElement js/document)
             (when (exists? (.-exitFullscreen js/document))
               (.exitFullscreen js/document))
             (when-let [el (.-current stage-ref)]
               (when (exists? (.-requestFullscreen el))
                 (-> (.requestFullscreen el)
                     (.catch (fn [_] nil))))))))
        refresh-index!
        (hooks/use-callback
         [config fetch-fn]
         (fn []
           (let [do-fetch (or fetch-fn js/fetch)
                 page-href (.. js/window -location -href)
                 req-url (.-href (js/URL. (:index config) page-href))]
             (set-refreshing? true)
             (-> (do-fetch req-url #js {:cache "no-store"})
                 (.then (fn [resp]
                          (when-not (.-ok resp)
                            (throw (js/Error.
                                    (str "HTTP " (.-status resp)))))
                          (-> (.text resp)
                              (.then
                               (fn [body]
                                 (let [resp-url (.-url resp)
                                       raw-base (if (str/blank? resp-url)
                                                  req-url
                                                  resp-url)
                                       base-url
                                       (core/normalize-index-base-url
                                        raw-base page-href)]
                                   (core/parse-image-index body
                                                           base-url)))))))
                 (.then (fn [fetched]
                          (when (.-current mounted-ref)
                            (set-playlist
                             (fn [prev]
                               (core/merge-image-list
                                (:images prev)
                                (:index prev)
                                fetched
                                (:order config))))
                            (set-status (if (seq fetched) :ready :empty))
                            (set-error-msg nil)
                            (set-refreshing? false))))
                 (.catch (fn [err]
                           (tel/log! :warn
                                     (str "Index refresh failed: " err))
                           (when (.-current mounted-ref)
                             (set-error-msg
                              (or (.-message err)
                                  "Failed to load image index"))
                             (set-status (fn [s]
                                           (if (= s :ready)
                                             :ready
                                             :error)))
                             (set-refreshing? false))))))))]
    (hooks/use-effect
     [fade-ms]
     (let [doc-style (.-style (.-documentElement js/document))]
       (.setProperty doc-style "--fade-duration" (str fade-ms "ms"))
       (fn []
         (.removeProperty doc-style "--fade-duration"))))
    (hooks/use-effect
     [images]
     (let [keep-set (set images)]
       (set! (.-current loaded-urls-ref)
             (select-keys (.-current loaded-urls-ref) keep-set))
       (set! (.-current warm-order-ref)
             (filterv (fn [u] (contains? keep-set u))
                      (.-current warm-order-ref)))))
    (hooks/use-effect
     [current-url fade-ms ensure-preloaded!]
     (if-not current-url
       (set-fade-state {:active nil :prev nil :seq 0})
       (let [commit!
             (fn []
               (set-fade-state
                (fn [st]
                  (if (= (:active st) current-url)
                    st
                    {:active current-url
                     :prev (when (and (:active st) (pos? fade-ms))
                             (:active st))
                     :seq (inc (:seq st))}))))]
         (if (contains? (.-current loaded-urls-ref) current-url)
           (commit!)
           (let [cancelled (atom false)]
             (-> (ensure-preloaded! current-url)
                 (.then (fn [_]
                          (when (and (not @cancelled)
                                     (.-current mounted-ref))
                            (commit!)))))
             (fn []
               (reset! cancelled true)))))))
    (hooks/use-effect
     [(:seq fade-state) (:prev fade-state) fade-ms]
     (when (and (:prev fade-state) (pos? fade-ms))
       (let [expected-seq (:seq fade-state)
             id (js/setTimeout
                 (fn []
                   (when (.-current mounted-ref)
                     (set-fade-state
                      (fn [st]
                        (if (= (:seq st) expected-seq)
                          (assoc st :prev nil)
                          st)))))
                 (+ fade-ms 100))]
         (fn []
           (js/clearTimeout id)))))
    (hooks/use-effect
     :once
     (set! (.-current mounted-ref) true)
     (show-hud!)
     (fn []
       (set! (.-current mounted-ref) false)
       (when-let [tid (.-current hud-timer-ref)]
         (js/clearTimeout tid))))
    (hooks/use-effect
     [config refresh-index!]
     (set-status :loading)
     (set-error-msg nil)
     (set-playlist {:images [] :index 0})
     (refresh-index!)
     (let [ms (* (:refresh config) 1000)
           id (js/setInterval refresh-index! ms)]
       (fn []
         (js/clearInterval id))))
    (hooks/use-effect
     [paused? total (:delay config) advance-next
      (some? (:active fade-state))]
     (when (and (not paused?)
                (> total 1)
                (some? (:active fade-state)))
       (let [ms (* (:delay config) 1000)
             id (js/setInterval advance-next ms)]
         (fn []
           (js/clearInterval id)))))
    (hooks/use-effect
     [images safe-idx total ensure-preloaded!]
     (when (pos? total)
       (doseq [u (core/preload-window images safe-idx 3)]
         (ensure-preloaded! u))))
    (hooks/use-effect
     [advance-next step-prev toggle-fullscreen! refresh-index! show-hud!]
     (let [on-key (fn [e]
                    (let [k (.-key e)]
                      (cond
                        (= k "ArrowRight")
                        (do (.preventDefault e)
                            (show-hud!)
                            (advance-next))

                        (= k "ArrowLeft")
                        (do (.preventDefault e)
                            (show-hud!)
                            (step-prev))

                        (= k " ")
                        (do (.preventDefault e)
                            (show-hud!)
                            (set-paused? not))

                        (or (= k "f") (= k "F"))
                        (do (.preventDefault e)
                            (show-hud!)
                            (toggle-fullscreen!))

                        (or (= k "r") (= k "R"))
                        (do (.preventDefault e)
                            (show-hud!)
                            (refresh-index!)))))]
       (.addEventListener js/window "keydown" on-key)
       (fn []
         (.removeEventListener js/window "keydown" on-key))))
    (hooks/use-effect
     :once
     (let [on-fs (fn [_]
                   (set-fullscreen?
                    (boolean (.-fullscreenElement js/document))))]
       (.addEventListener js/document "fullscreenchange" on-fs)
       (fn []
         (.removeEventListener js/document "fullscreenchange" on-fs))))
    (hooks/use-effect
     [paused?]
     (when (and (not paused?)
                (exists? js/navigator)
                (some? (.-wakeLock js/navigator)))
       (let [active (atom true)
             sentinel-atom (atom nil)
             release-lock!
             (fn []
               (when-let [s @sentinel-atom]
                 (reset! sentinel-atom nil)
                 (-> (.release s) (.catch (fn [_] nil)))))
             request-lock!
             (fn []
               (when (and @active
                          (nil? @sentinel-atom)
                          (not= (.-visibilityState js/document)
                                "hidden"))
                 (-> (.request (.-wakeLock js/navigator) "screen")
                     (.then
                      (fn [s]
                        (if @active
                          (do
                            (reset! sentinel-atom s)
                            (when (exists? (.-addEventListener s))
                              (.addEventListener
                               s "release"
                               (fn [_]
                                 (swap! sentinel-atom
                                        (fn [cur]
                                          (when-not (= cur s)
                                            cur)))))))
                          (-> (.release s) (.catch (fn [_] nil))))))
                     (.catch
                      (fn [err]
                        (tel/log!
                         :debug
                         (str "Wake lock unavailable: " err)))))))
             on-vis (fn [_]
                      (when (= (.-visibilityState js/document) "visible")
                        (request-lock!)))]
         (request-lock!)
         (.addEventListener js/document "visibilitychange" on-vis)
         (fn []
           (reset! active false)
           (.removeEventListener js/document "visibilitychange" on-vis)
           (release-lock!)))))
    (let [stage-cls (if hud-visible?
                      "slideshow-stage"
                      "slideshow-stage slideshow-stage--idle")
          hud-cls (if hud-visible?
                    "slideshow-hud slideshow-hud--visible"
                    "slideshow-hud slideshow-hud--hidden")
          fit-cls (if (= (:fit config) "cover")
                    "slideshow-image slideshow-image--cover"
                    "slideshow-image slideshow-image--contain")
          active-url (:active fade-state)
          prev-url (:prev fade-state)
          in-cls (str fit-cls
                      (if (pos? fade-ms)
                        " slideshow-image--incoming"
                        " slideshow-image--instant"))
          filename (if active-url
                     (core/filename-from-url active-url)
                     "")]
      (d/div
       {:ref stage-ref
        :class stage-cls
        :on-mouse-move (fn [_] (show-hud!))
        :on-click (fn [_] (show-hud!))}
       (cond
         (and (= status :ready) active-url)
         (<>
          (when prev-url
            (let [cur-seq (:seq fade-state)]
              (d/img {:key (str "out-" cur-seq "-" prev-url)
                      :class (str fit-cls " slideshow-image--outgoing")
                      :src prev-url
                      :alt ""
                      :aria-hidden "true"
                      :on-animation-end
                      (fn [_]
                        (set-fade-state
                         (fn [st]
                           (if (= (:seq st) cur-seq)
                             (assoc st :prev nil)
                             st))))})))
          (d/img {:key (str "in-" (:seq fade-state) "-" active-url)
                  :class in-cls
                  :src active-url
                  :alt filename
                  :on-error (fn [_]
                              (tel/log!
                               :warn
                               (str "Failed to load image: "
                                    active-url)))}))

         (= status :ready)
         (d/div
          {:class "slideshow-status"}
          (d/p "Loading image\u2026"))

         (= status :loading)
         (d/div
          {:class "slideshow-status"}
          (d/p "Loading image index\u2026"))

         (= status :empty)
         (d/div
          {:class "slideshow-status"}
          (d/p (str "No images found at " (:index config)))
          (d/div
           {:class "slideshow-status-actions"}
           (d/button {:class "hud-btn"
                      :type "button"
                      :on-click (fn [_] (refresh-index!))}
                     "Refresh")
           (d/button {:class "hud-btn"
                      :type "button"
                      :on-click (fn [_] (on-configure config))}
                     "Configure")))

         :else
         (d/div
          {:class "slideshow-status slideshow-status--error"}
          (d/p (or error-msg "Failed to load image index"))
          (d/div
           {:class "slideshow-status-actions"}
           (d/button {:class "hud-btn"
                      :type "button"
                      :on-click (fn [_] (refresh-index!))}
                     "Retry")
           (d/button {:class "hud-btn"
                      :type "button"
                      :on-click (fn [_] (on-configure config))}
                     "Configure"))))
       (d/div
        {:class hud-cls}
        (d/div
         {:class "hud-info"}
         (when (pos? total)
           (d/span {:class "hud-counter"}
                   (str (inc safe-idx) " / " total)))
         (when-not (str/blank? filename)
           (d/span {:class "hud-filename"} filename))
         (when (and (= status :ready) error-msg)
           (d/span {:class "hud-error"}
                   (str "Refresh failed: " error-msg))))
        (d/div
         {:class "hud-controls"}
         (d/button {:class "hud-btn"
                    :type "button"
                    :aria-label "Previous"
                    :disabled (zero? total)
                    :on-click (fn [_]
                                (show-hud!)
                                (step-prev))}
                   "\u2039 Prev")
         (d/button {:class "hud-btn"
                    :type "button"
                    :on-click (fn [_]
                                (show-hud!)
                                (set-paused? not))}
                   (if paused? "Resume" "Pause"))
         (d/button {:class "hud-btn"
                    :type "button"
                    :aria-label "Next"
                    :disabled (zero? total)
                    :on-click (fn [_]
                                (show-hud!)
                                (advance-next))}
                   "Next \u203a"))
        (d/div
         {:class "hud-controls"}
         (d/button {:class "hud-btn"
                    :type "button"
                    :disabled refreshing?
                    :on-click (fn [_]
                                (show-hud!)
                                (refresh-index!))}
                   (if refreshing? "Refreshing\u2026" "Refresh"))
         (d/button {:class "hud-btn"
                    :type "button"
                    :on-click (fn [_]
                                (show-hud!)
                                (toggle-fullscreen!))}
                   (if fullscreen? "Exit Fullscreen" "Fullscreen"))
         (d/button {:class "hud-btn"
                    :type "button"
                    :on-click (fn [_] (on-configure config))}
                   "Configure")))))))

(defnc app-root
  "Root application component routing between config and slideshow."
  [{:keys [initial-search on-navigate fetch-fn preload-fn]}]
  (let [start-search (if (some? initial-search)
                       initial-search
                       (.. js/window -location -search))
        [route set-route] (hooks/use-state
                           (fn []
                             (core/parse-location-search start-search)))
        navigate! (fn [qs]
                    (let [url (str (.. js/window -location -pathname) qs)]
                      (if on-navigate
                        (on-navigate url)
                        (.pushState js/window.history nil "" url))))
        handle-start (fn [cfg]
                       (navigate! (core/encode-config cfg))
                       (set-route {:mode :slideshow :config cfg}))
        handle-configure (fn [cfg]
                           (navigate! (core/encode-edit-config cfg))
                           (set-route {:mode :config :config cfg}))]
    (hooks/use-effect
     :once
     (let [on-pop (fn [_]
                    (set-route (core/parse-location-search
                                (.. js/window -location -search))))]
       (.addEventListener js/window "popstate" on-pop)
       (fn []
         (.removeEventListener js/window "popstate" on-pop))))
    (if (= :slideshow (:mode route))
      ($ slideshow-view {:config (:config route)
                         :on-configure handle-configure
                         :fetch-fn fetch-fn
                         :preload-fn preload-fn})
      ($ config-form {:initial-config (:config route)
                      :on-submit handle-start}))))
