package ph.basaya.app.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosswordGridBuilderTest {

    @Test
    fun placesAcrossAndDownWordsAtSharedMatchingCells() {
        val words = listOf("CATS", "ART", "TAR")

        val grid = CrosswordGridBuilder.build(words)
        val across = grid.placements.filter { it.direction == CrosswordDirection.ACROSS }
        val down = grid.placements.filter { it.direction == CrosswordDirection.DOWN }
        val sharedCells = across.flatMap { acrossPlacement ->
            words[acrossPlacement.wordIndex].indices.mapNotNull { acrossIndex ->
                val position = acrossPlacement.positionAt(acrossIndex)
                down.firstOrNull { downPlacement ->
                    words[downPlacement.wordIndex].indices.any { downIndex ->
                        downPlacement.positionAt(downIndex) == position &&
                            words[acrossPlacement.wordIndex][acrossIndex].equals(
                                words[downPlacement.wordIndex][downIndex],
                                ignoreCase = true
                            )
                    }
                }?.let { position }
            }
        }

        assertTrue(across.isNotEmpty())
        assertTrue(down.isNotEmpty())
        assertTrue(sharedCells.isNotEmpty())

        val allPositions = grid.placements.flatMap { placement ->
            words[placement.wordIndex].indices.map(placement::positionAt)
        }
        assertEquals(allPositions.size - sharedCells.distinct().size, allPositions.distinct().size)
    }

    @Test
    fun supportsLongVerticalWordsAndReportsFullGridBounds() {
        val words = listOf("A", "ABCDEFGHIJKLMNOPQRSTUVWX")

        val grid = CrosswordGridBuilder.build(words)
        val downPlacement = grid.placements.first { it.direction == CrosswordDirection.DOWN }

        assertEquals(words[downPlacement.wordIndex].length, grid.rows)
        assertEquals(1, grid.columns)
    }

    @Test
    fun separatesWordsWithoutSharedLettersWithoutOverlappingCells() {
        val words = listOf("CAT", "DOG")

        val grid = CrosswordGridBuilder.build(words)
        val occupiedPositions = grid.placements.flatMap { placement ->
            words[placement.wordIndex].indices.map(placement::positionAt)
        }

        assertTrue(grid.placements.any { it.direction == CrosswordDirection.DOWN })
        assertEquals(occupiedPositions.size, occupiedPositions.distinct().size)
    }
}
