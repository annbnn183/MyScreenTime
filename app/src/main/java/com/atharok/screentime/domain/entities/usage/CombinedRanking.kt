package com.atharok.screentime.domain.entities.usage

/** 综合排行榜里的一行。 */
data class CombinedRankedApp(
    val app: RankedApp,
    /** 从 1 开始的名次。 */
    val rank: Int,
    /** 综合占比（0..1）：时长占比与点击占比按权重相加，所有应用的综合占比之和为 1。 */
    val score: Float,
    /** 该应用时长占本范围内所有应用总时长的比例（0..1）。 */
    val durationShare: Float,
    /** 该应用点击量占本范围内所有应用总点击量的比例（0..1）。 */
    val launchShare: Float
)

/**
 * 把「屏幕使用时长」和「点击（打开）次数」合成一个排名。
 *
 * 做法：先分别算出每个应用在本范围内的时长占比、点击占比，再按权重相加：
 * `综合占比 = 时长占比 × w + 点击占比 × (1 - w)`。
 * 用「占总量的比例」而不是原始数值，是因为两者量纲不同（毫秒 vs 次数），
 * 直接相加会被数值大的一项完全支配；占比则都落在 0..1 且各自总和为 1，可以公平地加权。
 */
object CombinedRanking {

    const val WEIGHT_TIME_FIRST = 0.7f
    const val WEIGHT_BALANCED = 0.5f
    const val WEIGHT_LAUNCH_FIRST = 0.3f

    /**
     * @param durationWeight 时长占比的权重（0..1），点击占比的权重为 `1 - durationWeight`。
     *
     * 本范围内没有任何点击量记录时，权重自动退化为只看时长，避免一半权重白白变成 0；
     * 反过来没有任何时长时只看点击量。
     * 同分时依次按时长、名称排序，保证结果稳定。
     */
    fun rank(apps: List<RankedApp>, durationWeight: Float): List<CombinedRankedApp> {
        val candidates = apps.filter { it.duration > 0L || it.launchCount > 0 }
        if (candidates.isEmpty()) return emptyList()

        val totalDuration: Long = candidates.sumOf { it.duration }
        val totalLaunches: Long = candidates.sumOf { it.launchCount.toLong() }

        val weight: Double = when {
            totalLaunches <= 0L -> 1.0
            totalDuration <= 0L -> 0.0
            else -> durationWeight.coerceIn(0f, 1f).toDouble()
        }

        val scored = candidates.map { app ->
            val durationShare = if (totalDuration > 0L) app.duration.toDouble() / totalDuration else 0.0
            val launchShare = if (totalLaunches > 0L) app.launchCount.toDouble() / totalLaunches else 0.0
            CombinedRankedApp(
                app = app,
                rank = 0,
                score = (durationShare * weight + launchShare * (1.0 - weight)).toFloat(),
                durationShare = durationShare.toFloat(),
                launchShare = launchShare.toFloat()
            )
        }

        return scored
            .sortedWith(
                compareByDescending<CombinedRankedApp> { it.score }
                    .thenByDescending { it.app.duration }
                    .thenBy { it.app.appName.lowercase() }
            )
            .mapIndexed { index, item -> item.copy(rank = index + 1) }
    }
}
