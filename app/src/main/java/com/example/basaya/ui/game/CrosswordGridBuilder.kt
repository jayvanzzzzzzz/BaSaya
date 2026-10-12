package com.example.basaya.ui.game

import java.util.Locale

internal enum class CrosswordDirection {
    ACROSS,
    DOWN
}

internal data class CrosswordCellPosition(
    val row: Int,
    val column: Int
)

internal data class CrosswordWordPlacement(
    val wordIndex: Int,
    val start: CrosswordCellPosition,
    val direction: CrosswordDirection
) {
    fun positionAt(letterIndex: Int): CrosswordCellPosition =
        when (direction) {
            CrosswordDirection.ACROSS -> start.copy(column = start.column + letterIndex)
            CrosswordDirection.DOWN -> start.copy(row = start.row + letterIndex)
        }
}

internal data class CrosswordGrid(
    val placements: List<CrosswordWordPlacement>,
    val rows: Int,
    val columns: Int
)

internal object CrosswordGridBuilder {
    private data class OccupiedCell(
        val letter: Char,
        val directions: Set<CrosswordDirection>
    )

    private data class Candidate(
        val placement: CrosswordWordPlacement,
        val intersections: Int,
        val area: Long
    )

    fun build(words: List<String>): CrosswordGrid {
        require(words.none { it.isEmpty() }) { "Crossword words must not be empty." }
        if (words.isEmpty()) return CrosswordGrid(emptyList(), 0, 0)

        val placements = mutableListOf(
            CrosswordWordPlacement(
                wordIndex = 0,
                start = CrosswordCellPosition(0, 0),
                direction = CrosswordDirection.ACROSS
            )
        )
        val occupied = mutableMapOf<CrosswordCellPosition, OccupiedCell>()
        addPlacement(words[0], placements.first(), occupied)

        for (wordIndex in 1 until words.size) {
            val word = words[wordIndex]
            val candidates = mutableListOf<Candidate>()
            for (direction in CrosswordDirection.values()) {
                candidates += findCandidates(wordIndex, word, direction, placements, words, occupied)
            }

            val placement = candidates
                .minWithOrNull(
                    compareByDescending<Candidate> { it.intersections }
                        .thenBy { it.area }
                        .thenByDescending {
                            if (it.placement.direction == preferredDirection(wordIndex)) 1 else 0
                        }
                )
                ?.placement
                ?: placeSeparately(wordIndex, placements, words, preferredDirection(wordIndex))

            placements += placement
            addPlacement(word, placement, occupied)
        }

        val minRow = placements.minOf { it.start.row }
        val minColumn = placements.minOf { it.start.column }
        val normalized = placements.map { placement ->
            placement.copy(
                start = CrosswordCellPosition(
                    row = placement.start.row - minRow,
                    column = placement.start.column - minColumn
                )
            )
        }

        val maxRow = normalized.maxOf { placement ->
            placement.positionAt(words[placement.wordIndex].lastIndex).row
        }
        val maxColumn = normalized.maxOf { placement ->
            placement.positionAt(words[placement.wordIndex].lastIndex).column
        }
        return CrosswordGrid(normalized, maxRow + 1, maxColumn + 1)
    }

    private fun findCandidates(
        wordIndex: Int,
        word: String,
        direction: CrosswordDirection,
        placements: List<CrosswordWordPlacement>,
        words: List<String>,
        occupied: Map<CrosswordCellPosition, OccupiedCell>
    ): List<Candidate> {
        val candidates = mutableMapOf<CrosswordCellPosition, Candidate>()
        val normalizedWord = word.uppercase(Locale.ROOT)

        for (placed in placements) {
            if (placed.direction == direction) continue
            val placedWord = words[placed.wordIndex].uppercase(Locale.ROOT)

            for (newIndex in normalizedWord.indices) {
                for (existingIndex in placedWord.indices) {
                    if (normalizedWord[newIndex] != placedWord[existingIndex]) continue

                    val crossing = placed.positionAt(existingIndex)
                    val start = when (direction) {
                        CrosswordDirection.ACROSS ->
                            CrosswordCellPosition(crossing.row, crossing.column - newIndex)
                        CrosswordDirection.DOWN ->
                            CrosswordCellPosition(crossing.row - newIndex, crossing.column)
                    }
                    val candidate = CrosswordWordPlacement(wordIndex, start, direction)
                    val intersections = validIntersections(word, candidate, occupied) ?: continue
                    val updatedBounds = boundsAfter(candidate, word.length, placements, words)
                    val area = (updatedBounds.maxRow - updatedBounds.minRow + 1).toLong() *
                        (updatedBounds.maxColumn - updatedBounds.minColumn + 1)
                    candidates[start] = Candidate(candidate, intersections, area)
                }
            }
        }
        return candidates.values.toList()
    }

