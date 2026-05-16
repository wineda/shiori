package com.wineda.shiori.domain.model

enum class MemoTag(val key: String, val label: String, val symbol: String) {
    GOOD("good", "良", "◯"),
    HARD("hard", "辛", "◐"),
    INSIGHT("insight", "気付", "◑"),
    TOMORROW("tomorrow", "明日", "◉");

    companion object {
        fun fromKey(key: String?): MemoTag? = entries.firstOrNull { it.key == key }
    }
}
