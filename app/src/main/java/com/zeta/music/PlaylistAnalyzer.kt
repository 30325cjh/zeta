package com.zeta.music

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class PlaylistTrack(
    val title: String,
    val artist: String,
    val thumbnail: String
)

object PlaylistAnalyzer {
    fun analyze(client: OkHttpClient, apiKey: String, playlistUrl: String): List<PlaylistTrack> {
        val prompt = """
            Open and inspect this publicly accessible YouTube Music or YouTube playlist page using URL context:
            $playlistUrl

            Extract the song entries that are actually present on the playlist page.
            Do not invent missing songs. Return at most 100 entries, in the original playlist order.
            Return ONLY a JSON array. Each item must have exactly these string fields:
            title, artist, thumbnail.
            Use an empty string when artist or thumbnail is unavailable.
            Do not provide audio URLs, download links, or copied audio.
            If the playlist cannot be accessed, return [].
        """.trimIndent()
        val requestJson = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts",
                    JSONArray().put(JSONObject().put("text", prompt)))
            ))
            .put("tools", JSONArray().put(JSONObject().put("url_context", JSONObject())))
            .put("generationConfig", JSONObject().put("temperature", 0.1).put("responseMimeType", "application/json"))
        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body)
            .build()
        val responseText = client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: error("Gemini 응답이 비어 있습니다.")
            if (!response.isSuccessful) error("Gemini API 오류 HTTP " + response.code + ": " + raw.take(300))
            raw
        }
        val root = JSONObject(responseText)
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            ?: error("Gemini가 플레이리스트 분석 결과를 반환하지 않았습니다.")
        val urlMetadata = candidate.optJSONObject("urlContextMetadata")?.optJSONArray("urlMetadata")
        var retrieved = false
        if (urlMetadata != null) {
            for (i in 0 until urlMetadata.length()) {
                if (urlMetadata.optJSONObject(i)?.optString("urlRetrievalStatus") == "URL_RETRIEVAL_STATUS_SUCCESS") retrieved = true
            }
        }
        if (!retrieved) error("Gemini가 플레이리스트 페이지를 읽지 못했습니다. 공개 플레이리스트인지 확인하세요.")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: error("Gemini 응답 형식이 올바르지 않습니다.")
        val text = buildString {
            for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }.trim()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) error("플레이리스트 곡 목록을 읽지 못했습니다.")
        val array = JSONArray(text.substring(start, end + 1))
        val tracks = ArrayList<PlaylistTrack>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            tracks.add(PlaylistTrack(title, item.optString("artist").trim(), item.optString("thumbnail").trim()))
        }
        return tracks
    }
}
