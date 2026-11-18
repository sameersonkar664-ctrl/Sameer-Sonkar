package com.example.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.model.CarSpec
import com.example.model.GameCatalog
import com.example.model.GamePhase
import com.example.model.HudSnapshot
import com.example.model.LevelSpec
import java.util.concurrent.CopyOnWriteArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.random.Random

interface EngineEventListener {
    fun onHudTick(snapshot: HudSnapshot)
    fun onBatteryPickedUp()
    fun onNvgAutoOff(reasonLowBattery: Boolean)
    fun onLevelCleared(distanceMeters: Int, batteryLeft: Int)
    fun onPlayerCrashed(distanceMeters: Int, batteryLeft: Int)
}

class HighwayGlRenderer(
    private val listener: EngineEventListener,
) : GLSurfaceView.Renderer {

    private enum class EntityKind { ONCOMING, STALLED, ROADBLOCK, BATTERY }

    private data class WorldEntity(
        val kind: EntityKind,
        var x: Float,
        var z: Float,
        val halfW: Float,
        val halfL: Float,
        val relSpeed: Float,
        val colorHex: Int,
        var spin: Float = 0f,
    )

    private data class TreeProp(
        val x: Float,
        var z: Float,
    )

    // External thread-safe inputs from Compose/Sensors
    @Volatile var leftPressed: Boolean = false
    @Volatile var rightPressed: Boolean = false
    @Volatile var gasPressed: Boolean = false
    @Volatile var brakePressed: Boolean = false
    @Volatile var dragSteer: Float = 0f
    @Volatile var dragActive: Boolean = false
    @Volatile var tiltSteer: Float = 0f
    @Volatile var tiltEnabled: Boolean = false

    // Simulation state
    @Volatile private var phase: GamePhase = GamePhase.MENU
    @Volatile private var selectedCar: CarSpec = GameCatalog.cars[0]
    @Volatile private var selectedLevel: LevelSpec = GameCatalog.levels[0]
    @Volatile private var nvgActive: Boolean = false

    private var battery: Float = 100f
    private var speed: Float = 0f
    private var distance: Float = 0f
    private var carX: Float = 0f
    private var carVX: Float = 0f
    private var carShowcaseYaw: Float = 0f
    private var cycleTime: Float = 0f
    private var spawnTimer: Float = 0.8f
    private var batteryTimer: Float = 4.5f
    private var lastFrameNs: Long = 0L
    private var lastHudReportMs: Long = 0L

    private val roadWidth = 14f
    private val dashZPositions = FloatArray(20) { idx -> 12f - idx * 10f }
    private val trees = ArrayList<TreeProp>(34)
    private val entities = CopyOnWriteArrayList<WorldEntity>()

    // GL handles & matrices
    private var program = 0
    private var aPosLoc = 0
    private var aNormLoc = 0
    private var uMvpLoc = 0
    private var uModelLoc = 0
    private var uColorLoc = 0
    private var uEmissiveLoc = 0
    private var uAmbientLoc = 0
    private var uSunDirLoc = 0
    private var uSunColorLoc = 0
    private var uFogColorLoc = 0
    private var uFogDensityLoc = 0
    private var uCarPosLoc = 0
    private var uHeadlightLoc = 0
    private var uNvgLoc = 0
    private var uIsPointLoc = 0

    private lateinit var boxMesh: GlMesh
    private lateinit var coneMesh: GlMesh
    private lateinit var starMesh: GlMesh

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val skyRgb = FloatArray(3)
    private val fogRgb = FloatArray(3)
    private val ambientRgb = FloatArray(3)
    private val sunRgb = FloatArray(3)
    private val roadRgb = FloatArray(3)
    private val tmpRgb = FloatArray(3)
    private val zeroRgb = floatArrayOf(0f, 0f, 0f)

    private var fogDensity = 0.0085f
    private var headlightIntensity = 1.2f
    private var starsVisible = false

    init {
        for (i in 0 until 34) {
            val side = if (i % 2 == 0) -1f else 1f
            val x = side * (roadWidth / 2f + 3.5f + Random.nextFloat() * 14f)
            trees.add(TreeProp(x = x, z = 10f - i * 5.5f))
        }
    }

    fun selectCar(car: CarSpec) {
        selectedCar = car
    }

    fun selectLevel(level: LevelSpec) {
        selectedLevel = level
    }

    fun startRun(car: CarSpec, level: LevelSpec) {
        selectedCar = car
        selectedLevel = level
        phase = GamePhase.PLAYING
        nvgActive = false
        battery = 100f
        speed = car.maxSpeed * 0.45f
        distance = 0f
        carX = 0f
        carVX = 0f
        carShowcaseYaw = 0f
        cycleTime = 0f
        spawnTimer = 0.8f
        batteryTimer = 4.5f
        entities.clear()
        reportHud(force = true)
    }

    fun openMenu() {
        phase = GamePhase.MENU
        nvgActive = false
        carX = 0f
        carVX = 0f
        entities.clear()
        reportHud(force = true)
    }

    fun toggleNvg(): Boolean {
        if (phase != GamePhase.PLAYING) return false
        if (!nvgActive && battery <= 4f) {
            listener.onNvgAutoOff(reasonLowBattery = true)
            return false
        }
        nvgActive = !nvgActive
        reportHud(force = true)
        return true
    }

    fun currentNvgState(): Boolean = nvgActive

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        val vs = GlMeshUtils.compileShader(GLES20.GL_VERTEX_SHADER, GlMeshUtils.VERTEX_SHADER)
        val fs = GlMeshUtils.compileShader(GLES20.GL_FRAGMENT_SHADER, GlMeshUtils.FRAGMENT_SHADER)
        program = GlMeshUtils.linkProgram(vs, fs)

        aPosLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aNormLoc = GLES20.glGetAttribLocation(program, "aNormal")
        uMvpLoc = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        uModelLoc = GLES20.glGetUniformLocation(program, "uModelMatrix")
        uColorLoc = GLES20.glGetUniformLocation(program, "uColor")
        uEmissiveLoc = GLES20.glGetUniformLocation(program, "uEmissive")
        uAmbientLoc = GLES20.glGetUniformLocation(program, "uAmbientColor")
        uSunDirLoc = GLES20.glGetUniformLocation(program, "uSunDir")
        uSunColorLoc = GLES20.glGetUniformLocation(program, "uSunColor")
        uFogColorLoc = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogDensityLoc = GLES20.glGetUniformLocation(program, "uFogDensity")
        uCarPosLoc = GLES20.glGetUniformLocation(program, "uCarPos")
        uHeadlightLoc = GLES20.glGetUniformLocation(program, "uHeadlightInt")
        uNvgLoc = GLES20.glGetUniformLocation(program, "uNvgMode")
        uIsPointLoc = GLES20.glGetUniformLocation(program, "uIsPoint")

        boxMesh = GlMeshUtils.createUnitBox()
        coneMesh = GlMeshUtils.createUnitCone(8)
        starMesh = GlMeshUtils.createStarField(420)
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, 62f, aspect, 0.2f, 240f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0.016f else min(0.05f, (nowNs - lastFrameNs) / 1_000_000_000f)
        lastFrameNs = nowNs

        updateSimulation(dt)
        computeEnvironmentColors()

        GLES20.glClearColor(skyRgb[0], skyRgb[1], skyRgb[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(program)

        val camX = carX * 0.55f
        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, 4.2f, 8.2f,
            carX * 0.35f, 1.0f, -18f,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glUniform3fv(uAmbientLoc, 1, ambientRgb, 0)
        GLES20.glUniform3f(uSunDirLoc, 0.35f, 0.85f, 0.25f)
        GLES20.glUniform3fv(uSunColorLoc, 1, sunRgb, 0)
        GLES20.glUniform3fv(uFogColorLoc, 1, fogRgb, 0)
        GLES20.glUniform1f(uFogDensityLoc, fogDensity)
        GLES20.glUniform3f(uCarPosLoc, carX, 0f, 0f)
        GLES20.glUniform1f(uHeadlightLoc, headlightIntensity)
        GLES20.glUniform1f(uNvgLoc, if (nvgActive) 1f else 0f)

        // Stars
        if (starsVisible) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES20.glUniform1f(uIsPointLoc, 1f)
            GLES20.glUniform3f(uColorLoc, 0.95f, 0.98f, 1f)
            GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
            drawMesh(starMesh)
        }
        GLES20.glUniform1f(uIsPointLoc, 0f)

        // Ground & Road
        drawBox(0f, -0.15f, -100f, 240f, 0.2f, 320f, 0x0B1310, 0x000000)
        drawBoxRgb(0f, -0.04f, -100f, roadWidth, 0.08f, 320f, roadRgb, zeroRgb)

        // Guardrails
        val edgeCol = if (nvgActive) 0x22FF66 else 0x64748B
        val edgeEm = if (nvgActive) 0x15803D else 0x000000
        drawBox(-roadWidth / 2f - 0.2f, 0.14f, -100f, 0.35f, 0.28f, 320f, edgeCol, edgeEm)
        drawBox(roadWidth / 2f + 0.2f, 0.14f, -100f, 0.35f, 0.28f, 320f, edgeCol, edgeEm)

        // Lane Dashes
        val dashCol = if (nvgActive) 0x4ADE80 else 0xE2E8F0
        val dashEm = if (nvgActive) 0x16A34A else 0x1E293B
        for (z in dashZPositions) {
            drawBox(-roadWidth / 6f, 0.02f, z, 0.24f, 0.03f, 3.8f, dashCol, dashEm)
            drawBox(roadWidth / 6f, 0.02f, z, 0.24f, 0.03f, 3.8f, dashCol, dashEm)
        }

        // Roadside Trees
        val crownCol = if (nvgActive) 0x16A34A else 0x14532D
        for (tree in trees) {
            drawBox(tree.x, 1.1f, tree.z, 0.45f, 2.2f, 0.45f, 0x3E2723, 0x000000)
            drawCone(tree.x, 3.4f, tree.z, 3.0f, 3.6f, 3.0f, crownCol, 0x000000)
        }

        // Entities (Traffic, Roadblocks, Battery pickups)
        for (e in entities) {
            when (e.kind) {
                EntityKind.ROADBLOCK -> {
                    drawBox(e.x, 0.5f, e.z, 3.2f, 1.0f, 0.8f, 0x334155, 0x000000)
                    drawBox(e.x, 0.65f, e.z, 3.24f, 0.26f, 0.84f, 0xF59E0B, 0x78350F)
                }
                EntityKind.BATTERY -> {
                    drawRotatedBox(e.x, 0.95f, e.z, 1.1f, 1.1f, 1.1f, e.spin, 0x22FF66, 0x16A34A)
                }
                EntityKind.ONCOMING -> {
                    drawCar(e.x, e.z, yawDeg = 180f, rollDeg = 0f, bodyColorHex = e.colorHex, isPlayer = false)
                }
                EntityKind.STALLED -> {
                    drawCar(e.x, e.z, yawDeg = 0f, rollDeg = 0f, bodyColorHex = 0x1E293B, isPlayer = false)
                }
            }
        }

        // Player Car
        val yaw = if (phase == GamePhase.MENU) carShowcaseYaw else -carVX * 0.85f
        val roll = if (phase == GamePhase.MENU) 0f else -carVX * 1.4f
        drawCar(carX, 0f, yawDeg = yaw, rollDeg = roll, bodyColorHex = selectedCar.colorInt, isPlayer = true)
    }

    private fun updateSimulation(dt: Float) {
        if (phase == GamePhase.MENU) {
            carShowcaseYaw = (carShowcaseYaw + dt * 34f) % 360f
            return
        }
        if (phase != GamePhase.PLAYING) return

        val car = selectedCar
        val lvl = selectedLevel
        val topSpd = car.maxSpeed * lvl.speedMult

        speed = when {
            gasPressed -> min(topSpd, speed + 36f * dt)
            brakePressed -> (speed - 58f * dt).coerceAtLeast(16f)
            else -> {
                val cruise = topSpd * 0.58f
                if (speed < cruise) min(cruise, speed + 16f * dt)
                else (speed - 10f * dt).coerceAtLeast(cruise)
            }
        }

        var steer = (if (rightPressed) 1f else 0f) - (if (leftPressed) 1f else 0f)
        if (tiltEnabled && abs(tiltSteer) > 0.05f) {
            steer = tiltSteer
        } else if (dragActive && abs(dragSteer) > 0.02f) {
            steer = dragSteer
        }

        val targetVX = steer * 11.5f * car.handling
        carVX += (targetVX - carVX) * min(1f, dt * 10f)
        carX += carVX * dt
        val maxBound = roadWidth / 2f - 1.3f
        carX = carX.coerceIn(-maxBound, maxBound)

        distance += speed * dt * 0.65f
        if (lvl.isCycle) {
            cycleTime += dt * 0.16f
        }

        // Check Level Completion
        val target = lvl.targetMeters
        if (target != null && distance >= target) {
            phase = GamePhase.LEVEL_UP
            nvgActive = false
            reportHud(force = true)
            listener.onLevelCleared(distance.toInt(), battery.toInt())
            return
        }

        // NVG Battery drain / recharge
        if (nvgActive) {
            battery -= car.battDrain * dt
            if (battery <= 0f) {
                battery = 0f
                nvgActive = false
                listener.onNvgAutoOff(reasonLowBattery = false)
            }
        } else {
            battery = min(100f, battery + 6.5f * dt)
        }

        // Scroll road dashes & trees
        for (i in dashZPositions.indices) {
            dashZPositions[i] += speed * dt
            if (dashZPositions[i] > 16f) dashZPositions[i] -= 200f
        }
        for (tree in trees) {
            tree.z += speed * dt
            if (tree.z > 14f) tree.z -= 187f
        }

        // Spawn hazards & batteries
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnHazard()
            spawnTimer = lvl.trafficRate * (0.7f + Random.nextFloat() * 0.55f)
        }

        batteryTimer -= dt
        if (batteryTimer <= 0f) {
            spawnBattery()
            batteryTimer = 6.0f + Random.nextFloat() * 4.0f
        }

        // Update entities & check collisions
        val toRemove = ArrayList<WorldEntity>()
        for (e in entities) {
            e.z += (speed + e.relSpeed) * dt
            if (e.kind == EntityKind.BATTERY) e.spin = (e.spin + dt * 140f) % 360f

            if (e.z > 14f) {
                toRemove.add(e)
                continue
            }

            if (abs(e.z) < (2.0f + e.halfL) * 0.82f && abs(carX - e.x) < (0.95f + e.halfW) * 0.85f) {
                if (e.kind == EntityKind.BATTERY) {
                    battery = min(100f, battery + 35f)
                    toRemove.add(e)
                    listener.onBatteryPickedUp()
                } else {
                    phase = GamePhase.GAME_OVER
                    nvgActive = false
                    entities.removeAll(toRemove)
                    reportHud(force = true)
                    listener.onPlayerCrashed(distance.toInt(), battery.toInt())
                    return
                }
            }
        }
        if (toRemove.isNotEmpty()) entities.removeAll(toRemove)

        reportHud(force = false)
    }

    private fun spawnHazard() {
        val lanes = floatArrayOf(-4.2f, 0f, 4.2f)
        val laneX = lanes[Random.nextInt(lanes.size)] + (Random.nextFloat() - 0.5f) * 0.6f
        val roll = Random.nextFloat()
        val kind = when {
            roll < 0.36f -> EntityKind.ROADBLOCK
            roll < 0.58f -> EntityKind.STALLED
            else -> EntityKind.ONCOMING
        }
        val palette = intArrayOf(0x64748B, 0x3B82F6, 0x10B981, 0xA855F7, 0xE11D48)
        val col = if (kind == EntityKind.STALLED) 0x1E293B else palette[Random.nextInt(palette.size)]
        entities.add(
            WorldEntity(
                kind = kind,
                x = laneX,
                z = -175f,
                halfW = if (kind == EntityKind.ROADBLOCK) 1.55f else 0.95f,
                halfL = if (kind == EntityKind.ROADBLOCK) 0.45f else 2.0f,
                relSpeed = if (kind == EntityKind.ONCOMING) 24f else 0f,
                colorHex = col,
            ),
        )
    }

    private fun spawnBattery() {
        val lanes = floatArrayOf(-4.2f, 0f, 4.2f)
        val laneX = lanes[Random.nextInt(lanes.size)]
        entities.add(
            WorldEntity(
                kind = EntityKind.BATTERY,
                x = laneX,
                z = -175f,
                halfW = 0.85f,
                halfL = 0.85f,
                relSpeed = 0f,
                colorHex = 0x22FF66,
            ),
        )
    }

    private fun computeEnvironmentColors() {
        val lvl = selectedLevel
        if (nvgActive) {
            GlMeshUtils.colorToRgb(0x021407, skyRgb)
            GlMeshUtils.colorToRgb(0x04240D, fogRgb)
            fogDensity = 0.008f
            ambientRgb[0] = 0.35f; ambientRgb[1] = 0.72f; ambientRgb[2] = 0.42f
            sunRgb[0] = 0.55f; sunRgb[1] = 0.95f; sunRgb[2] = 0.65f
            GlMeshUtils.colorToRgb(0x092913, roadRgb)
            headlightIntensity = 1.4f
            starsVisible = true
            return
        }

        if (lvl.isCycle) {
            val dayFactor = (cos(cycleTime.toDouble()).toFloat() + 1f) * 0.5f
            GlMeshUtils.lerpRgb(0x02040A, 0x38BDF8, dayFactor, skyRgb)
            GlMeshUtils.lerpRgb(0x030712, 0x7DD3FC, dayFactor, fogRgb)
            fogDensity = 0.007f + (1f - dayFactor) * 0.017f
            GlMeshUtils.lerpRgb(0x0F172A, 0xE0F2FE, dayFactor, ambientRgb)
            val ambScale = 0.25f + dayFactor * 0.72f
            ambientRgb[0] *= ambScale; ambientRgb[1] *= ambScale; ambientRgb[2] *= ambScale
            val sunScale = 0.25f + dayFactor * 0.85f
            sunRgb[0] = sunScale; sunRgb[1] = sunScale; sunRgb[2] = sunScale
            GlMeshUtils.lerpRgb(0x0B0F17, 0x2A3140, dayFactor, roadRgb)
            headlightIntensity = 2.6f - dayFactor * 1.9f
            starsVisible = dayFactor < 0.35f
        } else {
            GlMeshUtils.colorToRgb(lvl.skyColor, skyRgb)
            GlMeshUtils.colorToRgb(lvl.fogColor, fogRgb)
            fogDensity = lvl.fogDensity
            GlMeshUtils.colorToRgb(lvl.ambientColor, ambientRgb)
            ambientRgb[0] *= lvl.ambientIntensity * 0.72f
            ambientRgb[1] *= lvl.ambientIntensity * 0.72f
            ambientRgb[2] *= lvl.ambientIntensity * 0.72f
            GlMeshUtils.colorToRgb(lvl.sunColor, sunRgb)
            sunRgb[0] *= lvl.sunIntensity * 0.65f
            sunRgb[1] *= lvl.sunIntensity * 0.65f
            sunRgb[2] *= lvl.sunIntensity * 0.65f
            GlMeshUtils.colorToRgb(lvl.roadColor, roadRgb)
            headlightIntensity = if (lvl.isNight) 2.8f else 0.5f
            starsVisible = lvl.isNight
        }
    }

    private fun reportHud(force: Boolean) {
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastHudReportMs < 50L) return
        lastHudReportMs = nowMs

        val ratio = (speed / selectedCar.maxSpeed).coerceIn(0f, 1.35f)
        val kmh = (ratio * selectedCar.topKmhDisplay).toInt()
        listener.onHudTick(
            HudSnapshot(
                speedKmh = kmh,
                speedRatio = ratio,
                distanceMeters = distance.toInt(),
                batteryPercent = battery.toInt(),
                nvgActive = nvgActive,
                phase = phase,
            ),
        )
    }

    private fun drawCar(
        x: Float,
        z: Float,
        yawDeg: Float,
        rollDeg: Float,
        bodyColorHex: Int,
        isPlayer: Boolean,
    ) {
        // Transform hierarchy via local matrix composition
        fun part(lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float, col: Int, em: Int) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, x, 0f, z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
            Matrix.translateM(modelMatrix, 0, lx, ly, lz)
            Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GlMeshUtils.colorToRgb(col, tmpRgb)
            GLES20.glUniform3fv(uColorLoc, 1, tmpRgb, 0)
            GlMeshUtils.colorToRgb(em, tmpRgb)
            GLES20.glUniform3fv(uEmissiveLoc, 1, tmpRgb, 0)
            GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
            drawMesh(boxMesh)
        }

        // Chassis + cabin + roof
        part(0f, 0.52f, 0f, 1.9f, 0.58f, 4.1f, bodyColorHex, 0x000000)
        part(0f, 1.0f, -0.15f, 1.52f, 0.52f, 2.05f, 0x0F172A, 0x000000)
        part(0f, 1.28f, -0.15f, 1.44f, 0.08f, 1.65f, bodyColorHex, 0x000000)

        if (isPlayer) {
            part(0f, 1.02f, 1.85f, 1.75f, 0.08f, 0.35f, bodyColorHex, 0x000000)
        }

        // 4 Wheels
        part(-0.98f, 0.36f, -1.25f, 0.28f, 0.72f, 0.72f, 0x111111, 0x000000)
        part(0.98f, 0.36f, -1.25f, 0.28f, 0.72f, 0.72f, 0x111111, 0x000000)
        part(-0.98f, 0.36f, 1.25f, 0.28f, 0.72f, 0.72f, 0x111111, 0x000000)
        part(0.98f, 0.36f, 1.25f, 0.28f, 0.72f, 0.72f, 0x111111, 0x000000)

        // Taillights & Headlights (emissive)
        part(-0.62f, 0.58f, 2.06f, 0.42f, 0.14f, 0.08f, 0xFF2222, 0xDC2626)
        part(0.62f, 0.58f, 2.06f, 0.42f, 0.14f, 0.08f, 0xFF2222, 0xDC2626)
        part(-0.62f, 0.54f, -2.06f, 0.38f, 0.14f, 0.08f, 0xFEF08A, 0xFACC15)
        part(0.62f, 0.54f, -2.06f, 0.38f, 0.14f, 0.08f, 0xFEF08A, 0xFACC15)
    }

    private fun drawBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        colorHex: Int, emissiveHex: Int,
    ) {
        GlMeshUtils.colorToRgb(colorHex, tmpRgb)
        val em = FloatArray(3)
        GlMeshUtils.colorToRgb(emissiveHex, em)
        drawBoxRgb(x, y, z, sx, sy, sz, tmpRgb, em)
    }

    private fun drawRotatedBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float,
        colorHex: Int, emissiveHex: Int,
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GlMeshUtils.colorToRgb(colorHex, tmpRgb)
        GLES20.glUniform3fv(uColorLoc, 1, tmpRgb, 0)
        GlMeshUtils.colorToRgb(emissiveHex, tmpRgb)
        GLES20.glUniform3fv(uEmissiveLoc, 1, tmpRgb, 0)
        GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        drawMesh(boxMesh)
    }

    private fun drawBoxRgb(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rgb: FloatArray, emissiveRgb: FloatArray,
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniform3fv(uColorLoc, 1, rgb, 0)
        GLES20.glUniform3fv(uEmissiveLoc, 1, emissiveRgb, 0)
        GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        drawMesh(boxMesh)
    }

    private fun drawCone(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        colorHex: Int, emissiveHex: Int,
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GlMeshUtils.colorToRgb(colorHex, tmpRgb)
        GLES20.glUniform3fv(uColorLoc, 1, tmpRgb, 0)
        GlMeshUtils.colorToRgb(emissiveHex, tmpRgb)
        GLES20.glUniform3fv(uEmissiveLoc, 1, tmpRgb, 0)
        GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        drawMesh(coneMesh)
    }

    private fun drawMesh(mesh: GlMesh) {
        val stride = 6 * 4
        mesh.vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)

        mesh.vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(aNormLoc)
        GLES20.glVertexAttribPointer(aNormLoc, 3, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)

        GLES20.glDrawArrays(mesh.drawMode, 0, mesh.vertexCount)
    }
}
