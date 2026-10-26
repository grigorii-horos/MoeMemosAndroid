package me.mudkip.moememos.data.coffer

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CofferEditorProxyTest {
    private val seen = mutableListOf<String>()

    private fun client(answer: (okhttp3.Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            seen += chain.request().url.toString()
            answer(chain.request())
        })
        .build()

    private fun ok(req: okhttp3.Request) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
        .code(200).message("OK").body("{}".toResponseBody("application/json".toMediaType())).build()

    private val prefixes = listOf("/api/", "/uploads/", "/plugins/")

    @Test
    fun onlyTheListedPrefixesOnTheAssetOriginAreForwarded() {
        val proxy = CofferEditorProxy(client(::ok), "https://h/", prefixes)
        assertEquals(200, proxy.handle("$ASSET_ORIGIN/api/node/notes", "GET")!!.status)
        assertEquals(200, proxy.handle("$ASSET_ORIGIN/uploads/notes/notes/5/a.jpg", "GET")!!.status)
        assertEquals(200, proxy.handle("$ASSET_ORIGIN/plugins/notes/schema.js?v=1", "GET")!!.status)
        assertEquals(
            listOf("https://h/api/node/notes", "https://h/uploads/notes/notes/5/a.jpg", "https://h/plugins/notes/schema.js?v=1"),
            seen,
        )
        assertNull(proxy.handle("$ASSET_ORIGIN/embed/editor.html", "GET"))
        assertNull(proxy.handle("$ASSET_ORIGIN/vendor/react.js", "GET"))
        assertNull(proxy.handle("https://evil/api/x", "GET"))
        assertEquals(3, seen.size)
    }

    @Test
    fun postIsNotForwardedInV1() {
        val proxy = CofferEditorProxy(client(::ok), "https://h", prefixes)
        assertNull(proxy.handle("$ASSET_ORIGIN/api/upload", "POST"))
        assertEquals(0, seen.size)
    }

    @Test
    fun networkFailureFallsBackToCacheThen504() {
        val proxy = CofferEditorProxy(
            client { req ->
                if (req.cacheControl.onlyIfCached) {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(504).message("cache miss")
                        .body("".toResponseBody(null)).build()
                } else {
                    throw IOException("offline")
                }
            },
            "https://h",
            prefixes,
        )
        val res = proxy.handle("$ASSET_ORIGIN/api/plugins", "GET")!!
        assertEquals(504, res.status)
        assertEquals(2, seen.size) // network, then the cache-only retry
    }

    @Test
    fun noAnswerAtAllIsA504NotAnException() {
        val proxy = CofferEditorProxy(client { throw IOException("offline") }, "https://h", prefixes)
        assertEquals(504, proxy.handle("$ASSET_ORIGIN/api/plugins", "GET")!!.status)
    }

    @Test
    fun oneProxyPerHostForTheWholeProcess() {
        val made = mutableListOf<String>()
        fun get(host: String) = CofferEditorProxies.forHost(host) {
            made += host
            CofferEditorProxy(client(::ok), host, prefixes)
        }
        val a = get("https://h")
        assertTrue(a === get("https://h"))
        val b = get("https://other")
        assertTrue(a !== b)
        assertEquals(listOf("https://h", "https://other"), made)
    }

    @Test
    fun onlyTheEditorPagesOwnOriginStaysInTheWebView() {
        assertTrue(isEditorPage("$ASSET_ORIGIN/embed/editor.html"))
        assertFalse(isEditorPage("https://example.com/x"))
        assertFalse(isEditorPage("https://appassets.androidplatform.net.evil.com/"))
    }
}
