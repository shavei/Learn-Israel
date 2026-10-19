package com.shavei.mapa.learning

import com.shavei.mapa.learning.HebrewNames.Match
import kotlin.test.Test
import kotlin.test.assertEquals

class HebrewNamesTest {
    @Test
    fun spellingVariantsNormalizeTogether() {
        assertEquals(HebrewNames.normalize("קריית שמונה"), HebrewNames.normalize("קרית-שמונה"))
        assertEquals(HebrewNames.normalize("פתח תקווה"), HebrewNames.normalize("פתח תקוה"))
        assertEquals(HebrewNames.normalize("ירושלים"), HebrewNames.normalize("יְרוּשָׁלַיִם"))
        assertEquals(HebrewNames.normalize("תל אביב"), HebrewNames.normalize("תל אביב"))
    }

    @Test
    fun aliasesAndAbbreviationsMatch() {
        val names = listOf("תל אביב-יפו", "תל אביב", "תא")
        assertEquals(Match.EXACT, HebrewNames.match("ת\"א", names))
        assertEquals(Match.EXACT, HebrewNames.match("תל-אביב", names))
        assertEquals(Match.EXACT, HebrewNames.match("  תל אביב יפו ", names))
    }

    @Test
    fun oneSlipOnALongNameIsClose() {
        assertEquals(Match.CLOSE, HebrewNames.match("נתנה", listOf("נתניה")))
        assertEquals(Match.EXACT, HebrewNames.match("אשקלונ", listOf("אשקלון")), "final letters are folded")
        assertEquals(Match.CLOSE, HebrewNames.match("אשקלו", listOf("אשקלון")))
        assertEquals(Match.WRONG, HebrewNames.match("חיפה", listOf("נתניה")))
        assertEquals(Match.WRONG, HebrewNames.match("עכה", listOf("עכו")), "short names need to be exact")
        assertEquals(Match.WRONG, HebrewNames.match("", listOf("עכו")))
    }
}
