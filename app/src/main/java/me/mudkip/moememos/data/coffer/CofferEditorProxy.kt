package me.mudkip.moememos.data.coffer

import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** The local origin the embedded editor page is served from (androidx.webkit's asset loader). */
const val ASSET_ORIGIN = "https://appassets.androidplatform.net"

/** True for the editor page's own origin — the only thing the editor WebView may navigate to. */
fun isEditorPage(url: String): Boolean = url.startsWith("$ASSET_ORIGIN/")

/** What the WebView gets back for a forwarded request. */
data class ProxyResponse(
    val status: Int,
    val mime: String?,
    val headers: Map<String, String>,
    val body: InputStream,
)

/**
 * Forwards the embedded editor's server requests (`/api/…`, `/uploads/…`, `/plugins/…` — the
 * `proxied` list of the bundle's `version.json`) to the account's Coffer host through the
 * account's OkHttp client, so the token never reaches JavaScript. Every GET answer is kept in a
 * disk cache and served from it when the network fails: the plugin bundles and the schema then
 * survive an offline start after one online one. Anything else — another origin, another path,
 * a POST — is not ours (`null`).
 */
class CofferEditorProxy(
    client: OkHttpClient,
    host: String,
    private val prefixes: List<String>,
    cacheDir: File? = null,
) {
    private val host = host.trimEnd('/')

    private val cache = cacheDir?.let { Cache(it, CACHE_BYTES) }

    private val client: OkHttpClient = client.newBuilder().apply {
        // A dead connection (Wi-Fi without internet) must fall back to the cache quickly.
        connectTimeout(5, TimeUnit.SECONDS)
        readTimeout(10, TimeUnit.SECONDS)
        if (cache != null) {
            cache(cache)
            // Store every answer, revalidate it while online: an offline FORCE_CACHE read then
            // finds the last copy whatever caching the server asked for.
            addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .removeHeader("Pragma").removeHeader("Expires")
                    .header("Cache-Control", "no-cache")
                    .build()
            }
        }
    }.build()

    fun handle(url: String, method: String): ProxyResponse? {
        if (method != "GET" && method != "HEAD") return null
        if (!url.startsWith("$ASSET_ORIGIN/")) return null
        val path = url.removePrefix(ASSET_ORIGIN)
        if (prefixes.none { path.startsWith(it) }) return null
        val request = Request.Builder().url(host + path).method(method, null).build()
        // A plugin bundle is addressed by its version: a stored copy is the right one.
        if (path.startsWith("/plugins/") && "?v=" in path) {
            val cached = runCatching {
                client.newCall(request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()).execute()
            }.getOrNull()
            if (cached != null && cached.code != 504) return cached.toProxyResponse()
            cached?.close()
        }
        val response = try {
            client.newCall(request).execute()
        } catch (_: IOException) {
            try {
                client.newCall(request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()).execute()
            } catch (_: IOException) {
                return offline()
            }
        }
        return response.toProxyResponse()
    }

    private fun Response.toProxyResponse() = ProxyResponse(
        status = code,
        mime = body.contentType()?.let { "${it.type}/${it.subtype}" },
        headers = headers.toMap(),
        body = body.byteStream(),
    )

    /** Releases the disk cache; the next proxy for another host may then own the directory. */
    fun close() {
        cache?.close()
    }

    private fun offline() = ProxyResponse(504, "text/plain", emptyMap(), "offline".byteInputStream())

    private companion object {
        const val CACHE_BYTES = 20L * 1024 * 1024
    }
}

/**
 * One proxy — hence one disk cache on its directory — for the whole process: OkHttp forbids two
 * live caches on one directory, and every screen open used to build a new one. A different host
 * (another account) replaces it and closes the old cache.
 */
object CofferEditorProxies {
    private var current: Pair<String, CofferEditorProxy>? = null

    @Synchronized
    fun forHost(host: String, create: () -> CofferEditorProxy): CofferEditorProxy {
        current?.let { (h, proxy) -> if (h == host) return proxy else proxy.close() }
        return create().also { current = host to it }
    }
}
