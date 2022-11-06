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

package com.example.wakemeup.graphics

import android.view.Surface
import android.view.SurfaceView
import com.example.wakemeup.Aperture
import com.example.wakemeup.Sensitivity
import com.example.wakemeup.ShutterSpeed
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper

private const val NearPlane = 0.5
private const val FarPlane = 100.0
private const val FovDegrees = 60.0

class Viewer(
    val engine: Engine,
    val surfaceView: SurfaceView
) {
    val view: View = engine.createView()
    val camera: Camera = EntityManager.get().create().run {
        engine.createCamera(this).apply { setExposure(Aperture, ShutterSpeed, Sensitivity) }
    }

    var scene: Scene? = null
        set(value) {
            view.scene = value
            field = value
        }

    private val uiHelper: UiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var displayHelper: DisplayHelper
    private var swapChain: SwapChain? = null
    private val renderer: Renderer = engine.createRenderer()

    init {
        view.camera = camera

        displayHelper = DisplayHelper(surfaceView.context)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(surfaceView)
        addDetachListener(surfaceView)

        camera.lookAt(
            0.0, 0.0,  0.0,
            0.0, 0.3, -1.0,
            0.0, 1.0,  0.0
        )
    }

    fun render(frameTimeNanos: Long) {
        if (!uiHelper.isReadyToRender) {
            return
        }

        // Render the scene, unless the renderer wants to skip the frame.
        if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun addDetachListener(view: android.view.View) {
        class AttachListener : android.view.View.OnAttachStateChangeListener {
            var detached = false

            override fun onViewAttachedToWindow(v: android.view.View) { detached = false }

            override fun onViewDetachedFromWindow(v: android.view.View) {
                if (!detached) {
                    uiHelper.detach()

                    engine.destroyRenderer(renderer)
                    engine.destroyView(this@Viewer.view)
                    engine.destroyCameraComponent(camera.entity)

                    detached = true
                }
            }
        }
        view.addOnAttachStateChangeListener(AttachListener())
    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, surfaceView.display)
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            android.util.Log.d("Filament", "w=$width, h=$height")
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(FovDegrees, aspect, NearPlane, FarPlane, Camera.Fov.VERTICAL)
        }
    }
}

fun setupViewer(viewer: Viewer) {
    viewer.view.run {
        antiAliasing = View.AntiAliasing.NONE

        dynamicResolutionOptions = dynamicResolutionOptions.apply {
            enabled = true
        }

        renderQuality = View.RenderQuality().apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }
    }
}
