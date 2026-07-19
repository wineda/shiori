/* 栞 — Service Worker
   役割：アプリ本体（HTML/CSS/JS/フォント/アイコン/manifest）だけをキャッシュし、
   オフライン起動を可能にする。
   重要：ユーザーデータ（IndexedDB）は SW の管轄外。ここでは一切触れない。
        キャッシュの更新・削除をしても IndexedDB は消えない（別枠）。 */

const CACHE = 'shiori-shell-v3';

// プリキャッシュするアプリ本体。バージョンを上げたら CACHE 名も上げる。
const SHELL = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.webmanifest',
  './fonts/ShipporiMincho-400.woff2',
  './fonts/ShipporiMincho-500.woff2',
  './fonts/ZenKakuGothicNew-400.woff2',
  './fonts/ZenKakuGothicNew-500.woff2',
  './fonts/ZenKakuGothicNew-700.woff2',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-192-maskable.png',
  './icons/icon-512-maskable.png'
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE)
      .then((c) => c.addAll(SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  // GET 以外・別オリジンは素通し（IndexedDB はそもそも fetch を経由しない）。
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  // ページ遷移（アプリ起動）はキャッシュのシェルを最優先で返し、裏で更新する。
  if (req.mode === 'navigate') {
    e.respondWith(
      caches.match('./index.html').then((cached) => {
        const net = fetch(req)
          .then((res) => { caches.open(CACHE).then((c) => c.put('./index.html', res.clone())); return res; })
          .catch(() => cached);
        return cached || net;
      })
    );
    return;
  }

  // それ以外の同一オリジン GET：キャッシュ優先、無ければ取得してキャッシュ。
  e.respondWith(
    caches.match(req).then((cached) => {
      if (cached) return cached;
      return fetch(req).then((res) => {
        if (res && res.status === 200 && res.type === 'basic') {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      });
    })
  );
});
