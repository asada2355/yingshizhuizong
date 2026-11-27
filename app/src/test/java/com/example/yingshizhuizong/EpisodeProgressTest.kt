package com.example.yingshizhuizong

import com.example.yingshizhuizong.data.EpisodeProgress
import com.example.yingshizhuizong.data.episodeProgressFor
import com.example.yingshizhuizong.data.entity.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeProgressTest {

    private fun old(season: Int = 0, episode: Int = 0) =
        EpisodeProgress(season, episode, WatchStatus.WATCHING)

    @Test
    fun `marking an episode advances progress and sets watching`() {
        val result = episodeProgressFor(
            season = 2, episode = 5, watched = true,
            totalEpisodes = 10, isLastSeason = false, old = old(2, 3),
        )
        assertEquals(2, result.seasonNumber)
        assertEquals(5, result.episodeNumber)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun `marking the final episode of the final season finishes the series`() {
        val result = episodeProgressFor(
            season = 3, episode = 10, watched = true,
            totalEpisodes = 10, isLastSeason = true, old = old(3, 9),
        )
        assertEquals(WatchStatus.WATCHED, result.status)
        assertEquals(10, result.episodeNumber)
    }

    @Test
    fun `final episode of a non-final season does not finish the series`() {
        val result = episodeProgressFor(
            season = 1, episode = 10, watched = true,
            totalEpisodes = 10, isLastSeason = false, old = old(1, 9),
        )
        assertEquals(WatchStatus.WATCHING, result.status)
        assertEquals(10, result.episodeNumber)
    }

    @Test
    fun `unknown episode count never finishes the series`() {
        val result = episodeProgressFor(
            season = 1, episode = 10, watched = true,
            totalEpisodes = 0, isLastSeason = true, old = old(1, 9),
        )
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun `unmarking an episode steps back one and returns to watching`() {
        val result = episodeProgressFor(
            season = 2, episode = 5, watched = false,
            totalEpisodes = 10, isLastSeason = false,
            old = EpisodeProgress(2, 5, WatchStatus.WATCHED),
        )
        assertEquals(4, result.episodeNumber)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun `unmarking the first episode goes back to episode zero`() {
        val result = episodeProgressFor(
            season = 1, episode = 1, watched = false,
            totalEpisodes = 8, isLastSeason = false, old = old(1, 1),
        )
        assertEquals(0, result.episodeNumber)
        assertEquals(WatchStatus.WATCHING, result.status)
    }
}