/*
 * MIT License
 *
 * Copyright (c) 2021 Romain Guy
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

package com.example.wakemeup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.wakemeup.graphics.computeSunriseTime
import com.example.wakemeup.graphics.computeSunsetTime
import java.util.*

class WakeupCall(
    val id: Long,
    enabled: Boolean,
    time: Float,
    location: Pair<Double, Double>,
    elevation: Double,
    timeZone: TimeZone,
    name: String
) {
    var enabled by mutableStateOf(enabled)
    var time by mutableStateOf(time)
    var location by mutableStateOf(location)
    var elevation by mutableStateOf(elevation)
    var timeZone by mutableStateOf(timeZone)
    var name by mutableStateOf(name)
}

data class Category(val id: Long, val name: String, val photo: Int)

val Categories = listOf(
    Category(0, "Work", R.drawable.work),
    Category(1, "Friends", R.drawable.friends),
    Category(2, "Travel", R.drawable.travel),
    Category(3, "Kids", R.drawable.kids),
    Category(4, "Photo", R.drawable.photo),
    Category(5, "Workout", R.drawable.workout)
)

val WakeupCalls = listOf(
    WakeupCall(
        0,
        true,
        10.50f,
        37.45 to -122.18,
        10.0,
        TimeZone.getTimeZone("America/Los_Angeles"),
        "Mountain View"
    ),
    WakeupCall(
        2,
        true,
        computeSunsetTime(48.85 to 2.35, TimeZone.getTimeZone("Europe/Paris")) - 0.3f,
        48.85 to 2.35,
        30.0,
        TimeZone.getTimeZone("Europe/Paris"),
        "Paris"
    ),
    WakeupCall(
        3,
        false,
        computeSunriseTime(48.85 to 2.35, TimeZone.getTimeZone("Europe/Paris")) + 1.5f,
        48.85 to 2.35,
        30.0,
        TimeZone.getTimeZone("Europe/Paris"),
        "Paris"
    ),
    WakeupCall(
        4,
        true,
        5.84f,
        64.15 to -21.95,
        60.0,
        TimeZone.getTimeZone("UTC+0"),
        "Reykjavík"
    ),
    WakeupCall(
        5,
        false,
        15.75f,
        37.45 to -122.18,
        10.0,
        TimeZone.getTimeZone("America/Los_Angeles"),
        "Mountain View"
    ),
)
