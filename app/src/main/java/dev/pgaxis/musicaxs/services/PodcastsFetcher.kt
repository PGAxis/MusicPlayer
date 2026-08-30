package dev.pgaxis.musicaxs.services

import android.util.Log
import dev.pgaxis.musicaxs.models.PodcastEpisode
import dev.pgaxis.musicaxs.models.PodcastFeed
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.Locale

object PodcastFetcher {
    private val client = OkHttpClient()

    class FeedFetchException(message: String) : Exception(message)

    private fun fetchFeedBody(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) MusicAxs/1.0 PodcastFetcher")
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw FeedFetchException("Server returned HTTP ${resp.code} for this feed")
            }

            val body = resp.body?.string()
                ?: throw FeedFetchException("Empty response from feed URL")

            val contentType = resp.header("Content-Type").orEmpty()
            val looksLikeXml = contentType.contains("xml", ignoreCase = true) ||
                    body.trimStart().let {
                        it.startsWith("<?xml", ignoreCase = true) ||
                                it.startsWith("<rss", ignoreCase = true) ||
                                it.startsWith("<feed", ignoreCase = true)
                    }

            if (!looksLikeXml) {
                val hint = when {
                    body.contains("Cloudflare", ignoreCase = true) &&
                            body.contains("blocked", ignoreCase = true) ->
                        "Blocked by the host's security check (Cloudflare)"
                    body.contains("<html", ignoreCase = true) ->
                        "URL returned a webpage, not a podcast feed"
                    else -> "Response doesn't look like a valid RSS/XML feed"
                }
                throw FeedFetchException(hint)
            }

            return body
        }
    }

    fun fetchFeed(url: String): PodcastFeed {
        val body = fetchFeedBody(url)
        Log.d("PodcastDebug", body)

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(body.reader())

        var title = ""
        var description = ""
        var artUrl: String? = null
        var episodeCount = 0
        var insideImage = false
        var insideItem = false
        var currentTag = ""

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "image" if !insideItem -> insideImage = true
                            "item" -> { insideItem = true; episodeCount++ }
                            "itunes:image" if !insideItem -> {
                                artUrl = parser.getAttributeValue(null, "href")
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isEmpty()) {
                            eventType = parser.next()
                            continue
                        }
                        when {
                            !insideItem && !insideImage && currentTag == "title" && title.isEmpty() ->
                                title = text
                            !insideItem && !insideImage && currentTag == "description" && description.isEmpty() ->
                                description = text
                            insideImage && currentTag == "url" && artUrl == null ->
                                artUrl = text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "image" -> insideImage = false
                            "item" -> insideItem = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            throw FeedFetchException("Couldn't parse feed XML: ${e.message}")
        }

        return PodcastFeed(
            url = url,
            title = title.ifEmpty { url },
            description = description,
            artUrl = artUrl,
            episodeCount = episodeCount
        )
    }

    fun fetchEpisodes(url: String): List<PodcastEpisode> {
        val body = fetchFeedBody(url)

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(body.reader())

        val episodes = mutableListOf<PodcastEpisode>()
        var insideItem = false
        var currentTag = ""
        var title = ""
        var description = ""
        var audioUrl = ""
        var publishDate = ""
        var durationMs = 0L

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "item" -> insideItem = true
                            "enclosure" if insideItem -> {
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                if (type.startsWith("audio")) {
                                    audioUrl = parser.getAttributeValue(null, "url") ?: ""
                                }
                            }
                            "itunes:duration" if insideItem -> { /* handled in TEXT */ }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isEmpty() || !insideItem) {
                            eventType = parser.next()
                            continue
                        }
                        when (currentTag) {
                            "title" -> if (title.isEmpty()) title = text
                            "description" -> if (description.isEmpty()) description = text
                            "itunes:summary" -> if (description.isEmpty()) description = text
                            "pubDate" -> if (publishDate.isEmpty()) publishDate = formatPodcastDate(text)
                            "itunes:duration" -> {
                                durationMs = parseDuration(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            if (audioUrl.isNotEmpty()) {
                                episodes.add(
                                    PodcastEpisode(
                                        title = title.ifEmpty { "Untitled" },
                                        description = description,
                                        audioUrl = audioUrl,
                                        publishDate = publishDate,
                                        durationMs = durationMs
                                    )
                                )
                            }
                            title = ""; description = ""; audioUrl = ""
                            publishDate = ""; durationMs = 0L
                            insideItem = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            throw FeedFetchException("Couldn't parse episode list: ${e.message}")
        }

        return episodes
    }

    private fun parseDuration(raw: String): Long {
        val parts = raw.trim().split(":")
        return when (parts.size) {
            1 -> (parts[0].toLongOrNull() ?: 0L) * 1000L
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0L
                val s = parts[1].toLongOrNull() ?: 0L
                (m * 60 + s) * 1000L
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val s = parts[2].toLongOrNull() ?: 0L
                (h * 3600 + m * 60 + s) * 1000L
            }
            else -> 0L
        }
    }

    private fun formatPodcastDate(raw: String): String {
        return try {
            val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            val date = inputFormat.parse(raw) ?: return raw
            val outputFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            outputFormat.format(date)
        } catch (_: Exception) {
            raw
        }
    }
}