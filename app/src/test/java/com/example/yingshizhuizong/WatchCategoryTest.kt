package com.example.yingshizhuizong

import com.example.yingshizhuizong.data.entity.WatchCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCategoryTest {

    @Test
    fun `no genre ids falls back to other`() {
        assertEquals(WatchCategory.OTHER, WatchCategory.fromTmdbIds(emptyList()))
        assertEquals(WatchCategory.OTHER, WatchCategory.fromTmdbIds(listOf(99999)))
    }

    @Test
    fun `single genre maps to its category`() {
        assertEquals(WatchCategory.SCI_FI, WatchCategory.fromTmdbIds(listOf(878)))
        assertEquals(WatchCategory.ANIMATION, WatchCategory.fromTmdbIds(listOf(16)))
        // 剧集版科幻类型
        assertEquals(WatchCategory.SCI_FI, WatchCategory.fromTmdbIds(listOf(10765)))
    }

    @Test
    fun `specific genre wins over generic drama`() {
        // 「动作 + 剧情」应归到动作，而不是泛泛的剧情
        assertEquals(WatchCategory.ACTION, WatchCategory.fromTmdbIds(listOf(18, 28)))
        // 「动画 + 家庭」应归到动画
        assertEquals(WatchCategory.ANIMATION, WatchCategory.fromTmdbIds(listOf(10751, 16)))
    }

    @Test
    fun `unknown stored name falls back to other`() {
        assertEquals(WatchCategory.OTHER, WatchCategory.fromName("NOT_A_CATEGORY"))
        assertEquals(WatchCategory.OTHER, WatchCategory.fromName(null))
        assertEquals(WatchCategory.CRIME, WatchCategory.fromName("CRIME"))
    }

    @Test
    fun `all labels are unique and other has no genre mapping`() {
        val labels = WatchCategory.entries.map { it.label }
        assertEquals(labels.size, labels.distinct().size)
        assertTrue(WatchCategory.OTHER.tmdbGenreIds.isEmpty())
    }
}
