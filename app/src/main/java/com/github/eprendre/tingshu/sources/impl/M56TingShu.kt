package com.github.eprendre.tingshu.sources.impl

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Handler
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.webkit.*
import com.github.eprendre.tingshu.App
import com.github.eprendre.tingshu.R
import com.github.eprendre.tingshu.extensions.*
import com.github.eprendre.tingshu.sources.AudioUrlExtractor
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.sources.TingShuSourceHandler
import com.github.eprendre.tingshu.utils.*
import com.github.eprendre.tingshu.widget.RxBus
import com.github.eprendre.tingshu.widget.RxEvent
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.upstream.DataSource
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object M56TingShu : TingShu {
    private lateinit var extractor: M56AudioUrlExtractor

    override fun getMainSectionTabs(): List<SectionTab> {
        return listOf(
            SectionTab("玄幻武侠", "http://m.ting56.com/paihangbang/1-1.html"),
            SectionTab("都市言情", "http://m.ting56.com/paihangbang/2-2.html"),
            SectionTab("恐怖悬疑", "http://m.ting56.com/paihangbang/3-3.html"),
            SectionTab("网游竞技", "http://m.ting56.com/paihangbang/4-4.html"),
            SectionTab("军事历史", "http://m.ting56.com/paihangbang/6-6.html"),
            SectionTab("刑侦推理", "http://m.ting56.com/paihangbang/41-41.html")
        )
    }

    override fun getOtherSectionTabs(): List<SectionTab> {
        return listOf(
            SectionTab("职场商战", "http://m.ting56.com/paihangbang/7-7.html"),
            SectionTab("百家讲坛", "http://m.ting56.com/paihangbang/10-10.html"),
            SectionTab("广播剧", "http://m.ting56.com/paihangbang/40-40.html"),
            SectionTab("幽默笑话", "http://m.ting56.com/paihangbang/44-44.html"),
            SectionTab("相声", "http://m.ting56.com/book/43.html"),
            SectionTab("儿童读物", "http://m.ting56.com/paihangbang/11-11.html")
        )
    }

    override fun getAudioUrlExtractor(exoPlayer: ExoPlayer, dataSourceFactory: DataSource.Factory): AudioUrlExtractor {
        if (!M56TingShu::extractor.isInitialized) {
            extractor = M56AudioUrlExtractor(exoPlayer, dataSourceFactory)
        }

        return extractor
    }

    override fun playFromBookUrl(bookUrl: String): Completable {
        return Completable.fromCallable {
            val doc = Jsoup.connect(bookUrl).get()
//            val book = doc.getElementsByClass("list-ov-tw").first()
//            val cover = book.getElementsByTag("img").first().attr("src")

            TingShuSourceHandler.downloadCoverForNotification()

            //获取书本信息
//            val bookInfos = book.getElementsByTag("span").map { it.text() }
//            Prefs.currentBookName = bookInfos[0]
//            Prefs.author = bookInfos[2]
//            Prefs.artist = bookInfos[3]

            //获取章节列表
            val episodes = doc.getElementById("playlist")
                .getElementsByTag("a")
                .map {
                    Episode(it.text(), it.attr("abs:href"))
                }
            App.playList = episodes
            return@fromCallable null
        }
    }

    override fun getSectionDetail(url: String): Single<Section> {
        return Single.fromCallable {
            var currentPage: Int
            var totalPage: Int

            val list = ArrayList<Book>()
            val doc = Jsoup.connect(url).get()
            val container = doc.selectFirst(".xsdz")
            doc.getElementById("page_num1").text().split("/").let {
                currentPage = it[0].toInt()
                totalPage = it[1].toInt()
            }
            val nextUrl = doc.getElementById("page_next1").attr("abs:href")
            val elementList = container.getElementsByClass("list-ov-tw")
            elementList.forEach { item ->
                var coverUrl = item.selectFirst(".list-ov-t a img").attr("original")
                if (coverUrl.startsWith("/")) {//有些网址已拼接好，有些没有拼接
                    //这里用主站去拼接，因为用http://m.ting56.com/拼接时经常封面报错
                    coverUrl = "http://www.ting56.com$coverUrl"
                }
                val ov = item.selectFirst(".list-ov-w")
                val bookUrl = ov.selectFirst(".bt a").attr("abs:href")
                val title = ov.selectFirst(".bt a").text()
                val (author, artist) = ov.select(".zz").let { element ->
                    Pair(element[0].text(), element[1].text())
                }
                val intro = ov.selectFirst(".nr").text()
                list.add(Book(coverUrl, bookUrl, title, author, artist).apply { this.intro = intro })
            }

            return@fromCallable Section(list, currentPage, totalPage, url, nextUrl)
        }
    }

    override fun search(keywords: String, page: Int): Single<Pair<List<Book>, Int>> {
        return Single.fromCallable {
            //            var currentPage: Int
            var totalPage: Int
            val url = "http://m.ting56.com/search.asp?searchword=${URLEncoder.encode(keywords, "gb2312")}&page=$page"
            val list = ArrayList<Book>()
            val doc = Jsoup.connect(url).get()
            val container = doc.selectFirst(".xsdz")
            container.getElementById("page_num1").text().split("/").let {
                //                currentPage = it[0].toInt()
                totalPage = it[1].toInt()
            }
            val elementList = container.getElementsByClass("list-ov-tw")
            elementList.forEach { item ->
                val coverUrl = item.selectFirst(".list-ov-t a img").attr("original")
                val ov = item.selectFirst(".list-ov-w")
                val bookUrl = ov.selectFirst(".bt a").attr("abs:href")
                val title = ov.selectFirst(".bt a").text()
                val (author, artist) = ov.select(".zz").let { element ->
                    Pair(element[0].text(), element[1].text())
                }
                val intro = ov.selectFirst(".nr").text()
                list.add(Book(coverUrl, bookUrl, title, author, artist).apply { this.intro = intro })
            }
            return@fromCallable Pair(list, totalPage)
        }
    }

    private class M56AudioUrlExtractor(
        private val exoPlayer: ExoPlayer,
        private val dataSourceFactory: DataSource.Factory
    ) : AudioUrlExtractor {
        private val compositeDisposable by lazy { CompositeDisposable() }
        private val webView by lazy { WebView(App.appContext) }
        private var isPageFinished = false
        private var isAudioGet = false
        private var isError = false
//        private var currentUrl = ""

        init {
            //jsoup 只能解析静态页面，使用 webview 可以省不少力气
            webView.settings.javaScriptEnabled = true
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    //有时候页面某些元素卡加载进度 onPageFinished 永远也不会调用便超时了
                    //但这个时候实际播放地址早就出来了，所以获取播放地址的方法放到这边
                    if (newProgress > 60 && !isPageFinished) {
                        isPageFinished = true
                        tryGetAudioSrc()
                    }
                }
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    //56听书的页面没 redirect 但是 onPageFinished 会被多次调用
//                    if (currentUrl == url && !isPageFinished) {
//                        isPageFinished = true
//                        tryGetAudioSrc()
//                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    when (errorCode) {
                        ERROR_TIMEOUT, ERROR_HOST_LOOKUP -> {
                            compositeDisposable.clear()
                            postError()
                        }
                    }
                }

                @SuppressLint("NewApi")
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    onReceivedError(view, error.errorCode, error.description.toString(), request.url.toString())
                }
            }
        }

        private fun postError() {
            if (!isAudioGet) {
                isError = true
                RxBus.post(RxEvent.ParsingPlayUrlErrorEvent())
                webView.loadUrl("about:blank")
            }
        }

        override fun extract(url: String) {
            compositeDisposable.clear()
            isAudioGet = false
            isPageFinished = false
            isError = false
            webView.loadUrl(url)
            Completable.timer(12, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .subscribe {
                    postError()
                }
                .addTo(compositeDisposable)
        }

        /**
         * 页面加载结束的时候 jp_audio_0 里面的音频地址不会马上被塞进去，需要等页面的ajax回调结束才有。
         * 这里进行反复读取直到拿到音频的播放地址
         */
        @Synchronized
        private fun tryGetAudioSrc() {
            if (isAudioGet || isError) {
                return
            }
            //提取webview的html
            webView.evaluateJavascript(
                "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();"
            ) { html ->
                val unescapedHtml = StringEscapeUtils.unescapeJava(html)//提取出来的html需要unescape

                //使用Jsoup解析html文本
                val doc = Jsoup.parse(unescapedHtml)

                val audioElement = doc.getElementById("jp_audio_0")
                val audioUrl = audioElement?.attr("src")
                if (audioUrl.isNullOrBlank()) {
                    Handler().postDelayed({
                        tryGetAudioSrc()
                    }, 500)
                    return@evaluateJavascript
                }
                if (isAudioGet) {
                    return@evaluateJavascript
                }
                compositeDisposable.clear()
                isAudioGet = true

                var art = App.coverBitmap
                if (art == null) {
                    art = BitmapFactory.decodeResource(App.appContext.resources, R.drawable.ic_notification)
                }

                val bookname = Prefs.currentEpisodeName + " - " + Prefs.currentBookName

                val metadata = MediaMetadataCompat.Builder()
                    .apply {
                        title = bookname
                        artist = Prefs.artist
                        mediaUri = audioUrl

                        displayTitle = bookname
                        displaySubtitle = Prefs.artist
                        downloadStatus = MediaDescriptionCompat.STATUS_NOT_DOWNLOADED
                        albumArt = art
                    }
                    .build()

                val source = metadata.toMediaSource(dataSourceFactory)
                exoPlayer.prepare(source)
                if (Prefs.currentEpisodePosition > 0) {
                    exoPlayer.seekTo(Prefs.currentEpisodePosition)
                }
                webView.loadUrl("about:blank")
            }
        }
    }

}
