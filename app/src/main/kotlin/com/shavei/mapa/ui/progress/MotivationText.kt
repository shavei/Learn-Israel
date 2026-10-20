package com.shavei.mapa.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shavei.mapa.R
import com.shavei.mapa.learning.Achievement
import com.shavei.mapa.learning.Rank

@Composable
fun rankName(rank: Rank): String = stringResource(
    when (rank) {
        Rank.TOURIST -> R.string.rank_tourist
        Rank.HIKER -> R.string.rank_hiker
        Rank.GUIDE -> R.string.rank_guide
        Rank.NAVIGATOR -> R.string.rank_navigator
        Rank.CARTOGRAPHER -> R.string.rank_cartographer
        Rank.SABRA -> R.string.rank_sabra
    },
)

@Composable
fun achievementTitle(achievement: Achievement): String = stringResource(
    when (achievement) {
        Achievement.FIRST_KNOWN -> R.string.achievement_first_known
        Achievement.FRAME -> R.string.achievement_frame
        Achievement.REGIONS -> R.string.achievement_regions
        Achievement.DISTRICTS -> R.string.achievement_districts
        Achievement.MAIN_CITIES -> R.string.achievement_main_cities
        Achievement.KINNERET -> R.string.achievement_kinneret
        Achievement.WHOLE_REGION -> R.string.achievement_whole_region
        Achievement.ALL_CITIES -> R.string.achievement_all_cities
        Achievement.NATURE -> R.string.achievement_nature
        Achievement.GATEWAYS -> R.string.achievement_gateways
        Achievement.HERITAGE -> R.string.achievement_heritage
        Achievement.NO_MISS -> R.string.achievement_no_miss
        Achievement.LONG_MEMORY -> R.string.achievement_long_memory
        Achievement.STREAK_7 -> R.string.achievement_streak_7
        Achievement.STREAK_30 -> R.string.achievement_streak_30
    },
)

/** What the achievement asks for; [total] is how many places it counts, where the text names it. */
@Composable
fun achievementText(achievement: Achievement, total: Int): String = when (achievement) {
    Achievement.FIRST_KNOWN -> stringResource(R.string.achievement_first_known_text)
    Achievement.FRAME -> stringResource(R.string.achievement_frame_text)
    Achievement.REGIONS -> stringResource(R.string.achievement_regions_text, total)
    Achievement.DISTRICTS -> stringResource(R.string.achievement_districts_text, total)
    Achievement.MAIN_CITIES -> stringResource(R.string.achievement_main_cities_text, total)
    Achievement.KINNERET -> stringResource(R.string.achievement_kinneret_text)
    Achievement.WHOLE_REGION -> stringResource(R.string.achievement_whole_region_text)
    Achievement.ALL_CITIES -> stringResource(R.string.achievement_all_cities_text, total)
    Achievement.NATURE -> stringResource(R.string.achievement_nature_text)
    Achievement.GATEWAYS -> stringResource(R.string.achievement_gateways_text)
    Achievement.HERITAGE -> stringResource(R.string.achievement_heritage_text)
    Achievement.NO_MISS -> stringResource(R.string.achievement_no_miss_text)
    Achievement.LONG_MEMORY -> stringResource(R.string.achievement_long_memory_text)
    Achievement.STREAK_7 -> stringResource(R.string.achievement_streak_7_text)
    Achievement.STREAK_30 -> stringResource(R.string.achievement_streak_30_text)
}
