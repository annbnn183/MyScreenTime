package com.atharok.screentime.domain

import com.atharok.screentime.domain.entities.usage.CombinedRanking
import com.atharok.screentime.domain.entities.usage.RankedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedRankingTest {

    private fun app(name: String, minutes: Long, launches: Int) = RankedApp(
        packageName = "pkg.$name",
        appName = name,
        appIcon = null,
        duration = minutes * 60_000L,
        launchCount = launches
    )

    // A：用得久但很少打开；B：用得不久但打开最勤；C：两头都少。
    private val apps = listOf(app("A", 600, 10), app("B", 300, 70), app("C", 100, 20))

    @Test
    fun timeFirst_putsTheLongestUsedAppFirst() {
        val ranked = CombinedRanking.rank(apps, CombinedRanking.WEIGHT_TIME_FIRST)
        assertEquals(listOf("A", "B", "C"), ranked.map { it.app.appName })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
    }

    @Test
    fun launchFirst_putsTheMostOpenedAppFirst() {
        val ranked = CombinedRanking.rank(apps, CombinedRanking.WEIGHT_LAUNCH_FIRST)
        assertEquals(listOf("B", "A", "C"), ranked.map { it.app.appName })
    }

    @Test
    fun combinedSharesAddUpToOne() {
        val ranked = CombinedRanking.rank(apps, CombinedRanking.WEIGHT_BALANCED)
        assertEquals(1f, ranked.sumOf { it.score.toDouble() }.toFloat(), 1e-4f)
    }

    @Test
    fun withoutAnyLaunchData_fallsBackToScreenTimeOnly() {
        val noClicks = listOf(app("A", 10, 0), app("B", 30, 0))
        val ranked = CombinedRanking.rank(noClicks, CombinedRanking.WEIGHT_LAUNCH_FIRST)
        assertEquals(listOf("B", "A"), ranked.map { it.app.appName })
        assertEquals(0.75f, ranked.first().score, 1e-4f)
    }

    @Test
    fun appsWithNoActivityAreDropped_andEmptyInputIsEmpty() {
        assertTrue(CombinedRanking.rank(emptyList(), 0.5f).isEmpty())
        assertTrue(CombinedRanking.rank(listOf(app("Z", 0, 0)), 0.5f).isEmpty())
    }
}
