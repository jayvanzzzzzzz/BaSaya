package ph.basaya.app.data.model

data class LecturePage(
    val pageNumber: Int,
    val sentences: List<String>,
    val audioResNames: List<String> = emptyList(), // e.g. "lesson1_page1_sentence1" -> res/raw file
    // Cloudflare R2 URL per sentence ("" when none). Nullable because lectures cached
    // in Room before this field existed deserialize it as null.
    val audioUrls: List<String>? = null,
    // When each word of each sentence is spoken, in seconds (empty when the audio has no
    // timings: the lecture then estimates them). Nullable for the same Room reason.
    val wordStarts: List<List<Double>>? = null
)
