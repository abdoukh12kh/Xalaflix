
package eu.kanade.cloudstream3.xalaflix

import eu.kanade.cloudstream3.*
import eu.kanade.cloudstream3.api.*
import eu.kanade.cloudstream3.network.*
import eu.kanade.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.lang.Exception

class Xalaflix : MainAPI() {
    override var name = "Xalaflix"
    override var mainUrl = "https://xalaflix.io"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun search(query: String, page: Int): List<SearchResponse> {
        val url = "$mainUrl/page/$page/?s=${query.replace(" ", "+")}"
        return parseSearchPage(app.get(url).document)
    }

    private fun parseSearchPage(doc: Document) = doc.select(".movie-item, .serie-item").mapNotNull {
        val a = it.selectFirst("a") ?: return@mapNotNull null
        val title = it.selectFirst(".title")?.text()?.trim() ?: return@mapNotNull null
        val poster = it.selectFirst("img")?.let { e -> fixUrl(e.attr("src")) } ?: ""
        val href = fixUrl(a.attr("href"))
        val type = if (it.hasClass("serie-item")) TvType.TvSeries else TvType.Movie
        newSearchResponse(title, href, type) { posterUrl = poster }
    }

    override suspend fun getMainPage(page: Int): HomePageResponse {
        val sections = listOf(
            "Latest" to "$mainUrl/page/$page",
            "Trending" to "$mainUrl/category/trending/page/$page",
            "Movies" to "$mainUrl/category/movies/page/$page",
            "Series" to "$mainUrl/category/series/page/$page"
        )
        val lists = sections.mapNotNull { (title, url) ->
            val items = parseSearchPage(app.get(url).document)
            if (items.isNotEmpty()) HomePageList(title, items) else null
        }
        return HomePageResponse(lists)
    }

    override suspend fun load(url: String): LoadResponse? {
        val resp = app.get(url)
        if (resp.status.value != 200) throw Exception("Page not found or blocked")

        val doc = resp.document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val desc = doc.select("p.description, .entry-content > p").firstOrNull()?.text()?.trim()
        val poster = doc.selectFirst(".poster img")?.let { fixUrl(it.attr("src")) }
        val genres = doc.select(".genres a").map { it.text().trim() }
        val year = doc.selectFirst(".release-date, .year")?.text()?.filter(Char::isDigit)?.toIntOrNull()

        val epEls = doc.select(".episode-list a")
        return if (epEls.isNotEmpty()) {
            val eps = epEls.map { Episode(fixUrl(it.attr("href")), it.text().trim()) }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries) {
                posterUrl = poster
                plot = desc
                this.year = year
                tags = genres
                episodes = eps
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "") {
                posterUrl = poster
                plot = desc
                this.year = year
                tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        url: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit
    ): List<VideoStream> {
        val resp = app.get(url)
        if (resp.status.value != 200) throw Exception("Video page not accessible")
        val doc = resp.document

        val streams = mutableListOf<VideoStream>()

        doc.select("video source").forEach {
            val src = fixUrl(it.attr("src"))
            val label = it.attr("label") ?: it.attr("res") ?: "Default"
            val quality = Regex("(\d{3,4})[pP]").find(label)?.value ?: label
            streams.add(VideoStream(src, quality))
        }

        doc.select("track[kind=subtitles]").forEach {
            val subUrl = fixUrl(it.attr("src"))
            val lang = it.attr("label") ?: "Subtitle"
            subtitleCallback(SubtitleFile(lang, subUrl))
        }

        if (streams.isEmpty()) {
            doc.select("iframe").mapNotNull { it.attr("src") }.forEach { iframeUrl ->
                val hosterPage = app.get(fixUrl(iframeUrl)).document
                hosterPage.select("source, video, iframe").forEach { src ->
                    src.attr("src")?.let { link ->
                        streams.add(VideoStream(fixUrl(link), "iframe"))
                    }
                }
            }
        }

        if (streams.isEmpty()) throw Exception("No video links found")
        return streams
    }

    private fun fixUrl(u: String): String {
        return if (u.startsWith("http")) u
        else mainUrl.trimEnd('/') + "/" + u.trimStart('/')
    }

    override suspend fun subtitles(url: String) = emptyList<SubtitleFile>()
}
