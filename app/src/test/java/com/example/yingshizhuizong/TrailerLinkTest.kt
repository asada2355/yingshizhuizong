package com.example.yingshizhuizong

import com.example.yingshizhuizong.data.TrailerLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预告片链接解析的单元测试（纯 Kotlin，不依赖 Android） */
class TrailerLinkTest {

    @Test
    fun youtubeLinksAreParsed() {
        assertEquals("dQw4w9WgXcQ", TrailerLink.youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", TrailerLink.youtubeId("https://youtu.be/dQw4w9WgXcQ?t=10"))
        assertEquals("dQw4w9WgXcQ", TrailerLink.youtubeId("https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1"))
        assertEquals("dQw4w9WgXcQ", TrailerLink.youtubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", TrailerLink.youtubeId("https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
        assertEquals("dQw4w9WgXcQ", TrailerLink.youtubeId("dQw4w9WgXcQ"))
    }

    @Test
    fun nonYoutubeLinksAreRejected() {
        assertNull(TrailerLink.youtubeId("https://example.com/trailer.mp4"))
        assertNull(TrailerLink.youtubeId(""))
    }

    @Test
    fun directVideoUrlsAreDetected() {
        assertTrue(TrailerLink.isDirectVideo("https://example.com/a.mp4?token=1"))
        assertTrue(TrailerLink.isDirectVideo("https://example.com/a.m3u8"))
        assertFalse(TrailerLink.isDirectVideo("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertFalse(TrailerLink.isDirectVideo("ftp://example.com/a.mp4"))
    }

    @Test
    fun playerUrlsAreBuilt() {
        assertTrue(TrailerLink.embedUrl("dQw4w9WgXcQ").startsWith("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertTrue(TrailerLink.thumbnail("dQw4w9WgXcQ").contains("/vi/dQw4w9WgXcQ/"))
    }
}
