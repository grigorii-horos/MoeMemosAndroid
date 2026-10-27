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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
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
    var ready by remember { mutableStateOf(false) }
    // The loader stays until the editor has drawn THIS note (the shared WebView may still show the last one).
    var shown by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    val dark = isSystemInDarkTheme()
    val theme = materialTokens()
    var tagFieldFocused by remember { mutableStateOf(false) }
    // While typing in the note the keyboard needs the room; the tags come back when it closes.
    val typing = WindowInsets.isImeVisible && !tagFieldFocused

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
            put("theme", buildJsonObject { theme.forEach { (k, v) -> put(k, v) } })
            if (recordId != null) put("record", buildJsonObject {
                put("node", CofferNode.NODE)
                put("id", recordId.toString())
            })
        }
        shown = false
        CofferEditorHost.load(doc.toString())
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            placeholder = { Text(stringResource(R.string.coffer_note_title), style = MaterialTheme.typography.headlineSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        val proxy = rememberCofferEditorProxy()
        val listener = remember {
            object : CofferEditorHost.Listener {
                override fun onReady() { ready = true; loadIntoEditor() }
                override fun onChange(markdown: String) { text = markdown; emit() }
                override fun onShown() { shown = true }
                // A dead renderer: a fresh WebView is built and attached instead of crashing the app.
                override fun onGone() { ready = false; generation++ }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            key(generation) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        CofferEditorHost.warm(ctx, proxy)
                        CofferEditorHost.attach(ctx, listener)
                    },
                    onRelease = { CofferEditorHost.detach(listener) },
                )
            }
            if (!shown) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }

        if (!typing) {
            TagChips(tags = tags, known = knownTags, onChange = { emit() }, onFocus = { tagFieldFocused = it })
        }

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
private fun TagChips(tags: MutableList<String>, known: List<String>, onChange: () -> Unit, onFocus: (Boolean) -> Unit) {
    var draft by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    fun add(tag: String) {
        val t = tag.trim().removePrefix("#")
        if (t.isNotEmpty() && t !in tags) { tags.add(t); onChange() }
        draft = ""
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            InputChip(
                selected = true,
                onClick = { tags.remove(tag); onChange() },
                label = { Text(tag) },
                leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) },
            )
        }
        if (!adding) {
            AssistChip(
                onClick = { adding = true },
                label = { Text(stringResource(R.string.coffer_add_tag)) },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        } else {
            Box {
                val focus = remember { FocusRequester() }
                val suggestions = known.filter { it !in tags && draft.isNotEmpty() && it.startsWith(draft.removePrefix("#"), ignoreCase = true) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.replace(" ", ""); dismissed = false },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.labelLarge,
                    leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null, Modifier.size(18.dp)) },
                    modifier = Modifier.width(180.dp).focusRequester(focus).onFocusChanged {
                        onFocus(it.isFocused)
                        if (!it.isFocused && draft.isEmpty()) adding = false
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add(draft) }),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
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
}

/** The app's Material colours as the page's theme tokens, so the editor wears the same palette. */
@Composable
private fun materialTokens(): Map<String, String> {
    val c = MaterialTheme.colorScheme
    fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)
    return mapOf(
        "--background" to c.surface.hex(),
        "--foreground" to c.onSurface.hex(),
        "--card" to c.surfaceContainer.hex(),
        "--card-foreground" to c.onSurface.hex(),
        "--popover" to c.surfaceContainerHigh.hex(),
        "--popover-foreground" to c.onSurface.hex(),
        "--muted" to c.surfaceContainerHighest.hex(),
        "--muted-foreground" to c.onSurfaceVariant.hex(),
        "--border" to c.outlineVariant.hex(),
        "--input" to c.outline.hex(),
        "--primary" to c.primary.hex(),
        "--primary-foreground" to c.onPrimary.hex(),
        "--secondary" to c.secondaryContainer.hex(),
        "--secondary-foreground" to c.onSecondaryContainer.hex(),
        "--accent" to c.secondaryContainer.hex(),
        "--accent-foreground" to c.onSecondaryContainer.hex(),
        "--ring" to c.primary.hex(),
        "--font-sans" to "Roboto, system-ui, sans-serif",
    )
}

/** The editor's request proxy for the current account — one per process (`CofferEditorProxies`). */
@Composable
private fun rememberCofferEditorProxy(): () -> CofferEditorProxy {
    val userState = LocalUserState.current
    val context = LocalContext.current
    val host = userState.host.trimEnd('/')
    return {
        CofferEditorProxies.forHost(host) {
            CofferEditorProxy(
                userState.okHttpClient,
                host,
                CofferEditorHost.proxiedPrefixes(context),
                File(context.cacheDir, "coffer-editor"),
            )
        }
    }
}

/**
 * Boots the editor WebView in the background as soon as a Coffer account's notes are on screen,
 * so the first note opens without the page assembling itself.
 */
@Composable
fun CofferEditorPrewarm() {
    val context = LocalContext.current
    val proxy = rememberCofferEditorProxy()
    LaunchedEffect(Unit) { CofferEditorHost.warm(context, proxy) }
}
