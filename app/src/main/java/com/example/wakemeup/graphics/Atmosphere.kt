/*
 * MIT License
 *
 * Copyright (c) 2021 Romain Guy
 * Copyright (c) 2021 Felix Westin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

// The atmosphere scattering code is adapted from https://github.com/Fewes/MinimalAtmosphere

package com.example.wakemeup.graphics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.example.wakemeup.WakeupCall
import com.example.wakemeup.math.*
import com.example.wakemeup.ui.theme.NightBlue
import net.e175.klaus.solarpositioning.DeltaT
import net.e175.klaus.solarpositioning.SPA
import java.util.*
import kotlin.math.*

private const val Sunrise = 0
private const val Sunset  = 2

private const val ScatteringSampleCount   = 32
private const val OpticalDepthSampleCount = 8

private const val Eps = 1e-6f

private const val Exposure = 40.0f

private const val PlanetRadius      = 6_371_000.0f
private const val AtmosphereDensity = 1.0f
private const val AtmosphereHeight  = 100_000.0f
private const val RayleighHeight    = AtmosphereHeight * 0.080f
private const val MieHeight         = AtmosphereHeight * 0.012f

private val PlanetCenter = Float3(0.0f, -PlanetRadius, 0.0f)

private val CoefficientsRayleigh = Float3(5.802f, 13.558f, 33.100f) * Eps
private val CoefficientsMie      = Float3(3.996f,  3.996f,  3.996f) * Eps
private val CoefficientsOzone    = Float3(0.650f,  1.881f,  0.085f) * Eps

private fun sphereIntersection(
    rayStart: Float3,
    rayDir: Float3,
    sphereCenter: Float3,
    sphereRadius: Float
): Float2 {
    val start = rayStart - sphereCenter
    val a = dot(rayDir, rayDir)
    val b = 2.0f * dot(start, rayDir)
    val c = dot(start, start) - (sphereRadius * sphereRadius)
    val d = b * b - 4.0f * a * c
    return if (d < 0.0) {
        Float2(-1.0f)
    } else {
        val e = sqrt(d)
        Float2(-b - e, -b + e) / (2.0f * a)
    }
}

private fun planetIntersection(rayStart: Float3, rayDir: Float3) =
    sphereIntersection(rayStart, rayDir, PlanetCenter, PlanetRadius)

private fun atmosphereIntersection(rayStart: Float3, rayDir: Float3) =
    sphereIntersection(rayStart, rayDir, PlanetCenter, PlanetRadius + AtmosphereHeight)

private fun phaseMie(costh: Float, g: Float = 0.85f): Float {
    val mg = min(g, 0.9381f)
    val k = 1.55f * g - 0.55f * mg * mg * mg
    val kcosth = k * costh
    return (1.0f - k * k) / ((4.0f * Pi) * (1.0f - kcosth) * (1.0f - kcosth))
}

private fun phaseRayleigh(costh: Float) = 3.0f * (1.0f + costh * costh) / (16.0f * Pi)

private fun atmosphereHeight(positionWS: Float3) = distance(positionWS, PlanetCenter) - PlanetRadius

private fun densityRayleigh(h: Float) = exp(-max(0.0f, h / RayleighHeight))

private fun densityMie(h: Float) = exp(-max(0.0f, h / MieHeight))

private fun densityOzone(h: Float) = max(0.0f, 1.0f - abs(h - 25000.0f) / 15000.0f)

private fun atmosphereDensity(h: Float) = Float3(densityRayleigh(h), densityMie(h), densityOzone(h))

private fun integrateOpticalDepth(rayStart: Float3, rayDir: Float3): Float3 {
    val intersection = atmosphereIntersection(rayStart, rayDir)
    val rayLength    = intersection.y

    val sampleCount  = OpticalDepthSampleCount
    val stepSize     = rayLength / sampleCount.toFloat()

    var opticalDepth = Float3(0.0f)

    for (i in 0 until sampleCount) {
        val localPosition = rayStart + rayDir * (i.toFloat() + 0.5f) * stepSize
        val localHeight   = atmosphereHeight(localPosition)
        val localDensity  = atmosphereDensity(localHeight) * stepSize

        opticalDepth += localDensity
    }

    return opticalDepth
}

private fun absorb(opticalDepth: Float3): Float3 {
    val absorption =
            opticalDepth.x * CoefficientsRayleigh +
            opticalDepth.y * CoefficientsMie * 1.1f +
            opticalDepth.z * CoefficientsOzone
    return Float3(
        exp(-absorption.x * AtmosphereDensity),
        exp(-absorption.y * AtmosphereDensity),
        exp(-absorption.z * AtmosphereDensity)
    )
}

private fun integrateScattering(
    rayStart: Float3,
    rayDir: Float3,
    rayLength: Float,
    lightDir: Float3,
    lightColor: Float3
): Float3 {
    val rayHeight = atmosphereHeight(rayStart)
    val sampleDistributionExponent = 1.0f + saturate(1.0f - rayHeight / AtmosphereHeight) * 8.0f

    val intersection = atmosphereIntersection(rayStart, rayDir)
    var start = rayStart
    var length = min(rayLength, intersection.y)
    if (intersection.x > 0.0f) {
        start += rayDir * intersection.x
        length -= intersection.x
    }

    val costh  = dot(rayDir, lightDir)
    val phaseR = phaseRayleigh(costh)
    val phaseM = phaseMie(costh)

    val sampleCount  = ScatteringSampleCount

    var opticalDepth = Float3(0.0f)
    var rayleigh     = Float3(0.0f)
    var mie          = Float3(0.0f)

    var prevRayTime  = 0.0f

    for (i in 0 until sampleCount) {
        val rayTime = pow(i / sampleCount.toFloat(), sampleDistributionExponent) * length
        val stepSize = rayTime - prevRayTime

        val localPosition = start + rayDir * rayTime
        val localHeight   = atmosphereHeight(localPosition)
        val localDensity  = atmosphereDensity(localHeight) * stepSize

        opticalDepth += localDensity

        val opticalDepthlight  = integrateOpticalDepth(localPosition, lightDir)
        val lightTransmittance = absorb(opticalDepth + opticalDepthlight)

        rayleigh += lightTransmittance * phaseR * localDensity.x
        mie      += lightTransmittance * phaseM * localDensity.y

        prevRayTime = rayTime
    }

    // We don't need to return the transmittance for our needs here
    // transmittance = absorb(opticalDepth);

    return (rayleigh * CoefficientsRayleigh + mie * CoefficientsMie) * lightColor
}

private fun integrateScatteringColor(
    lightDir: Float3,
    rayDir: Float3,
    rayStart: Float3 = Float3(0.0f),
    rayLength: Float = Float.POSITIVE_INFINITY,
    lightColor: Float3 = Float3(1.0f)
): Color {
    val color =
        (integrateScattering(rayStart, rayDir, rayLength, lightDir, lightColor) * Exposure).apply {
            this / max(this)
        }
    return Color(
        saturate(color.x),
        saturate(color.y),
        saturate(color.z),
        1.0f,
        ColorSpaces.LinearSrgb
    )
}

fun computeSunDirection(call: WakeupCall): Float3 {
    val refTime = GregorianCalendar()
    val dateTime = GregorianCalendar(call.timeZone)
    dateTime.set(
        refTime.get(GregorianCalendar.YEAR),
        refTime.get(GregorianCalendar.MONTH),
        refTime.get(GregorianCalendar.DAY_OF_MONTH),
        floor(call.time).toInt(),
        (fract(call.time) * 60.0f).toInt()
    )

    val position = SPA.calculateSolarPosition(
        dateTime,
        call.location.first,
        call.location.second,
        call.elevation,
        DeltaT.estimate(dateTime)
    )

    val zenith = HalfPi - radians(position.zenithAngle.toFloat())
    val azimuth = radians(position.azimuth.toFloat())

    return Float3(
        cos(zenith) * sin(azimuth),
        sin(zenith),
        cos(zenith) * cos(azimuth)
    )
}

fun computeSunsetTime(
    location: Pair<Double, Double>,
    timeZone: TimeZone
) = computeSunEventTime(location, timeZone, Sunset, 18.5f)

fun computeSunriseTime(
    location: Pair<Double, Double>,
    timeZone: TimeZone
) = computeSunEventTime(location, timeZone, Sunrise, 6.5f)

private fun computeSunEventTime(
    location: Pair<Double, Double>,
    timeZone: TimeZone,
    event: Int,
    defaultTime: Float
): Float {
    val refDate = GregorianCalendar()
    val date = GregorianCalendar(timeZone)
    date.set(
        refDate.get(GregorianCalendar.YEAR),
        refDate.get(GregorianCalendar.MONTH),
        refDate.get(GregorianCalendar.DAY_OF_MONTH)
    )

    // TODO: We should use a proper deltaT
    val events = SPA.calculateSunriseTransitSet(date, location.first, location.second, 68.0)
    val sunEventTime = events[event]
    if (sunEventTime != null) {
        val hour = sunEventTime.get(GregorianCalendar.HOUR_OF_DAY)
        val minutes = sunEventTime.get(GregorianCalendar.MINUTE)
        return hour.toFloat() + minutes / 60.0f
    }

    // The sun is always below the horizon, return a made up time
    return defaultTime
}

fun computeSunColor(direction: Float3) = integrateScatteringColor(direction, direction)

fun computeSunUiColor(direction: Float3, sunColor: Color) =
    if (planetIntersection(Float3(10.0f), direction).y > 0.0) {
        NightBlue
    } else {
        sunColor
    }
