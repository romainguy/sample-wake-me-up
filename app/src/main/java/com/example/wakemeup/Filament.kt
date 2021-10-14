package com.example.wakemeup

import android.content.res.AssetManager
import com.example.wakemeup.math.Float4
import com.example.wakemeup.math.radians
import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import kotlin.math.cos
import kotlin.math.sin

const val SunAngularRadius = 2.2f
const val SunHaloSize = 2.0f
const val SunHaloFalloff = 1.0f
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
const val SunLightIntensity = 100_000.0f

const val Aperture = 16f
const val ShutterSpeed = 1f / 125f
const val Sensitivity = 100f

data class SkyScene(
    val engine: Engine,
    val scene: Scene,
    val skybox: Int,
    val skyboxMaterial: MaterialInstance
)

fun computeSunDisc(
    radius: Float = radians(SunAngularRadius),
    haloSize: Float = SunHaloSize,
    haloFalloff: Float = SunHaloFalloff
) = Float4(
    cos(radius),
    sin(radius),
    1.0f / (cos(radius * haloSize) - cos(radius)),
    haloFalloff
)

fun exposure(aperture: Float, shutterSpeed: Float, sensitivity: Float): Float {
    val e = (aperture * aperture) / shutterSpeed * 100.0f / sensitivity
    return 1.0f / (1.2f * e)
}

fun readUncompressedAsset(assets: AssetManager, assetName: String): ByteBuffer {
    assets.openFd(assetName).use { fd ->
        val input = fd.createInputStream()
        val dst = ByteBuffer.allocate(fd.length.toInt())

        val src = Channels.newChannel(input)
        src.read(dst)
        src.close()

        return dst.apply { rewind() }
    }
}

fun createFullscreenTriangle(engine: Engine): Pair<IndexBuffer, VertexBuffer> {
    val floatSize = 4
    val shortSize = 2
    val vertexSize = 4 * floatSize

    fun ByteBuffer.put(v: Float4): ByteBuffer {
        putFloat(v.x)
        putFloat(v.y)
        putFloat(v.z)
        putFloat(v.w)
        return this
    }

    val vertexCount = 3
    val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
        .order(ByteOrder.nativeOrder())
        .put(Float4(-1.0f, -1.0f, 1.0f, 1.0f))
        .put(Float4( 3.0f, -1.0f, 1.0f, 1.0f))
        .put(Float4(-1.0f,  3.0f, 1.0f, 1.0f))
        .flip()

    val vertexBuffer = VertexBuffer.Builder()
        .bufferCount(1)
        .vertexCount(vertexCount)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION, 0,
            VertexBuffer.AttributeType.FLOAT4, 0,
            vertexSize
        )
        .build(engine)
    vertexBuffer.setBufferAt(engine, 0, vertexData)

    val indexData = ByteBuffer.allocate(vertexCount * shortSize)
        .order(ByteOrder.nativeOrder())
        .putShort(0)
        .putShort(1)
        .putShort(2)
        .flip()

    val indexBuffer = IndexBuffer.Builder()
        .indexCount(3)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(engine)
    indexBuffer.setBuffer(engine, indexData)

    return indexBuffer to vertexBuffer
}
