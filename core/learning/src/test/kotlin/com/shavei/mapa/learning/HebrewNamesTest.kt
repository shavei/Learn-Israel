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

    @Test
    fun aRoadNumberIsNeverASlip() {
        val route1 = listOf("כביש 1", "כביש מספר 1", "דרך 1")
        assertEquals(Match.EXACT, HebrewNames.match("כביש 1", route1))
        assertEquals(Match.WRONG, HebrewNames.match("כביש 4", route1))
        assertEquals(Match.WRONG, HebrewNames.match("כביש 40", listOf("כביש 90")))
        assertEquals(Match.WRONG, HebrewNames.match("כביש מספר 6", listOf("כביש מספר 2")))
        // A slip in the letters around the number is still forgiven.
        assertEquals(Match.CLOSE, HebrewNames.match("כביש מספ 1", route1))
    }
}
