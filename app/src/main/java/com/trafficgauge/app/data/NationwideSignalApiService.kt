package com.trafficgauge.app.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 행정안전부/한국지역정보개발원(KLID) "전국 교차로 신호제어기 실시간 정보" (data.go.kr, B551982/rti).
 *
 * Endpoint, envelope shape, operation names and field naming here are taken from a working
 * open-source client for this exact API (github.com/kangsiwoo/when-i-off,
 * KlidHttpClient/KlidSignalApi/SignalStateExploder) rather than guessed from the dataset
 * description page, so this part is on firmer ground than the earlier Seoul-only attempt.
 *
 * Coverage caveat: that project's own field testing (2026-09, ARCHITECTURE.md/#30) found
 * `tl_drct_info` (the real-time remaining-time operation) returning live, second-by-second
 * data only for 울산광역시 (stdgCd 3100000000) — 399 intersections there, changing on every
 * poll. A direct test against this app's own service key found Seoul (1100000000) now
 * returns exactly one row with a suspiciously round timestamp ("2026-09-26 00:00:00") that
 * didn't change across repeated polls a few seconds apart — plausibly a static placeholder
 * rather than a live reading. Coverage is clearly still rolling out unevenly by region, so
 * don't assume Seoul-or-elsewhere works just because the call succeeds — see
 * CompositeSignalTimingSource for the fallback that keeps the gauge working regardless.
 * `crsrd_map_info` (the static intersection list) is well populated nationwide either way
 * (Seoul alone: 2779 rows, confirmed live).
 */
interface NationwideSignalApiService {

    @GET("$BASE_PATH/$OP_INTERSECTION_INFO")
    suspend fun getIntersections(
        @Query("serviceKey") serviceKey: String,
        @Query("type") type: String = "json",
        @Query("stdgCd") stdgCd: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int = 1000,
    ): KlidEnvelope

    @GET("$BASE_PATH/$OP_SIGNAL_TIMING")
    suspend fun getSignalTiming(
        @Query("serviceKey") serviceKey: String,
        @Query("type") type: String = "json",
        @Query("stdgCd") stdgCd: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 1000,
    ): KlidEnvelope

    companion object {
        const val BASE_PATH = "B551982/rti"
        const val OP_INTERSECTION_INFO = "crsrd_map_info"
        const val OP_SIGNAL_TIMING = "tl_drct_info"

        const val RESULT_OK = "K0"
        const val RESULT_NODATA = "K3"
    }
}

data class KlidEnvelope(
    val header: KlidHeader? = null,
    val body: KlidBody? = null,
)

data class KlidHeader(
    val resultCode: String? = null,
    val resultMsg: String? = null,
)

data class KlidBody(
    val items: KlidItems? = null,
    val totalCount: Int? = null,
    val pageNo: Int? = null,
    val numOfRows: Int? = null,
)

data class KlidItems(
    /** Each row's fields as a raw string map — both operations return differently-shaped
     * rows (intersection metadata vs. the ~48 direction/movement signal fields), and the
     * signal one especially isn't worth declaring as 48 explicit properties. */
    val item: List<Map<String, String>> = emptyList(),
)
