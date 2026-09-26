package com.trafficgauge.app.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 행정안전부/한국지역정보개발원 "전국 통합 교차로 신호등 실시간 정보" (data.go.kr).
 *
 * TODO — fill in once the 활용신청(approval) page is available:
 *   - The exact path segments below (INTERSECTION_INFO_PATH / SIGNAL_TIMING_PATH) are
 *     placeholders. data.go.kr shows the real "요청 URL" on the dataset's Open API detail
 *     page after approval — copy it in here.
 *   - Field names on [IntersectionInfoItem]/[SignalTimingItem] follow the same
 *     {direction}{movement}Sg{Rmdr|Stat}{Cs|Nm} convention already confirmed against
 *     Seoul's V2X API (같은 경찰청/행안부 표준 규격), but haven't been checked against a
 *     real response from this specific nationwide dataset yet.
 *
 * Response envelope follows the standard data.go.kr Open API shape
 * (response.body.items.item / totalCount / pageNo / numOfRows).
 */
interface NationwideSignalApiService {

    @GET(INTERSECTION_INFO_PATH)
    suspend fun getIntersections(
        @Query("serviceKey") serviceKey: String,
        @Query("type") type: String = "json",
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int = 1000,
    ): DataGoKrEnvelope<IntersectionInfoItem>

    @GET(SIGNAL_TIMING_PATH)
    suspend fun getSignalTiming(
        @Query("serviceKey") serviceKey: String,
        @Query("type") type: String = "json",
        @Query("itstId") intersectionId: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 10,
    ): DataGoKrEnvelope<SignalTimingItem>

    companion object {
        // TODO: replace with the real paths from the data.go.kr "요청 URL" once approved.
        const val INTERSECTION_INFO_PATH = "TODO/intersectionInfo"
        const val SIGNAL_TIMING_PATH = "TODO/signalRemainTimeInfo"
    }
}

data class DataGoKrEnvelope<T>(
    val response: DataGoKrResponse<T>? = null,
)

data class DataGoKrResponse<T>(
    val header: DataGoKrHeader? = null,
    val body: DataGoKrBody<T>? = null,
)

data class DataGoKrHeader(
    val resultCode: String? = null,
    val resultMsg: String? = null,
)

data class DataGoKrBody<T>(
    val items: DataGoKrItems<T>? = null,
    val totalCount: Int? = null,
    val pageNo: Int? = null,
    val numOfRows: Int? = null,
)

data class DataGoKrItems<T>(
    val item: List<T> = emptyList(),
)

data class IntersectionInfoItem(
    val itstId: String? = null,
    val itstNm: String? = null,
    val itstLat: String? = null,
    val itstLot: String? = null,
    val ctpvCd: String? = null,
    val ctpvNm: String? = null,
    val spdLmt: String? = null,
) {
    fun latitude(): Double? = itstLat?.toDoubleOrNull()
    fun longitude(): Double? = itstLot?.toDoubleOrNull()
}

/**
 * One row of {direction}{movement}Sg{Rmdr|Stat}{Cs|Nm} fields. Only a Gson [Map] is used
 * here (rather than 48 explicit properties) so parsing doesn't have to hardcode a giant
 * field list; [SignalTimingRepository] looks up the exact key it needs at read time.
 */
typealias SignalTimingItem = Map<String, Any?>
