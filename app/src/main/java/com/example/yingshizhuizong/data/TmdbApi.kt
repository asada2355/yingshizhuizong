package com.example.yingshizhuizong.data

import android.util.Log
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

/** 通过 The Movie Database (TMDB) 查询真实海报图与预告片 */
object TmdbApi {

    /**
     * 免费的 TMDB API Key：https://www.themoviedb.org/settings/api
     * 注册后在「API」页面申请，填入下方字符串即可自动抓取海报与预告片。
     * 留空则跳过网络查询（界面显示占位图，预告片需手动填写链接）。
     */
    const val API_KEY = "36a035d159e2942669ff7485f3d9e7b6"

    private const val BASE = "https://api.themoviedb.org/3"
    private const val TAG = "TmdbApi"

    /** TMDB 里的一条视频（只保留 YouTube 来源，便于在内嵌播放器里播放） */
    data class Trailer(
        val key: String,
        val name: String,
        val type: String,
        val official: Boolean,
        val language: String,
    )

    /** 按片名搜索并返回海报 URL（w500），失败或无结果返回 null */
    suspend fun searchPoster(type: WatchType, title: String, year: Int?): String? =
        withContext(Dispatchers.IO) {
            if (API_KEY.isBlank()) {
                Log.w(TAG, "未配置 API_KEY，跳过海报查询")
                return@withContext null
            }
            val path = searchFirst(type, title, year)?.optString("poster_path").orEmpty()
            if (path.isBlank() || path == "null") {
                Log.w(TAG, "TMDB 结果无海报：$title")
                null
            } else {
                "https://image.tmdb.org/t/p/w500$path".also {
                    Log.i(TAG, "命中海报：$title -> $it")
                }
            }
        }

    /**
     * 查找预告片：按片名搜索 → 取作品 id → 查询该作品的视频列表。
     * 依次尝试 [titles] 里的片名（原名 / 中文名），命中即返回。
     */
    suspend fun fetchTrailer(type: WatchType, titles: List<String>, year: Int?): Trailer? =
        withContext(Dispatchers.IO) {
            if (API_KEY.isBlank()) {
                Log.w(TAG, "未配置 API_KEY，跳过预告片查询")
                return@withContext null
            }
            val endpoint = endpointOf(type)
            for (title in titles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                val id = searchFirst(type, title, year)?.optInt("id", 0) ?: 0
                if (id <= 0) {
                    Log.w(TAG, "预告片：未找到作品 $title")
                    continue
                }
                val candidates = mutableListOf<Trailer>()
                candidates += videos(endpoint, id, "zh-CN")
                candidates += videos(endpoint, id, "en-US")
                val best = pickBest(candidates)
                if (best != null) {
                    Log.i(TAG, "命中预告片：$title -> ${best.key}（${best.name}）")
                    return@withContext best
                }
                Log.w(TAG, "预告片：$title 没有可用的 YouTube 视频")
            }
            null
        }

    private fun endpointOf(type: WatchType) = if (type == WatchType.MOVIE) "movie" else "tv"

    /** TMDB 在线搜索结果（电影 / 剧集通用） */
    data class SearchItem(
        val tmdbId: Int,
        val title: String,
        val originalTitle: String,
        val type: WatchType,
        val releaseDate: Long?,
        val posterUrl: String,
        val rating: Float,
        val overview: String,
        /** 根据 TMDB genre_ids 自动推断的分类 */
        val category: WatchCategory = WatchCategory.OTHER,
    )

