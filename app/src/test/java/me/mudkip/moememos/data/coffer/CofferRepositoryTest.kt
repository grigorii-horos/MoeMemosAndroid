package me.mudkip.moememos.data.coffer

import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemosAccount
import me.mudkip.moememos.data.model.MemoVisibility
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private class FakeApi(var notes: List<CofferNote> = emptyList()) : CofferApi {
    val bodies = mutableListOf<JsonObject>()
    val deleted = mutableListOf<Long>()
    private fun echo(id: Long) = CofferNote(id = id, updatedAt = Instant.EPOCH)

    override suspend fun listNotes(fields: String) = ApiResponse.Success(notes)
    override suspend fun createNote(body: JsonObject): ApiResponse<CofferNote> { bodies += body; return ApiResponse.Success(echo(9)) }
    override suspend fun updateNote(id: Long, body: JsonObject): ApiResponse<CofferNote> { bodies += body; return ApiResponse.Success(echo(id)) }
    override suspend fun deleteNote(id: Long): ApiResponse<Unit> { deleted += id; return ApiResponse.Success(Unit) }
    override suspend fun upload(file: MultipartBody.Part) = ApiResponse.Success(CofferFile("u.png", "image/png"))
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
        assertEquals(listOf("x"), body["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
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
        assertEquals(setOf("title", "text", "tags"), api.bodies.single().keys)
    }

    @Test
    fun pinnedOnlySendsAnEmptyPatch() = runTest {
        val api = FakeApi()
        val res = repo(api).updateMemo("5", pinned = true, archived = true)
        assertTrue(res is ApiResponse.Success)
        assertEquals(emptySet<String>(), api.bodies.single().keys)
    }

    @Test
    fun deleteTrashesById() = runTest {
        val api = FakeApi()
        repo(api).deleteMemo("5")
        assertEquals(listOf(5L), api.deleted)
    }
}
