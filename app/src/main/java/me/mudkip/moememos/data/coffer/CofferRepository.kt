package me.mudkip.moememos.data.coffer

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.mapSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.data.model.MemoLocation
import me.mudkip.moememos.data.model.MemoLocationChange
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.Resource
import me.mudkip.moememos.data.model.User
import me.mudkip.moememos.data.repository.InputStreamRequestBody
import me.mudkip.moememos.data.repository.RemoteRepository
import okhttp3.MediaType
import okhttp3.MultipartBody
import java.io.InputStream
import java.net.URLConnection
import java.time.Instant

/**
 * MoeMemos' remote seam over Coffer's REST: one memo = one `notes:notes` record. The memo text is
 * split into title/text/tags and joined back by [CofferCompose]; attachments are the record's
 * `gallery` (images) and `files`. Pinned, archived, visibility and location are not stored.
 */
class CofferRepository(
    private val api: CofferApi,
    private val account: Account.Coffer,
) : RemoteRepository() {
    private val host = account.info.host.trimEnd('/')

    private fun resource(f: CofferFile, date: Instant) = Resource(
        remoteId = f.name,
        date = date,
        filename = f.name,
        mimeType = f.mime ?: mimeOf(f.name),
        uri = "$host/uploads/${f.name}",
    )

    private fun memo(n: CofferNote): Memo {
        val date = n.createdAt ?: n.updatedAt ?: Instant.now()
        val tags = n.tags.orEmpty()
        return Memo(
            remoteId = n.id.toString(),
            content = CofferCompose.join(NoteParts(n.title.orEmpty(), n.text.orEmpty(), tags)),
            date = date,
            pinned = false,
            visibility = MemoVisibility.PRIVATE,
            resources = (n.gallery.orEmpty() + n.files.orEmpty()).map { resource(it, date) },
            tags = tags,
            updatedAt = n.updatedAt,
        )
    }

    /** Only the parts that were passed: a `null` leaves those fields untouched on the server. */
    private fun body(content: String?, resourceNames: List<String>?): JsonObject = buildJsonObject {
        content?.let { CofferCompose.split(it) }?.let { p ->
            put(CofferNode.TITLE, p.title)
            put(CofferNode.TEXT, p.text)
            put(CofferNode.TAGS, JsonArray(p.tags.map(::JsonPrimitive)))
        }
        resourceNames?.let { names ->
            val (images, files) = names.partition { mimeOf(it)?.startsWith("image/") == true }
            put(CofferNode.IMAGES, files(images))
            put(CofferNode.FILES, files(files))
        }
    }

    private fun files(names: List<String>) = JsonArray(names.map { buildJsonObject { put("name", it) } })

    override suspend fun listMemos(): ApiResponse<List<Memo>> =
        api.listNotes().mapSuccess { map(::memo) }

    override suspend fun listArchivedMemos(): ApiResponse<List<Memo>> = ApiResponse.Success(emptyList())

    override suspend fun listWorkspaceMemos(pageSize: Int, pageToken: String?): ApiResponse<Pair<List<Memo>, String?>> =
        ApiResponse.Success(emptyList<Memo>() to null)

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        tags: List<String>?,
        createdAt: Instant?,
        location: MemoLocation?,
    ): ApiResponse<Memo> = api.createNote(body(content, resourceRemoteIds)).mapSuccess { memo(this) }

    override suspend fun updateMemo(
        remoteId: String,
        content: String?,
        resourceRemoteIds: List<String>?,
        visibility: MemoVisibility?,
        tags: List<String>?,
        pinned: Boolean?,
        archived: Boolean?,
        location: MemoLocationChange?,
    ): ApiResponse<Memo> =
        api.updateNote(remoteId.toLong(), body(content, resourceRemoteIds)).mapSuccess { memo(this) }

    override suspend fun deleteMemo(remoteId: String): ApiResponse<Unit> = api.deleteNote(remoteId.toLong())

    override suspend fun listResources(): ApiResponse<List<Resource>> =
        api.listNotes().mapSuccess {
            flatMap { n -> (n.gallery.orEmpty() + n.files.orEmpty()).map { resource(it, n.createdAt ?: Instant.now()) } }
        }

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String?,
    ): ApiResponse<Resource> {
        val part = MultipartBody.Part.createFormData(
            "file",
            filename,
            InputStreamRequestBody(type, contentLength, openInputStream),
        )
        return api.upload(part).mapSuccess { resource(this, Instant.now()) }
    }

    // An upload no record names is harmless; Coffer has no route to delete one.
    override suspend fun deleteResource(remoteId: String): ApiResponse<Unit> = ApiResponse.Success(Unit)

    override suspend fun getCurrentUser(): ApiResponse<User> =
        api.me().mapSuccess { User(identifier = id.toString(), name = displayName ?: login) }

    companion object {
        /** Coffer keeps the extension of an upload's name, so it is the MIME source here. */
        fun mimeOf(name: String): String? = URLConnection.guessContentTypeFromName(name)
            ?: name.substringAfterLast('.', "").lowercase().takeIf { it in PHONE_IMAGES }?.let { "image/$it" }

        // What a phone camera writes that the JVM's table may not know.
        private val PHONE_IMAGES = setOf("webp", "heic", "heif", "avif")
    }
}