    /**
     * 在线搜索电影 / 剧集，支持中文片名。
     * 返回 null 表示请求失败（网络或接口错误），空列表表示 TMDB 确实没有结果。
     */
    suspend fun searchMulti(query: String): List<SearchItem>? = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank()) {
            Log.w(TAG, "未配置 API_KEY，跳过在线搜索")
            return@withContext null
        }
        val keyword = query.trim()
        if (keyword.isEmpty()) return@withContext emptyList()
        val url = "$BASE/search/multi?api_key=$API_KEY" +
            "&query=${URLEncoder.encode(keyword, "UTF-8")}" +
            "&language=zh-CN&include_adult=false"
        val json = getJson(url) ?: return@withContext null
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        val list = mutableListOf<SearchItem>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val mediaType = o.optString("media_type")
            val type = when (mediaType) {
                "movie" -> WatchType.MOVIE
                "tv" -> WatchType.TV_SERIES
                else -> null
            } ?: continue
            val isMovie = type == WatchType.MOVIE
            val title = o.optString(if (isMovie) "title" else "name").trim()
            if (title.isEmpty()) continue
            val original = o.optString(if (isMovie) "original_title" else "original_name").trim()
            val poster = o.optString("poster_path").trim()
            val dateRaw = o.optString(if (isMovie) "release_date" else "first_air_date").trim()
            val genreIds = o.optJSONArray("genre_ids")?.let { arr ->
                (0 until arr.length()).map { arr.optInt(it) }
            }.orEmpty()
            list += SearchItem(
                tmdbId = o.optInt("id", 0),
                title = title,
                originalTitle = original,
                type = type,
                releaseDate = parseTmdbDate(dateRaw),
                posterUrl = if (poster.isEmpty() || poster == "null") {
                    ""
                } else {
                    "https://image.tmdb.org/t/p/w500$poster"
                },
                rating = o.optDouble("vote_average", 0.0).toFloat(),
                overview = o.optString("overview").trim(),
                category = WatchCategory.fromTmdbIds(genreIds),
            )
        }
        list
    }

    /** TMDB 日期是 yyyy-MM-dd，转成本地当天 0 点的毫秒 */
    private fun parseTmdbDate(raw: String): Long? {
        if (raw.isEmpty() || raw == "null") return null
        return try {
            LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** TMDB 作品详情里的演员 */
    data class CastMember(val name: String, val character: String)

    /** 作品详情：简介 + 导演 / 制片 / 主演 */
    data class Details(
        val overview: String,
        val directors: List<String>,
        val producers: List<String>,
        val cast: List<CastMember>,
        /** 根据 TMDB genres 推断的分类 */
        val category: WatchCategory,
    )

    /** 按片名解析 TMDB 作品 id（依次尝试多个片名），失败返回 null */
    suspend fun findId(type: WatchType, titles: List<String>, year: Int?): Int? =
        withContext(Dispatchers.IO) {
            if (API_KEY.isBlank()) return@withContext null
            for (title in titles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                val id = searchFirst(type, title, year)?.optInt("id", 0) ?: 0
                if (id > 0) return@withContext id
            }
            null
        }

    /**
     * 只推断分类：按片名搜索后读取结果里的 genre_ids（不额外请求作品详情）。
     * 找不到或类型无法识别时返回 null。
     */
    suspend fun searchCategory(type: WatchType, titles: List<String>, year: Int?): WatchCategory? =
        withContext(Dispatchers.IO) {
            if (API_KEY.isBlank()) return@withContext null
            for (title in titles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                val result = searchFirst(type, title, year) ?: continue
                val genreIds = result.optJSONArray("genre_ids")?.let { arr ->
                    (0 until arr.length()).map { arr.optInt(it) }
                }.orEmpty()
                val category = WatchCategory.fromTmdbIds(genreIds)
                if (category != WatchCategory.OTHER) return@withContext category
            }
            null
        }

    /** 作品详情 + 演职员：简介、导演、制片、主演（最多 10 位） */
    suspend fun fetchDetails(type: WatchType, id: Int): Details? = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank()) return@withContext null
        val url = "$BASE/${endpointOf(type)}/$id?api_key=$API_KEY" +
            "&language=zh-CN&append_to_response=credits"
        val json = getJson(url) ?: return@withContext null
        val credits = json.optJSONObject("credits")
        val directors = mutableListOf<String>()
        val producers = mutableListOf<String>()
        val crew = credits?.optJSONArray("crew")
        if (crew != null) {
            for (i in 0 until crew.length()) {
                val o = crew.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                when (o.optString("job")) {
                    "Director" -> if (name !in directors) directors.add(name)
                    "Producer" -> if (name !in producers) producers.add(name)
                }
            }
        }
        // 剧集没有 Director 岗位，用 created_by 作为主创
        val created = json.optJSONArray("created_by")
        if (created != null) {
            for (i in 0 until created.length()) {
                val name = created.optJSONObject(i)?.optString("name")?.trim().orEmpty()
                if (name.isNotEmpty() && name !in directors) directors.add(name)
            }
        }
        val cast = mutableListOf<CastMember>()
        val castArr = credits?.optJSONArray("cast")
        if (castArr != null) {
            for (i in 0 until castArr.length()) {
                if (cast.size >= 10) break
                val o = castArr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                cast.add(CastMember(name, o.optString("character").trim()))
            }
        }
        val genreIds = json.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optInt("id", 0)?.takeIf { it > 0 }
            }
        }.orEmpty()
        Details(
            overview = json.optString("overview").trim(),
            directors = directors,
            producers = producers.take(5),
            cast = cast,
            category = WatchCategory.fromTmdbIds(genreIds),
        )
    }

    /** 搜索并返回最匹配的一条结果：先带年份精确搜索，没有再放宽年份 */
    private fun searchFirst(type: WatchType, title: String, year: Int?): JSONObject? {
        val endpoint = endpointOf(type)
        val yearParam = if (year != null && year > 0) {
            if (type == WatchType.MOVIE) "&year=$year" else "&first_air_date_year=$year"
        } else {
            ""
        }
        val base = "$BASE/search/$endpoint?api_key=$API_KEY" +
            "&query=${URLEncoder.encode(title, "UTF-8")}" +
            "&language=zh-CN&include_adult=false"
        return firstResult("$base$yearParam") ?: if (yearParam.isEmpty()) null else firstResult(base)
    }

    private fun firstResult(url: String): JSONObject? {
        val results = getJson(url)?.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        return results.optJSONObject(0)
    }

    /** 读取某部作品的视频列表，只保留合法的 YouTube 条目 */
    private fun videos(endpoint: String, id: Int, language: String): List<Trailer> {
        val url = "$BASE/$endpoint/$id/videos?api_key=$API_KEY&language=$language"
        val arr = getJson(url)?.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<Trailer>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.optString("site").equals("YouTube", ignoreCase = true)) continue
            val key = o.optString("key").trim()
            if (key.length != 11) continue
            list += Trailer(
                key = key,
                name = o.optString("name").trim(),
                type = o.optString("type"),
                official = o.optBoolean("official"),
                language = language,
            )
        }
        return list
    }

    /** 官方预告 > 预告 > 先导预告 > 其他花絮；同分时保留先出现的（中文优先） */
    private fun pickBest(candidates: List<Trailer>): Trailer? {
        val scored = candidates.distinctBy { it.key }.map { it to score(it) }
        val best = scored.maxOfOrNull { it.second } ?: return null
        return scored.firstOrNull { it.second == best }?.first
    }

    private fun score(trailer: Trailer): Int {
        var score = when (trailer.type.lowercase()) {
            "trailer" -> 100
            "teaser" -> 60
            "clip" -> 30
            "featurette", "behind the scenes" -> 20
            else -> 10
        }
        if (trailer.official) score += 40
        return score
    }

    private fun getJson(url: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            connection = conn
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "TMDB 返回 HTTP $code：$url")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (e: Exception) {
            Log.w(TAG, "TMDB 请求异常：$url", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
