package com.streambox.tvonline

data class EpgProgram(
    val timeRange: String,
    val title: String,
    val description: String,
    val progressPercent: Int = 50
)

data class TvChannel(
    val id: String,
    val number: Int,
    val name: String,
    val category: String,
    val streamUrl: String,
    val epg: List<EpgProgram> = emptyList()
)

object M3uPlaylistParser {
    fun parse(rawM3u: String): List<TvChannel> {
        val channels = mutableListOf<TvChannel>()
        val lines = rawM3u.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var currentTitle = "ТВ Канал"
        var currentCategory = "Общие"
        var index = 1

        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val groupMatch = Regex("""group-title="([^"]+)"""").find(line)
                if (groupMatch != null) {
                    currentCategory = groupMatch.groupValues[1]
                }
                val commaIndex = line.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex + 1 < line.length) {
                    currentTitle = line.substring(commaIndex + 1).trim()
                }
            } else if (line.startsWith("http://") || line.startsWith("https://")) {
                channels.add(
                    TvChannel(
                        id = "m3u_$index",
                        number = index,
                        name = currentTitle,
                        category = currentCategory,
                        streamUrl = line,
                        epg = listOf(
                            EpgProgram("LIVE", "Прямой эфир • $currentTitle", "Потоковое вещание HLS/M3U8", 50)
                        )
                    )
                )
                index++
                currentTitle = "ТВ Канал $index"
            }
        }
        return channels
    }
}
