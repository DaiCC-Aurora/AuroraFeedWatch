package com.aurora.podcast.data.model

/**
 * 字幕解析器：支持 VTT/SRT（带时间戳）、LRC（[mm:ss.xx]），
 * 以及纯文本 transcript（按句切分后均分到总时长）。
 */
object SubtitleParser {

    private val ARROW_REGEX = Regex(
        "(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})"
    )
    private val LRC_REGEX = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)")

    fun parse(text: String?, durationSeconds: Int): List<SubtitleCue> {
        if (text.isNullOrBlank()) return emptyList()
        val trimmed = text.trim().replace("\r\n", "\n").replace('\r', '\n')

        return when {
            trimmed.lines().any { ARROW_REGEX.containsMatchIn(it) } -> parseTimestamped(trimmed)
            trimmed.lines().any { LRC_REGEX.containsMatchIn(it) } -> parseLrc(trimmed)
            else -> fromPlainTranscript(trimmed, durationSeconds)
        }
    }

    private fun parseTimestamped(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var start = -1L
        var end = -1L
        val buf = StringBuilder()

        fun flush() {
            if (start >= 0 && buf.isNotBlank()) {
                cues.add(SubtitleCue(start, end, buf.toString().trim()))
            }
            start = -1
            end = -1
            buf.clear()
        }

        for (line in text.lines()) {
            val m = ARROW_REGEX.find(line)
            if (m != null) {
                flush()
                start = parseTimecode(m.groupValues[1])
                end = parseTimecode(m.groupValues[2])
            } else if (line.isBlank()) {
                flush()
            } else if (start >= 0) {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(line.trim())
            }
            // start < 0 的行（WEBVTT 头、序号）忽略
        }
        flush()
        return cues
    }

    private fun parseLrc(text: String): List<SubtitleCue> {
        val raw = text.lines().mapNotNull { line ->
            val m = LRC_REGEX.find(line) ?: return@mapNotNull null
            val minutes = m.groupValues[1].toLongOrNull() ?: 0L
            val seconds = m.groupValues[2].toLongOrNull() ?: 0L
            val frac = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            val content = m.groupValues[4].trim()
            if (content.isEmpty()) null else SubtitleCue(minutes * 60_000 + seconds * 1000 + frac, 0L, content)
        }.sortedBy { it.startMillis }

        return raw.mapIndexed { i, cue ->
            val end = if (i + 1 < raw.size) raw[i + 1].startMillis else cue.startMillis + 5000
            cue.copy(endMillis = end)
        }
    }

    private fun fromPlainTranscript(text: String, durationSeconds: Int): List<SubtitleCue> {
        val sentences = text
            .split(Regex("(?<=[.!?。！？])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return emptyList()

        val totalMillis = (durationSeconds.coerceAtLeast(1) * 1000L)
        val perCue = totalMillis / sentences.size
        return sentences.mapIndexed { i, s ->
            SubtitleCue(i * perCue, (i + 1) * perCue, s)
        }
    }

    /** "hh:mm:ss.mmm" / "mm:ss.mmm" / "ss.mmm" -> 毫秒 */
    private fun parseTimecode(tc: String): Long {
        val parts = tc.trim().replace(',', '.').split(':').reversed()
        val sec = parts.getOrElse(0) { "0" }.toDoubleOrNull() ?: 0.0
        val min = parts.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0
        val hour = parts.getOrElse(2) { "0" }.toDoubleOrNull() ?: 0.0
        return ((hour * 3600 + min * 60 + sec) * 1000).toLong()
    }
}