    private data class Bounds(
        val minRow: Int,
        val maxRow: Int,
        val minColumn: Int,
        val maxColumn: Int
    )

    private fun boundsAfter(
        candidate: CrosswordWordPlacement,
        wordLength: Int,
        placements: List<CrosswordWordPlacement>,
        words: List<String>
    ): Bounds {
        val allStarts = placements.map { it.start } + candidate.start
        val allEnds = placements.map { placement ->
            placement.positionAt(words[placement.wordIndex].lastIndex)
        } + candidate.positionAt(wordLength - 1)
        return Bounds(
            minRow = (allStarts + allEnds).minOf { it.row },
            maxRow = (allStarts + allEnds).maxOf { it.row },
            minColumn = (allStarts + allEnds).minOf { it.column },
            maxColumn = (allStarts + allEnds).maxOf { it.column }
        )
    }

    private fun validIntersections(
        word: String,
        candidate: CrosswordWordPlacement,
        occupied: Map<CrosswordCellPosition, OccupiedCell>
    ): Int? {
        var intersections = 0
        val normalizedWord = word.uppercase(Locale.ROOT)

        for (letterIndex in normalizedWord.indices) {
            val position = candidate.positionAt(letterIndex)
            val existing = occupied[position]
            if (existing != null) {
                if (existing.letter != normalizedWord[letterIndex] ||
                    existing.directions.contains(candidate.direction)
                ) {
                    return null
                }
                intersections++
            } else {
                val perpendicularNeighbors = when (candidate.direction) {
                    CrosswordDirection.ACROSS -> listOf(
                        position.copy(row = position.row - 1),
                        position.copy(row = position.row + 1)
                    )
                    CrosswordDirection.DOWN -> listOf(
                        position.copy(column = position.column - 1),
                        position.copy(column = position.column + 1)
                    )
                }
                if (perpendicularNeighbors.any { occupied[it] != null }) return null
            }
        }

        if (intersections == 0) return null

        val startBefore = when (candidate.direction) {
            CrosswordDirection.ACROSS -> candidate.start.copy(column = candidate.start.column - 1)
            CrosswordDirection.DOWN -> candidate.start.copy(row = candidate.start.row - 1)
        }
        val lastPosition = candidate.positionAt(normalizedWord.lastIndex)
        val endAfter = when (candidate.direction) {
            CrosswordDirection.ACROSS -> lastPosition.copy(column = lastPosition.column + 1)
            CrosswordDirection.DOWN -> lastPosition.copy(row = lastPosition.row + 1)
        }
        if (occupied[startBefore] != null || occupied[endAfter] != null) return null

        return intersections
    }

    private fun addPlacement(
        word: String,
        placement: CrosswordWordPlacement,
        occupied: MutableMap<CrosswordCellPosition, OccupiedCell>
    ) {
        val normalizedWord = word.uppercase(Locale.ROOT)
        normalizedWord.forEachIndexed { index, letter ->
            val position = placement.positionAt(index)
            val existing = occupied[position]
            occupied[position] = if (existing == null) {
                OccupiedCell(letter, setOf(placement.direction))
            } else {
                existing.copy(directions = existing.directions + placement.direction)
            }
        }
    }

    private fun placeSeparately(
        wordIndex: Int,
        placements: List<CrosswordWordPlacement>,
        words: List<String>,
        direction: CrosswordDirection
    ): CrosswordWordPlacement {
        val maxRow = placements.maxOf { it.positionAt(words[it.wordIndex].lastIndex).row }
        val minColumn = placements.minOf { it.start.column }
        val maxColumn = placements.maxOf { it.positionAt(words[it.wordIndex].lastIndex).column }
        val minRow = placements.minOf { it.start.row }
        return CrosswordWordPlacement(
            wordIndex = wordIndex,
            start = when (direction) {
                CrosswordDirection.ACROSS -> CrosswordCellPosition(row = maxRow + 2, column = minColumn)
                CrosswordDirection.DOWN -> CrosswordCellPosition(row = minRow, column = maxColumn + 2)
            },
            direction = direction
        )
    }

    private fun preferredDirection(wordIndex: Int): CrosswordDirection =
        if (wordIndex % 2 == 0) CrosswordDirection.ACROSS else CrosswordDirection.DOWN
}
