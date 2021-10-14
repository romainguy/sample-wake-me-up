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

@file:Suppress("NOTHING_TO_INLINE")

package com.example.wakemeup.math

import kotlin.math.floor

const val Pi         = 3.1415926536f
const val HalfPi    = Pi * 0.5f
const val TwoPi     = Pi * 2.0f
const val FourPi    = Pi * 4.0f
const val InvPi     = 1.0f / Pi
const val InvTwoPi  = InvPi * 0.5f
const val InvFourPi = InvPi * 0.25f

inline fun clamp(x: Float, min: Float, max: Float)= if (x < min) min else (if (x > max) max else x)

inline fun saturate(x: Float) = clamp(x, 0.0f, 1.0f)

inline fun mix(a: Float, b: Float, x: Float) = a * (1.0f - x) + b * x

inline fun degrees(v: Float) = v * (180.0f * InvPi)

inline fun radians(v: Float) = v * (Pi / 180.0f)

inline fun fract(v: Float) = v - floor(v)

inline fun sqr(v: Float) = v * v

inline fun pow(x: Float, y: Float) = StrictMath.pow(x.toDouble(), y.toDouble()).toFloat()

inline fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = clamp((x - e0) / (e1 - e0), 0.0f, 1.0f)
    return t * t * (3.0f - 2.0f * t)
}
