package com.example.yingshizhuizong.util

import android.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale

/**
 * 中文片名的拼音索引，用于「英文键盘也能搜中文片名」。
 *
 * 用系统自带的 ICU Han-Latin 转换，不需要额外依赖：
 *   「流浪地球2」→ "liulangdiqiu2 lldq2"
 * 前半段是全拼、后半段是首字母，搜索时按子串匹配即可。
 * 设备不支持时返回 null，不影响原有搜索。
 */
object Pinyin {

    private val engine: Transliterator? = try {
        Transliterator.getInstance("Han-Latin")
    } catch (e: Throwable) {
        null
    }

    private val MARKS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^a-z0-9]")
    private val SPACES = Regex("\\s+")

    fun of(text: String): String? {
        val translator = engine ?: return null
        if (text.isBlank()) return null
        return try {
            val lower = translator.transliterate(text).lowercase(Locale.ROOT)
            // 去掉声调符号：liú làng dì qiú → liu lang di qiu
            val plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(MARKS, "")
            val tokens = plain.split(SPACES).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            val full = NON_WORD.replace(tokens.joinToString(""), "")
            val initials = NON_WORD.replace(tokens.mapNotNull { it.firstOrNull() }.joinToString(""), "")
            when {
                full.isEmpty() -> null
                initials.isEmpty() -> full
                else -> "$full $initials"
            }
        } catch (e: Throwable) {
            null
        }
    }
}
