package me.mudkip.moememos.ui.coffer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mudkip.moememos.data.coffer.ASSET_ORIGIN
import me.mudkip.moememos.data.coffer.CofferEditorProxy
import me.mudkip.moememos.data.coffer.isEditorPage

/** The page inside the app's assets (`scripts/sync-coffer-editor.sh`). */
private const val EDITOR_URL = "$ASSET_ORIGIN/embed/editor.html"

/**
 * ONE editor WebView for the whole process, booted ahead of time ([warm]) so opening a note only
 * hands it the text: booting the kit (plugin bundles, schema, the editor chunk) is what made the
 * editor visibly assemble itself. A screen [attach]es it and gets the page's events through a
 * [Listener]; leaving [detach]es it, keeping it alive for the next note. A dead renderer is dropped
 * and the next [warm] builds a new one.
 */
object CofferEditorHost {
    interface Listener {
        fun onReady()
        fun onChange(markdown: String)
        fun onShown()
        fun onGone()
    }

    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private var wrapper: MutableContextWrapper? = null
    private var listener: Listener? = null

    /** The page booted (`cofferEditor` exists) — a document can be loaded. */
    var ready = false
        private set

    /** Builds the WebView and starts booting the page; a no-op once one exists. Main thread. */
    fun warm(context: Context, proxy: () -> CofferEditorProxy) {
        if (view != null) return
        val app = context.applicationContext
        val ctx = MutableContextWrapper(app)
        wrapper = ctx
        ready = false
        view = editorWebView(ctx, proxy(), CofferBridge(
            main,
            ready = { ready = true; listener?.onReady() },
            change = { md -> listener?.onChange(md) },
            shown = { listener?.onShown() },
        ), onGone = {
            view?.destroy()
            view = null
            ready = false
            listener?.onGone()
        })
    }

    /** Hands the WebView to a screen; [Listener.onReady] fires at once when the page already booted. */
    fun attach(activity: Context, l: Listener): WebView {
        val v = checkNotNull(view) { "warm() first" }
        wrapper?.baseContext = activity
        (v.parent as? ViewGroup)?.removeView(v)
        listener = l
        if (ready) l.onReady()
        return v
    }

    fun detach(l: Listener) {
        if (listener !== l) return
        listener = null
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        wrapper?.let { it.baseContext = it.applicationContext }
    }

    /** Loads a document (`EditorDoc` JSON of the page's API) into the editor. */
    fun load(docJson: String) {
        view?.evaluateJavascript("window.cofferEditor && window.cofferEditor.load($docJson)", null)
    }

    /** The server path prefixes the bundled page needs forwarded (its `version.json`). */
    fun proxiedPrefixes(context: Context): List<String> =
        context.assets.open("coffer-editor/version.json").use { s ->
            Json.parseToJsonElement(s.bufferedReader().readText()).jsonObject["proxied"]!!.jsonArray
                .map { it.jsonPrimitive.content }
        }
}

@SuppressLint("SetJavaScriptEnabled")
private fun editorWebView(
    context: MutableContextWrapper,
    proxy: CofferEditorProxy,
    bridge: CofferBridge,
    onGone: () -> Unit,
): WebView {
    val assets = WebViewAssetLoader.AssetsPathHandler(context)
    fun under(dir: String) = WebViewAssetLoader.PathHandler { path -> assets.handle("coffer-editor/$dir/$path") }
    val loader = WebViewAssetLoader.Builder()
        .addPathHandler("/embed/", under("embed"))
        .addPathHandler("/assets/", under("assets"))
        .addPathHandler("/vendor/", under("vendor"))
        .build()
    val main = Handler(Looper.getMainLooper())
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            // A link tapped in the note opens outside; the editor page never leaves its own origin,
            // so no other page ever gets the note's bridge.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (isEditorPage(request.url.toString())) return false
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return true
            }

            // A renderer killed for memory must not take the app down: drop this WebView.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                main.post(onGone)
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
                    ?: proxy.handle(request.url.toString(), request.method)?.let {
                        WebResourceResponse(it.mime, null, it.status, if (it.status < 400) "OK" else "Error", it.headers, it.body)
                    }
        }
        addJavascriptInterface(bridge, "CofferBridge")
        loadUrl(EDITOR_URL)
    }
}

/** What the editor page calls (`window.CofferBridge`); always answered on the main thread. */
class CofferBridge(
    private val main: Handler,
    private val ready: () -> Unit,
    private val change: (String) -> Unit,
    private val shown: () -> Unit,
) {
    @JavascriptInterface
    fun onReady() {
        main.post(ready)
    }

    @JavascriptInterface
    fun onChange(markdown: String) {
        main.post { change(markdown) }
    }

    @JavascriptInterface
    fun onShown() {
        main.post(shown)
    }
}
