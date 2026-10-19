package com.shavei.mapa.data

import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.FsrsCard
import java.time.Instant
import java.time.ZoneId

class ProgressRepository(private val dao: ProgressDao, private val appVersion: String) {

    suspend fun cards(): Map<String, FsrsCard> = dao.cards().associate { it.placeId to it.toCard() }

    suspend fun newIntroducedToday(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val startOfDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.countIntroducedSince(startOfDay)
    }

    suspend fun save(answer: Answer, sessionId: String) {
        dao.upsert(answer.after.toEntity(answer.place.id))
        dao.insert(
            ReviewEntity(
                placeId = answer.place.id,
                sessionId = sessionId,
                timestamp = answer.at,
                rating = answer.grade.rating.value,
                errorMeters = answer.grade.errorMeters,
                tapX = answer.tap.x,
                tapY = answer.tap.y,
                responseMs = answer.responseMs,
                appVersion = appVersion,
            ),
        )
    }

    private fun CardEntity.toCard() = FsrsCard(CardState.of(state), step, stability, difficulty, due, lastReview)

    private fun FsrsCard.toEntity(placeId: String) =
        CardEntity(placeId, state.value, step, stability, difficulty, due, lastReview)
}
