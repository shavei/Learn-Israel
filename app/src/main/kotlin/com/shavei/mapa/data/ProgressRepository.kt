package com.shavei.mapa.data

import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.CardKey
import com.shavei.mapa.learning.Facet
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.Response
import com.shavei.mapa.learning.StudyCard
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.FsrsCard
import java.time.Instant
import java.time.ZoneId

class ProgressRepository(private val dao: ProgressDao, private val appVersion: String) {

    suspend fun cards(): Map<CardKey, StudyCard> = dao.cards().associate {
        CardKey(it.placeId, Facet.valueOf(it.facet)) to
            StudyCard(FsrsCard(CardState.of(it.state), it.step, it.stability, it.difficulty, it.due, it.lastReview), it.struggles)
    }

    suspend fun newIntroducedToday(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val startOfDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.countIntroducedSince(startOfDay)
    }

    suspend fun save(answer: Answer, sessionId: String) {
        val key = answer.question.key
        val c = answer.after
        dao.upsert(
            CardEntity(
                key.placeId, key.facet.name, c.fsrs.state.value, c.fsrs.step, c.fsrs.stability, c.fsrs.difficulty,
                c.fsrs.due, c.fsrs.lastReview, c.struggles,
            ),
        )
        val tap = (answer.response as? Response.Tap)?.at
        dao.insert(
            ReviewEntity(
                placeId = key.placeId,
                facet = key.facet.name,
                mode = mode(answer.question),
                sessionId = sessionId,
                timestamp = answer.at,
                rating = answer.grade.rating.value,
                errorMeters = answer.grade.errorMeters,
                tapX = tap?.x,
                tapY = tap?.y,
                answer = when (val r = answer.response) {
                    is Response.Choice -> r.placeId
                    is Response.Typed -> r.text
                    is Response.Tap -> null
                },
                responseMs = answer.responseMs,
                appVersion = appVersion,
            ),
        )
    }

    private fun mode(q: Question) = when (q) {
        is Question.Locate -> "LOCATE"
        is Question.NameChoice -> "NAME_CHOICE"
        is Question.NameTyped -> "NAME_TYPED"
        is Question.Relate -> "RELATE"
    }
}
