package com.example.tasama.util

import androidx.compose.ui.geometry.Offset
import com.example.tasama.domain.model.RoutePoint
import kotlin.math.*

data class Location(val latitude: Double, val longitude: Double)

fun calculateDistance(p1: Location, p2: Location): Double {
    val r = 6371e3 // Earth's radius in meters
    val phi1 = p1.latitude * PI / 180
    val phi2 = p2.latitude * PI / 180
    val deltaPhi = (p2.latitude - p1.latitude) * PI / 180
    val deltaLambda = (p2.longitude - p1.longitude) * PI / 180

    val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) *
            sin(deltaLambda / 2) * sin(deltaLambda / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return r * c
}

fun Double.format(digits: Int): String {
    val factor = 10.0.pow(digits)
    return (round(this * factor) / factor).toString()
}

/**
 * Decodes an encoded polyline string into a list of Location points.
 */
fun decodePolyline(encoded: String): List<Location> {
    val poly = ArrayList<Location>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val p = Location(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(p)
    }

    return poly
}

fun getPolylineMidpoint(points: List<Location>): Location? {
    if (points.isEmpty()) return null
    if (points.size == 1) return points[0]

    var totalDistance = 0.0
    for (i in 0 until points.size - 1) {
        totalDistance += calculateDistance(points[i], points[i+1])
    }

    val midDistance = totalDistance / 2.0
    var currentDistance = 0.0
    for (i in 0 until points.size - 1) {
        val segmentDist = calculateDistance(points[i], points[i+1])
        if (currentDistance + segmentDist >= midDistance) {
            val ratio = if (segmentDist > 0) (midDistance - currentDistance) / segmentDist else 0.0
            val lat = points[i].latitude + (points[i+1].latitude - points[i].latitude) * ratio
            val lng = points[i].longitude + (points[i+1].longitude - points[i].longitude) * ratio
            return Location(lat, lng)
        }
        currentDistance += segmentDist
    }
    return points.last()
}

/**
 * Clips a line segment to the screen rectangle using Liang-Barsky algorithm.
 * Returns the clipped segment endpoints as a pair of Offsets.
 */
fun clipSegmentToRect(p1: Offset, p2: Offset, width: Float, height: Float): Pair<Offset, Offset>? {
    var t0 = 0f
    var t1 = 1f
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y

    fun clip(p: Float, q: Float): Boolean {
        if (p == 0f) return q >= 0
        val t = q / p
        if (p < 0) {
            if (t > t1) return false
            if (t > t0) t0 = t
        } else if (p > 0) {
            if (t < t0) return false
            if (t < t1) t1 = t
        }
        return true
    }

    if (clip(-dx, p1.x) && clip(dx, width - p1.x) &&
        clip(-dy, p1.y) && clip(dy, height - p1.y)) {
        return Pair(
            Offset(p1.x + t0 * dx, p1.y + t0 * dy),
            Offset(p1.x + t1 * dx, p1.y + t1 * dy)
        )
    }
    return null
}

/**
 * Finds the intersection of a ray starting from 'start' toward 'end' with screen edges.
 */
fun findRayIntersection(start: Offset, end: Offset, width: Float, height: Float): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y

    val tX = if (dx > 0) (width - start.x) / dx else if (dx < 0) -start.x / dx else Float.MAX_VALUE
    val tY = if (dy > 0) (height - start.y) / dy else if (dy < 0) -start.y / dy else Float.MAX_VALUE

    val t = min(tX, tY)
    return Offset(start.x + t * dx, start.y + t * dy)
}

/**
 * Function to apply avoidance logic to a screen point (e.g. for an off-screen marker).
 * Returns the final (x, y) coordinates clamped to edges and avoiding UI elements.
 */
fun applyUIAvoidance(
    point: Offset,
    width: Float,
    height: Float,
    avoidanceMarginPx: Float,
    indicatorRadiusPx: Float,
    headerHeightPx: Float,
    fabsWidthPx: Float,
    fabsHeightPx: Float
): Offset {
    var finalX = point.x
    var finalY = point.y

    // 1. Avoid Header (Full-width top area)
    if (finalY < headerHeightPx + indicatorRadiusPx + avoidanceMarginPx) {
        finalY = headerHeightPx + indicatorRadiusPx + avoidanceMarginPx
    }

    // 2. Avoid FABs (Bottom Right)
    if (finalY > height - fabsHeightPx - indicatorRadiusPx - avoidanceMarginPx && 
        finalX > width - fabsWidthPx - indicatorRadiusPx - avoidanceMarginPx) {
        if (width - finalX < height - finalY) {
            finalX = width - fabsWidthPx - indicatorRadiusPx - avoidanceMarginPx
        } else {
            finalY = height - fabsHeightPx - indicatorRadiusPx - avoidanceMarginPx
        }
    }

    // 3. Final Screen Clamping
    finalX = finalX.coerceIn(indicatorRadiusPx + avoidanceMarginPx, width - indicatorRadiusPx - avoidanceMarginPx)
    finalY = finalY.coerceIn(indicatorRadiusPx + avoidanceMarginPx, height - indicatorRadiusPx - avoidanceMarginPx)

    return Offset(finalX, finalY)
}

data class MapMarkerVisibilityData(
    val isMeVisible: Boolean,
    val isPartnerVisible: Boolean,
    val myEffectiveLocation: Location,
    val partnerEffectiveLocation: Location,
    val myEdgePoint: Offset?,
    val partnerEdgePoint: Offset?,
    val myAngle: Float,
    val partnerAngle: Float,
    val showPolyline: Boolean,
    val partnerScreenPos: Offset?
)
