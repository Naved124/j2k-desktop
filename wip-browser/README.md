Work in progress: the embedded browser (Cloudflare + WebView), kept out of the build until it's finished.
Destination: source-api/src/main/kotlin/dev/naved/j2kdesktop/browser/
Done: Cdp.kt (DevTools connection), Browser.kt (finds/launches system Chromium headless, UA + client hints), CookieStore.kt (persistent cookies shared by OkHttp/CookieManager/browser).
Still to write: CloudflareInterceptor (headless solve -> visible window -> route via browser), BrowserFetcher, android.webkit.* on top of CDP (move webkit from android-compat to source-api), NetworkHelper using PersistentCookieJar + Browser.userAgent.
