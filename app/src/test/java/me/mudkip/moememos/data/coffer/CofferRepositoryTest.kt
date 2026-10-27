package me.mudkip.moememos.data.coffer

import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemosAccount
import me.mudkip.moememos.data.model.MemoVisibility
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private class FakeApi(var notes: List<CofferNote> = emptyList(), val labelsServer: Boolean = true) : CofferApi {
    val listed = mutableListOf<String>()
    val bodies = mutableListOf<JsonObject>()
    val deleted = mutableListOf<Long>()
    private fun echo(id: Long) = CofferNote(id = id, updatedAt = Instant.EPOCH)

    override suspend fun listNotes(fields: String): ApiResponse<List<CofferNote>> {
        listed += fields
        // An old server refuses a field it does not know (400 bad_filter).
        if (!labelsServer && "labels" in fields.split(',')) {
            return ApiResponse.Failure.Error(retrofit2.Response.error<Any>(400, "{}".toResponseBody(null)))
        }
        return ApiResponse.Success(notes)
    }
    override suspend fun createNote(body: JsonObject): ApiResponse<CofferNote> { bodies += body; return ApiResponse.Success(echo(9)) }
    override suspend fun updateNote(id: Long, body: JsonObject): ApiResponse<CofferNote> { bodies += body; return ApiResponse.Success(echo(id)) }
    override suspend fun deleteNote(id: Long): ApiResponse<Unit> { deleted += id; return ApiResponse.Success(Unit) }
    var uploadedTo: Pair<String?, Long?>? = null
    override suspend fun upload(file: MultipartBody.Part, node: String?, id: Long?): ApiResponse<CofferFile> {
        uploadedTo = node to id
        return ApiResponse.Success(CofferFile("notes/notes/5/u.png", "image/png"))
    }
    val favorites = mutableListOf<Pair<Long, Boolean>>()
    override suspend fun setFavorite(id: Long, body: JsonObject): ApiResponse<CofferActivityResponse> {
        val fav = body["favorite"]!!.jsonPrimitive.content.toBoolean()
        favorites += id to fav
        return ApiResponse.Success(CofferActivityResponse(CofferActivity(fav)))
    }
    override suspend fun me() = ApiResponse.Success(CofferMe(1, "o"))
    override suspend fun health() = ApiResponse.Success(CofferHealth("ok", "x"))
}

class CofferRepositoryTest {
    private val account = Account.Coffer(MemosAccount(host = "https://c.lan/", accessToken = "t"))
    private fun repo(api: FakeApi) = CofferRepository(api, account)

    @Test
    fun listMapsANoteToAMemo() = runTest {
        val api = FakeApi(listOf(CofferNote(5, "T", "b", listOf("x"), listOf(CofferFile("a.jpg")), listOf(CofferFile("d.pdf")))))
        val memo = (repo(api).listMemos() as ApiResponse.Success).data.single()
        assertEquals("5", memo.remoteId)
        assertEquals("# T\n\nb\n\n#x", memo.content)
        assertEquals(listOf("a.jpg", "d.pdf"), memo.resources.map { it.filename })
        assertEquals("https://c.lan/uploads/a.jpg", memo.resources.first().uri)
        assertEquals("image/jpeg", memo.resources.first().mimeType)
    }

    @Test
    fun createSplitsContent() = runTest {
        val api = FakeApi()
        repo(api).createMemo("# T\nb #x", MemoVisibility.PRIVATE, emptyList())
        val body = api.bodies.single()
        assertEquals("T", body["title"]!!.jsonPrimitive.content)
        assertEquals("b", body["text"]!!.jsonPrimitive.content)
        assertEquals(listOf("x"), body["labels"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content })
    }

