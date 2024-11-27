package it.dogior.hadEnough

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class CalcioStreaming : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://calciostreaming.day"
    override var name = "CalcioStreaming"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,

        )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/partite-streaming.html").document
        val sections = document.select("div.slider-title").filter {it -> it.select("div.item").isNotEmpty()}

        if (sections.isEmpty()) throw ErrorLoadingException()

        return HomePageResponse(sections.map { it ->
            val categoryName = it.selectFirst("h2 > strong")!!.text()
            val shows = it.select("div.item").map {
                val href = it.selectFirst("a")!!.attr("href")
                val name = it.selectFirst("a > div > h1")!!.text()
                val posterUrl = fixUrl(it.selectFirst("a > img")!!.attr("src"))
                LiveSearchResponse(
                    name,
                    href,
                    this@CalcioStreaming.name,
                    TvType.Live,
                    posterUrl,
                )
            }
            HomePageList(
                categoryName,
                shows,
                isHorizontalImages = true
            )

        })

    }


    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document
        val poster =  fixUrl(document.select("#title-single > div").attr("style").substringAfter("url(").substringBeforeLast(")"))
        val matchStart = document.select("div.info-wrap > div").textNodes().joinToString("").trim()
        return LiveStreamLoadResponse(
            document.selectFirst(" div.info-t > h1")!!.text(),
            url,
            this.name,
            url,
            poster,
            plot = matchStart
        )
    }

    private fun matchFound(document: Document) : Boolean {
        return Regex(""""((.|\n)*?).";""").containsMatchIn(
            getAndUnpack(
                document.toString()
            ))
    }

    private fun getUrl(document: Document):String{
        return Regex(""""((.|\n)*?).";""").find(
            getAndUnpack(
                document.toString()
            ))!!.value.replace("""src="""", "").replace(""""""", "").replace(";", "")
    }

    private suspend fun extractVideoLinks(
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        document.select("button.btn").forEach { button ->
            var link = button.attr("data-link")
            var oldLink = link
            var videoNotFound = true
            while (videoNotFound) {
                val doc = app.get(link).document
                link = doc.selectFirst("iframe")?.attr("src") ?: break
                val newPage = app.get(fixUrl(link), referer = oldLink).document
                oldLink = link
                if (newPage.select("script").size >= 6 && matchFound(newPage)){
                    videoNotFound = false
                    callback(
                        ExtractorLink(
                            this.name,
                            button.text(),
                            getUrl(newPage),
                            fixUrl(link),
                            quality = 0,
                            true
                        )
                    )
                }
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        extractVideoLinks(data, callback)

        return true
    }
}
