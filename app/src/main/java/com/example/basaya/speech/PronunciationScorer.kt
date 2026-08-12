package com.example.basaya.speech

import kotlin.math.max
import kotlin.math.min


object PronunciationScorer {

    enum class ScoreBand {
        GREAT,
        OKAY,
        TRY_AGAIN
    }

    data class PronunciationResult(
        val scoreBand: ScoreBand,
        val scorePercent: Int,
        val matchedText: String?,
        val editDistance: Int
    )

    fun score(targetWord: String, candidates: List<String>, confidenceScores: FloatArray?): PronunciationResult {
        val normalizedTarget = normalize(targetWord)

        var bestCandidate: String? = null
        var bestDistance = Int.MAX_VALUE
        var bestIndex = -1

        candidates.forEachIndexed { index, candidate ->
            val normalizedCandidate = normalize(candidate)
            val distance = levenshteinDistance(normalizedTarget, normalizedCandidate)
            if (distance < bestDistance) {
                bestDistance = distance
                bestCandidate = candidate
                bestIndex = index
            }
        }

        val confidence = if (confidenceScores != null && bestIndex in confidenceScores.indices) {
            confidenceScores[bestIndex]
        } else {
            0.5f
        }

        val tolerance = toleranceForLength(normalizedTarget.length)
        val textScore = textSimilarityScore(bestDistance, normalizedTarget.length)

        val combinedPercent = ((textScore * 0.7f) + (confidence * 100f * 0.3f)).toInt().coerceIn(0, 100)

        val band = when {
            bestDistance <= tolerance.great -> ScoreBand.GREAT
            bestDistance <= tolerance.okay -> ScoreBand.OKAY
            else -> ScoreBand.TRY_AGAIN
        }

        return PronunciationResult(
            scoreBand = band,
            scorePercent = combinedPercent,
            matchedText = bestCandidate,
            editDistance = bestDistance
        )
    }

    private data class Tolerance(val great: Int, val okay: Int)

    /**
     * Shorter words get stricter tolerance since a small typo/mishear
     * represents a proportionally bigger error.
     */
    private fun toleranceForLength(length: Int): Tolerance {
        return when {
            length <= 4 -> Tolerance(great = 0, okay = 1)
            length <= 7 -> Tolerance(great = 1, okay = 2)
            else -> Tolerance(great = 1, okay = 2)
        }
    }

    private fun textSimilarityScore(distance: Int, targetLength: Int): Float {
        if (targetLength == 0) return 0f
        val similarity = 1f - (distance.toFloat() / max(targetLength, 1))
        return (similarity * 100f).coerceIn(0f, 100f)
    }

    private fun normalize(text: String): String {
        return text.trim().lowercase().replace(Regex("[^a-zñ ]"), "")
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + min(dp[i - 1][j - 1], min(dp[i - 1][j], dp[i][j - 1]))
                }
            }
        }

        return dp[a.length][b.length]
    }
}