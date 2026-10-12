package com.example.basaya.data.model

data class LecturePage(
    val pageNumber: Int,
    val sentences: List<String>,
    val audioResNames: List<String> = emptyList(), // e.g. "lesson1_page1_sentence1" -> res/raw file
    // Cloudflare R2 URL per sentence ("" when none). Nullable because lectures cached
    // in Room before this field existed deserialize it as null.
    val audioUrls: List<String>? = null
)
