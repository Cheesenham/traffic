package com.trafficgauge.app.routing

import com.trafficgauge.app.data.GeoUtils

/**
 * The KLID signal API (data.go.kr B551982/rti) only filters by `stdgCd`, the 10-digit
 * 법정동코드 for a 시/도 (province/metro-city) — e.g. 서울특별시 = 1100000000,
 * 울산광역시 = 3100000000 (confirmed against a live client, see NationwideSignalApiService).
 * There's no lat/lon or intersection-id query, so a GPS point has to be mapped to its
 * containing 시/도 first.
 *
 * This does that with a plain nearest-provincial-center lookup rather than real reverse
 * geocoding — coarse (city-level filter only, so precision at a province border doesn't
 * matter much), self-contained (no extra API/network round trip), and the 17 codes
 * themselves are static national standard codes that don't change.
 */
object SidoCodes {
    private data class Sido(val name: String, val stdgCd: String, val centerLat: Double, val centerLon: Double)

    private val SIDOS = listOf(
        Sido("서울특별시", "1100000000", 37.5665, 126.9780),
        Sido("부산광역시", "2600000000", 35.1796, 129.0756),
        Sido("대구광역시", "2700000000", 35.8714, 128.6014),
        Sido("인천광역시", "2800000000", 37.4563, 126.7052),
        Sido("광주광역시", "2900000000", 35.1595, 126.8526),
        Sido("대전광역시", "3000000000", 36.3504, 127.3845),
        Sido("울산광역시", "3100000000", 35.5384, 129.3114),
        Sido("세종특별자치시", "3611000000", 36.4801, 127.2890),
        Sido("경기도", "4100000000", 37.4138, 127.5183),
        Sido("강원특별자치도", "5100000000", 37.8228, 128.1555),
        Sido("충청북도", "4300000000", 36.6357, 127.4917),
        Sido("충청남도", "4400000000", 36.5184, 126.8000),
        Sido("전북특별자치도", "5200000000", 35.7175, 127.1530),
        Sido("전라남도", "4600000000", 34.8161, 126.4630),
        Sido("경상북도", "4700000000", 36.4919, 128.8889),
        Sido("경상남도", "4800000000", 35.4606, 128.2132),
        Sido("제주특별자치도", "5000000000", 33.4996, 126.5312),
    )

    fun stdgCdFor(lat: Double, lon: Double): String =
        SIDOS.minBy { GeoUtils.distanceMeters(lat, lon, it.centerLat, it.centerLon) }.stdgCd
}
