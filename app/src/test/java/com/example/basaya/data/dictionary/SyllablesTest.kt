package com.example.basaya.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

/** The same answers as the website's js/syllables.js; a change there must change these too. */
class SyllablesTest {

    private fun check(word: String, expected: String) = assertEquals(word, expected, Syllables.split(word))

    @Test
    fun splitsLikeTheWebsite() {
        check("bahay", "ba·hay")
        check("kain", "ka·in")
        check("araw", "a·raw")
        check("aklat", "ak·lat")
        check("bundok", "bun·dok")
        check("trabaho", "tra·ba·ho")
        check("arkipelago", "ar·ki·pe·la·go")
        check("pag-ibig", "pag-i·big")
        check("báhay", "bá·hay")
        check("Arkipelago", "Ar·ki·pe·la·go")
        check("sentro", "sen·tro")
        check("kontrata", "kon·tra·ta")
        check("ekstra", "eks·tra")
        check("angaw", "a·ngaw")
        check("ngiti", "ngi·ti")
        check("bitwin", "bit·win")
        check("plato", "pla·to")
        check("eskwela", "es·kwe·la")
        check("plantsa", "plan·tsa")
        check("kanluran", "kan·lu·ran")
        check("maganda", "ma·gan·da")
        check("kumakain", "ku·ma·ka·in")
        check("mag-aaral", "mag-a·a·ral")
        check("tatlo", "tat·lo")
        check("magandang umaga", "ma·gan·dang u·ma·ga")
        check("a", "a")
        check("-an", "-an")
        check("inspeksyon", "ins·pek·syon")
    }
}
