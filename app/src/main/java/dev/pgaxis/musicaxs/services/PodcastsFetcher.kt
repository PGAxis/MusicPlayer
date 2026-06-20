package dev.pgaxis.musicaxs.services

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

    fun fetchFeed(url: String): PodcastFeed {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Empty response")

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

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when {
                        currentTag == "image" && !insideItem -> insideImage = true
                        currentTag == "item" -> { insideItem = true; episodeCount++ }
                        currentTag == "itunes:image" && !insideItem -> {
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

        return PodcastFeed(
            url = url,
            title = title.ifEmpty { url },
            description = description,
            artUrl = artUrl,
            episodeCount = episodeCount
        )
    }

    fun fetchEpisodes(url: String): List<PodcastEpisode> {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Empty response")

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

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when {
                        currentTag == "item" -> insideItem = true
                        currentTag == "enclosure" && insideItem -> {
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (type.startsWith("audio")) {
                                audioUrl = parser.getAttributeValue(null, "url") ?: ""
                            }
                        }
                        currentTag == "itunes:duration" && insideItem -> { /* handled in TEXT */ }
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
                            // Format can be HH:MM:SS, MM:SS, or just seconds
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
                        // Reset for next item
                        title = ""; description = ""; audioUrl = ""
                        publishDate = ""; durationMs = 0L
                        insideItem = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
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