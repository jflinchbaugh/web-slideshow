// Service Worker for Slideshow
// Cache version updated at build time: {ts}
const CACHE_NAME = 'slideshow-v{ts}';
const ASSETS_TO_CACHE = [
  './',
  './index.html',
  './style.css',
  './js/main.js',
  './manifest.json',
  './icon.svg'
];

const APP_SHELL_PATHS = new Set(
  ASSETS_TO_CACHE.map((asset) => {
    return new URL(asset, self.registration.scope).pathname;
  })
);

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE);
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => {
            return name.startsWith('slideshow-') && name !== CACHE_NAME;
          })
          .map((name) => caches.delete(name))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // Only cache app shell assets; let index & image fetches hit network
  if (req.method !== 'GET' || !APP_SHELL_PATHS.has(url.pathname)) {
    return;
  }

  // Network-first with cache fallback for app shell
  event.respondWith(
    fetch(req)
      .then((networkResponse) => {
        if (networkResponse &&
            networkResponse.status === 200 &&
            networkResponse.type === 'basic') {
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(req, responseToCache);
          });
        }
        return networkResponse;
      })
      .catch(() => {
        return caches.match(req, { ignoreSearch: true })
          .then((cachedResponse) => {
            if (cachedResponse) {
              return cachedResponse;
            }
            if (req.mode === 'navigate') {
              return caches.match('./index.html')
                .then((resp) => resp || caches.match('./'));
            }
          });
      })
  );
});
