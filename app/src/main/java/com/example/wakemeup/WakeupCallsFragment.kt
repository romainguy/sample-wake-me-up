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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.wakemeup.databinding.FragmentWakeupCallsBinding
import com.example.wakemeup.graphics.*
import com.example.wakemeup.math.Float3
import com.example.wakemeup.math.normalize
import com.example.wakemeup.math.pow
import com.example.wakemeup.math.saturate
import com.example.wakemeup.ui.theme.WakeMeUpTheme
import com.google.android.filament.*
import kotlin.math.max

class WakeupCallsFragment : Fragment() {
    //region Initialization
    private lateinit var engine: Engine
    private lateinit var skyMaterial: Material
    private lateinit var fullscreenTriangle: Pair<IndexBuffer, VertexBuffer>
    private var filamentInitialized = false
    private val scenes = mutableMapOf<Long, SkyScene>()

    companion object {
        const val ARG_CATEGORY_ID = "category_id"
    }

    private var category: Category? = null
    private lateinit var wakeupCalls: List<WakeupCall>

    private var _binding: FragmentWakeupCallsBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            if (it.containsKey(ARG_CATEGORY_ID)) {
                val id = it.getLong(ARG_CATEGORY_ID)
                category = Categories.find { category -> category.id ==  id}
                wakeupCalls = WakeupCalls
            }
        }

        initFilament()
    }
    //endregion

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = setupBindings(inflater, container)

        binding.composeView.setContent {
            WakeMeUpTheme {
                Column {
                    Header()
                    for (wakeupCall in wakeupCalls) {
                        AlarmCard(wakeupCall)
                    }
                }
            }
        }

        return rootView
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun AlarmCard(wakeupCall: WakeupCall) {
        val sunDirection = remember(wakeupCall.time) { computeSunDirection(wakeupCall) }
        val sunColor = remember(sunDirection) { computeSunColor(sunDirection) }
        val expanded = remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 6.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = cardColor(wakeupCall),
            contentColor = cardContentColor(wakeupCall)
        ) {
            Column(
                modifier = Modifier.clickable { expanded.value = !expanded.value }
            ) {
                AlarmTitle(wakeupCall)

                AnimatedVisibility(visible = expanded.value) {
                    AlarmEditor(wakeupCall, sunDirection, sunColor)
                }
            }
        }
    }

    @Composable
    fun Header() {
        Row(modifier = Modifier.padding(32.dp, 16.dp, 32.dp, 8.dp)) {
            Text(
                modifier = Modifier.weight(1.0f),
                text = "ALARM",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.secondaryVariant
            )
            Text(
                text = "LOCATION",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.secondaryVariant
            )
        }
    }

    @Composable
    fun AlarmTitle(wakeupCall: WakeupCall) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 8.dp),
                painter = if (wakeupCall.enabled) {
                    painterResource(id = R.drawable.outline_alarm_on_24)
                } else {
                    painterResource(id = R.drawable.outline_alarm_off_24)
                },
                tint = LocalContentColor.current,
                contentDescription = "Alarm state"
            )

            Text(
                modifier = Modifier.weight(1.0f),
                text = formattedTime(wakeupCall.time),
                style = MaterialTheme.typography.h4
            )

            Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = wakeupCall.name,
                style = MaterialTheme.typography.h5
            )
        }
    }

    @Composable
    fun AlarmEditor(wakeupCall: WakeupCall, sunDirection: Float3, sunColor: Color) {
        val sunUiColor = remember(sunDirection) { computeSunUiColor(sunDirection, sunColor) }

        Box {
            SkyViewer(wakeupCall, sunDirection, sunColor)

            Slider(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = sunUiColor,
                    activeTrackColor = sunUiColor
                ),
                valueRange = 0.0f..23.99f,
                value = wakeupCall.time,
                onValueChange = { v -> wakeupCall.time = v }
            )
        }
    }

    @Composable
    fun SkyViewer(wakeupCall: WakeupCall, sunDirection: Float3, sunColor: Color) {
        var viewer by remember { mutableStateOf<Viewer?>(null) }

        LaunchedEffect(sunDirection, sunColor, viewer) {
            withFrameNanos { frameTimeNanos ->
                val skyScene = scenes[wakeupCall.id]!!
                setSkyProperties(skyScene.skyboxMaterial, sunDirection, sunColor)
                val direction = normalize(Float3(sunDirection.x, 0.0f, sunDirection.z))
                viewer?.camera?.lookAt(
                    0.0, 0.0, 0.0,
                    direction.x.toDouble(), 0.3, direction.z.toDouble(),
                    0.0, 1.0, 0.0
                )
                viewer?.render(frameTimeNanos)
            }
        }

        AndroidView({ context ->
            LayoutInflater.from(context).inflate(
                R.layout.filament_host, FrameLayout(context), false
            ).apply {
                val skyScene = createScene(wakeupCall.id)
                viewer = Viewer(skyScene.engine, this as SurfaceView).also {
                    setupViewer(it)
                    it.scene = skyScene.scene
                }
            }
        })
    }

    //region Support function
    @Composable
    private fun cardContentColor(wakeupCall: WakeupCall) = if (wakeupCall.enabled) {
        MaterialTheme.colors.onSurface
    } else {
        MaterialTheme.colors.surface
    }

    @Composable
    private fun cardColor(wakeupCall: WakeupCall) = if (wakeupCall.enabled) {
        MaterialTheme.colors.surface
    } else {
        MaterialTheme.colors.background
    }

    private fun setupBindings(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): View {
        _binding = FragmentWakeupCallsBinding.inflate(inflater, container, false)

        val rootView = binding.root
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBarInsets.top, bottom = systemBarInsets.bottom)
            insets
        }

        binding.toolbarLayout.title = category?.name

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()

        if (filamentInitialized) {
            filamentInitialized = false

            scenes.forEach {
                engine.destroyScene(it.value.scene)
                engine.destroyEntity(it.value.skybox)
                engine.destroyMaterialInstance(it.value.skyboxMaterial)
            }

            engine.destroyIndexBuffer(fullscreenTriangle.first)
            engine.destroyVertexBuffer(fullscreenTriangle.second)
            engine.destroyMaterial(skyMaterial)
            engine.destroy()
        }
    }

    private fun initFilament() {
        if (!filamentInitialized) {
            filamentInitialized = true

            engine = Engine.create()

            readUncompressedAsset(context?.assets!!, "materials/sky.filamat").let {
                skyMaterial = Material.Builder().payload(it, it.remaining()).build(engine)
            }

            fullscreenTriangle = createFullscreenTriangle(engine)
        }
    }

    private fun createScene(id: Long): SkyScene {
        val scene = engine.createScene()

        val skyboxMaterial = skyMaterial.createInstance()
        val sun = computeSunDisc()
        skyboxMaterial.setParameter("sun", sun.x, sun.y, sun.z, sun.w)

        val skybox = EntityManager.get().create()
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                fullscreenTriangle.second,
                fullscreenTriangle.first
            )
            .material(0, skyboxMaterial)
            .castShadows(false)
            .receiveShadows(false)
            .priority(0x7)
            .culling(false)
            .build(engine, skybox)

        scene.addEntity(skybox)

        scenes[id] = SkyScene(engine, scene, skybox, skyboxMaterial)

        return scenes[id]!!
    }

    private fun setSkyProperties(sky: MaterialInstance, lightDirection: Float3, lightColor: Color) {
        sky.setParameter("lightDirection", lightDirection.x, lightDirection.y, lightDirection.z)
        sky.setParameter("lightColor", lightColor.red, lightColor.green, lightColor.blue)
        // TODO: we should compute the actual intensity of the sun disc in the given direction
        sky.setParameter("lightIntensity",
            SunLightIntensity * saturate(pow(max(lightDirection.y, 1e-3f), 0.6f)) *
                    exposure(Aperture, ShutterSpeed, Sensitivity))
    }
    //endregion
}