    @Test
    fun createRoutesResourcesByMime() = runTest {
        val api = FakeApi()
        repo(api).createMemo("t", MemoVisibility.PRIVATE, listOf("p.png", "h.heic", "d.pdf"))
        val body = api.bodies.single()
        fun names(k: String) = body[k]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("p.png", "h.heic"), names("gallery"))
        assertEquals(listOf("d.pdf"), names("files"))
    }

    @Test
    fun updateWithResourcesOnlyPatchesFileFields() = runTest {
        val api = FakeApi()
        repo(api).updateMemo("5", resourceRemoteIds = listOf("p.png"))
        assertEquals(setOf("gallery", "files"), api.bodies.single().keys)
    }

    @Test
    fun updateWithContentOnlyPatchesTextFields() = runTest {
        val api = FakeApi()
        repo(api).updateMemo("5", content = "# T\nb")
        assertEquals(setOf("title", "text", "labels"), api.bodies.single().keys)
    }

    @Test
    fun pinnedIsTheFavorite() = runTest {
        val api = FakeApi(listOf(CofferNote(5, activity = CofferActivity(favorite = true))))
        assertTrue((repo(api).listMemos() as ApiResponse.Success).data.single().pinned)
        val res = repo(api).updateMemo("5", pinned = true, archived = true)
        assertTrue((res as ApiResponse.Success).data.pinned)
        assertEquals(listOf(5L to true), api.favorites)
        assertEquals(emptySet<String>(), api.bodies.single().keys)
    }

    @Test
    fun visibilityMapsToCofferPrivateOrPublic() = runTest {
        val api = FakeApi(listOf(CofferNote(5, visibility = "private"), CofferNote(6)))
        val memos = (repo(api).listMemos() as ApiResponse.Success).data
        assertEquals(listOf(MemoVisibility.PRIVATE, MemoVisibility.PUBLIC), memos.map { it.visibility })
        repo(api).createMemo("t", MemoVisibility.PRIVATE, emptyList())
        assertEquals("private", api.bodies.last()["_visibility"]!!.jsonPrimitive.content)
        repo(api).createMemo("t", MemoVisibility.PROTECTED, emptyList())
        assertEquals(JsonNull, api.bodies.last()["_visibility"])
        // An update only ever opens a note up: private is never sent again.
        repo(api).updateMemo("5", visibility = MemoVisibility.PRIVATE)
        assertEquals(emptySet<String>(), api.bodies.last().keys)
        repo(api).updateMemo("5", visibility = MemoVisibility.PUBLIC)
        assertEquals(JsonNull, api.bodies.last()["_visibility"])
    }

    @Test
    fun bareNamesBecomeUrlsAndFilenamesAreShort() = runTest {
        val api = FakeApi(listOf(CofferNote(5, "T", "![](a.jpg)", gallery = listOf(CofferFile("notes/notes/5/a.jpg")))))
        val memo = (repo(api).listMemos() as ApiResponse.Success).data.single()
        assertEquals("# T\n\n![](https://c.lan/uploads/notes/notes/5/a.jpg)", memo.content)
        assertEquals("a.jpg", memo.resources.single().filename)
    }

    @Test
    fun ownUrlsGoBackToBareNames() = runTest {
        val api = FakeApi()
        repo(api).updateMemo("5", content = "# T\n![](https://c.lan/uploads/notes/notes/5/a.jpg) ![](https://c.lan/uploads/notes/notes/6/b.jpg)")
        assertEquals("![](a.jpg) ![](https://c.lan/uploads/notes/notes/6/b.jpg)", api.bodies.single()["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun uploadForAKnownNoteGoesToItsDirectory() = runTest {
        val api = FakeApi()
        val res = repo(api).createResource("u.png", null, 1, { "x".byteInputStream() }, memoRemoteId = "5")
        assertEquals("notes:notes" to 5L, api.uploadedTo)
        assertEquals("u.png", (res as ApiResponse.Success).data.filename)
        repo(api).createResource("u.png", null, 1, { "x".byteInputStream() }, memoRemoteId = null)
        assertEquals(null to null, api.uploadedTo)
    }

    @Test
    fun deleteTrashesById() = runTest {
        val api = FakeApi()
        repo(api).deleteMemo("5")
        assertEquals(listOf(5L), api.deleted)
    }

    // The server renamed `tags` to `labels` ([{text}] or [{key}] items); the fork must work with
    // both versions across the deploy.
    @Test
    fun labelsAreReadAsTextsOrKeys() = runTest {
        val api = FakeApi(listOf(CofferNote(5, "T", "b", labels = listOf(CofferLabel(text = "a"), CofferLabel(key = "k")))))
        val memo = (repo(api).listMemos() as ApiResponse.Success).data.single()
        assertEquals(listOf("a", "k"), memo.tags)
        assertEquals("# T\n\nb\n\n#a #k", memo.content)
    }

    @Test
    fun anOldServerFallsBackToTagsAndKeepsWritingThem() = runTest {
        val api = FakeApi(listOf(CofferNote(5, "T", "b", tags = listOf("x"))), labelsServer = false)
        val r = repo(api)
        val memo = (r.listMemos() as ApiResponse.Success).data.single()
        assertEquals(listOf("x"), memo.tags)
        assertEquals(2, api.listed.size) // labels refused, then tags
        r.updateMemo("5", content = "# T\nb #y")
        assertEquals(listOf("y"), api.bodies.last()["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}
