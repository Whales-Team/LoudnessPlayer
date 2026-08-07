package com.wzl.loudnessplayer.data

enum class PlaybackMode(val displayName: String) {
    SEQUENTIAL("顺序播放"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放"),
    ;

    fun next(): PlaybackMode = entries[(ordinal + 1) % entries.size]
}

enum class LibraryViewMode(val displayName: String) {
    ALL("全部歌曲"),
    ARTIST("按歌手分类"),
    SMART_TITLE("按歌曲名智能分类"),
}

enum class AppTheme(val displayName: String) {
    LIGHT("浅色"),
    DARK("深色"),
    GREEN("绿色"),
    BLUE("蓝色"),
    BROWN("棕色"),
}

data class MusicFolder(
    val id: String,
    val name: String,
    val trackIds: Set<String> = emptySet(),
)
