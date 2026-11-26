# Slideshow

Full-screen web slideshow application built with ClojureScript, Helix
(React 18), shadow-cljs, external CSS, and build-time cache busting.

## Features

- Prompts for slideshow settings when opened without parameters and
  redirects to an encoded, bookmarkable URL.
- Opens directly into full-screen slideshow mode from a bookmarked URL.
- Periodically re-fetches an Apache `mod_autoindex`, Nginx `autoindex`
  (HTML or JSON), or Python `http.server` directory listing to add new
  images and drop deleted ones without interrupting playback.
- Keyboard shortcuts: `ArrowLeft` / `ArrowRight` (step), `Space`
  (pause/resume), `F` (fullscreen), `R` (refresh index).
- Automatically requests a Screen Wake Lock while playing (in supported
  browsers over HTTPS or `localhost`) so the display stays awake.
- Pre-caches and decodes upcoming images before transitioning so even
  the first appearance of each image fades smoothly.

## URL Query Parameters

- `index`: Relative or absolute URL to the image directory index
  (default `images/`).
- `delay`: Seconds between slides (integer >= 1, default `5`).
- `refresh`: Seconds between index refreshes (integer >= 5, default
  `60`).
- `fade`: Crossfade duration in milliseconds (integer >= 0, default
  `1000`).
- `order`: `sequential` or `shuffle` (default `sequential`).
- `fit`: `contain` or `cover` (default `contain`).
- `edit`: Set to `1` to open the configuration form pre-filled.

## Getting Started

### 1. Install Dependencies

```bash
npm install
# or
make install
```

### 2. Run Development Server

Start shadow-cljs with live reload:

```bash
npx shadow-cljs watch frontend
# or
make dev
```

Open `http://localhost:3000`. Subdirectories under `public/` without an
`index.html` automatically render Apache-style directory listings
during `make dev` for local testing.

### 3. Run Tests

Run the test suite in Node (JSDOM + React Testing Library):

```bash
npx shadow-cljs compile test
# or
make test
```

Watch and re-run tests on file changes:

```bash
npx shadow-cljs watch frontend test
# or
make dev-test
```

### 4. Production Release Build

```bash
npx shadow-cljs release frontend
# or
make release
```

Build hooks inject `?ts=<timestamp>` cache-busting parameters into
`public/index.html` and `public/sw.js` and generate `version.cljs`.

## License

Copyright (c) 2026 John

Distributed under the Eclipse Public License version 1.0.
