package me.mudkip.moememos.ui.coffer

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.mudkip.moememos.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.webkit.WebViewAssetLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.mudkip.moememos.data.coffer.ASSET_ORIGIN
import me.mudkip.moememos.data.coffer.CofferCompose
import me.mudkip.moememos.data.coffer.CofferEditorProxy
import me.mudkip.moememos.data.coffer.CofferEditorProxies
import me.mudkip.moememos.data.coffer.isEditorPage
import me.mudkip.moememos.data.coffer.CofferNode
import me.mudkip.moememos.data.coffer.NoteParts
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.data.model.MemoRepresentable
import me.mudkip.moememos.ui.component.Attachment
import me.mudkip.moememos.ui.component.InputImage
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.MemoInputViewModel
import java.io.File
import java.util.Locale

/** The page inside the app's assets (`scripts/sync-coffer-editor.sh`). */
private const val EDITOR_URL = "$ASSET_ORIGIN/embed/editor.html"

/**
 * A Coffer note's editing screen (spec `../coffer/docs/superpowers/specs/
 * 2026-10-27-moememos-coffer-editor-design.md`): a title field, Coffer's own markdown editor for
 * the text (a WebView over the bundled page), the note's tags as chips, then its attachments.
 * [content] is the memo text MoeMemos stores (`CofferCompose.join`, files as URLs); every change
 * goes back through [onContentChange] in that same form, so sync is untouched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CofferNoteEditor(
    modifier: Modifier,
    content: String,
    onContentChange: (String) -> Unit,
    memo: MemoRepresentable?,
    uploadResources: List<ResourceEntity>,
    inputViewModel: MemoInputViewModel,
) {
    val userState = LocalUserState.current
    val host = userState.host.trimEnd('/')
    val knownTags = LocalMemos.current.tags.toList()
    val recordId = memo?.remoteId?.toLongOrNull()
    val files = memo?.resources?.mapNotNull { it.remoteId }.orEmpty()
    val uploadsBase = "$host/uploads/"
    val recordBase = recordId?.let { "$uploadsBase${CofferNode.DIR}/$it/" }

    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }
    // What this screen last handed out: a different `content` came from outside (a restored draft).
    var emitted by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()

    fun emit() {
        val out = CofferCompose.join(NoteParts(title, CofferCompose.bareToUrl(text, files, uploadsBase), tags.toList()))
        emitted = out
        onContentChange(out)
    }

    fun loadIntoEditor() {
        val doc = buildJsonObject {
            put("text", text)
            put("lang", Locale.getDefault().language.takeIf { it in setOf("uk", "ru") } ?: "en")
            put("dark", dark)
            if (recordId != null) put("record", buildJsonObject {
                put("node", CofferNode.NODE)
                put("id", recordId.toString())
            })
        }
        webView?.evaluateJavascript("window.cofferEditor && window.cofferEditor.load($doc)", null)
    }

    LaunchedEffect(content) {
        if (content == emitted) return@LaunchedEffect
        val parts = CofferCompose.split(recordBase?.let { CofferCompose.urlToBare(content, it) } ?: content)
        title = parts.title
        text = parts.text
        tags.clear()
        tags.addAll(parts.tags)
        emitted = content
        if (ready) loadIntoEditor()
    }

    Column(modifier.fillMaxHeight()) {
        TextField(
            value = title,
            onValueChange = { title = it.replace('\n', ' '); emit() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text(stringResource(R.string.coffer_note_title)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        val context = LocalContext.current
        val proxy = CofferEditorProxies.forHost(host) {
            CofferEditorProxy(userState.okHttpClient, host, proxiedPrefixes(context), File(context.cacheDir, "coffer-editor"))
        }
        // A dead renderer (killed for memory) is replaced by a fresh WebView instead of taking the app down.
        var generation by remember { mutableStateOf(0) }
        key(generation) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    ready = false
                    editorWebView(
                        ctx,
                        proxy,
                        onReady = { ready = true; loadIntoEditor() },
                        onChange = { md -> text = md; emit() },
                        onGone = { generation++ },
                    ).also { webView = it }
                },
                onRelease = { it.destroy() },
            )
        }

        TagChips(tags = tags, known = knownTags, onChange = { emit() })

        val images = uploadResources.filter { it.mimeType?.startsWith("image/") == true }
        val others = uploadResources.filterNot { it.mimeType?.startsWith("image/") == true }
        if (images.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.height(80.dp).padding(horizontal = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(images, key = { it.identifier }) { InputImage(resource = it, inputViewModel = inputViewModel) }
            }
        }
        if (others.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(others, key = { it.identifier }) { r ->
                    Attachment(resource = r, onRemove = { inputViewModel.deleteResource(r.identifier) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: MutableList<String>, known: List<String>, onChange: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    var dismissed by remember { mutableStateOf(false) }
    fun add(tag: String) {
        val t = tag.trim().removePrefix("#")
        if (t.isNotEmpty() && t !in tags) { tags.add(t); onChange() }
        draft = ""
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = { tags.remove(tag); onChange() },
                label = { Text("#$tag") },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
            )
        }
        Box {
            val suggestions = known.filter { it !in tags && draft.isNotEmpty() && it.startsWith(draft.removePrefix("#"), ignoreCase = true) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.replace(" ", ""); dismissed = false },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(draft) }),
            )
            // Not focusable: the keyboard stays with the field, so a new tag that shares a prefix
            // with a known one can still be typed; tapping outside hides the list.
            DropdownMenu(
                expanded = suggestions.isNotEmpty() && !dismissed,
                onDismissRequest = { dismissed = true },
                properties = PopupProperties(focusable = false),
            ) {
                suggestions.take(8).forEach { s -> DropdownMenuItem(text = { Text("#$s") }, onClick = { add(s) }) }
            }
        }
    }
}

/** The server path prefixes the bundled page needs forwarded (its `version.json`). */
private fun proxiedPrefixes(context: android.content.Context): List<String> =
    context.assets.open("coffer-editor/version.json").use { s ->
        Json.parseToJsonElement(s.bufferedReader().readText()).jsonObject["proxied"]!!.jsonArray
            .map { it.jsonPrimitive.content }
    }

@SuppressLint("SetJavaScriptEnabled")
private fun editorWebView(
    context: android.content.Context,
    proxy: CofferEditorProxy,
    onReady: () -> Unit,
    onChange: (String) -> Unit,
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
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

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
        addJavascriptInterface(CofferBridge(main, onReady, onChange), "CofferBridge")
        loadUrl(EDITOR_URL)
    }
}

/** What the editor page calls (`window.CofferBridge`); always answered on the main thread. */
class CofferBridge(private val main: Handler, private val ready: () -> Unit, private val change: (String) -> Unit) {
    @JavascriptInterface
    fun onReady() {
        main.post(ready)
    }

    @JavascriptInterface
    fun onChange(markdown: String) {
        main.post { change(markdown) }
    }
}
