package me.mudkip.moememos.data.coffer

import com.skydoves.sandwich.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import me.mudkip.moememos.data.api.Rfc3339InstantSerializer
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant

/**
 * The fork's one node, `notes:notes`, as Coffer's REST serves it (measured on prod 2026-10-26):
 * the list is a bare array, `created_at` only comes when named in `_fields`, timestamps are ISO
 * strings, an empty multiple file field is `null`, `/uploads/<name>` needs the Bearer token.
 */
object CofferNode {
    const val NODE = "notes:notes"
    /** The note's upload directory under `/uploads/`, `<dir>/<id>/<name>` (Coffer's record-scoped uploads). */
    const val DIR = "notes/notes"
    const val LIST = "api/node/notes"
    const val ITEM = "api/item/notes/notes/{id}"
    const val TITLE = "title"
    const val TEXT = "text"
    const val TAGS = "tags"
    const val IMAGES = "gallery"
    const val FILES = "files"
    const val LIST_FIELDS = "$TITLE,$TEXT,$TAGS,$IMAGES,$FILES,created_at,updated_at"
}

@Serializable
data class CofferFile(val name: String, val mime: String? = null, val size: Long? = null)

@Serializable
data class CofferNote(
    val id: Long,
    val title: String? = null,
    val text: String? = null,
    val tags: List<String>? = null,
    val gallery: List<CofferFile>? = null,
    val files: List<CofferFile>? = null,
    @SerialName("created_at")
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createdAt: Instant? = null,
    @SerialName("updated_at")
    @Serializable(with = Rfc3339InstantSerializer::class)
    val updatedAt: Instant? = null,
)

@Serializable
data class CofferMe(val id: Long, val login: String, val displayName: String? = null)

@Serializable
data class CofferHealth(val status: String? = null, val startedAt: String? = null)

interface CofferApi {
    @GET(CofferNode.LIST)
    suspend fun listNotes(@Query("_fields") fields: String = CofferNode.LIST_FIELDS): ApiResponse<List<CofferNote>>

    @POST(CofferNode.LIST)
    suspend fun createNote(@Body body: JsonObject): ApiResponse<CofferNote>

    @PATCH(CofferNode.ITEM)
    suspend fun updateNote(@Path("id") id: Long, @Body body: JsonObject): ApiResponse<CofferNote>

    @DELETE(CofferNode.ITEM)
    suspend fun deleteNote(@Path("id") id: Long): ApiResponse<Unit>

    @Multipart
    @POST("api/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Query("node") node: String? = null,
        @Query("id") id: Long? = null,
    ): ApiResponse<CofferFile>

    @GET("api/auth/me")
    suspend fun me(): ApiResponse<CofferMe>

    @GET("health")
    suspend fun health(): ApiResponse<CofferHealth>
}
