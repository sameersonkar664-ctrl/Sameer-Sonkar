package com.example.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class GlMesh(
    val vertexBuffer: FloatBuffer,
    val vertexCount: Int,
    val drawMode: Int = GLES20.GL_TRIANGLES,
)

internal object GlMeshUtils {

    const val VERTEX_SHADER = """
        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        varying vec3 vWorldPos;
        varying vec3 vWorldNormal;
        void main() {
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            vWorldPos = worldPos.xyz;
            vWorldNormal = mat3(uModelMatrix) * aNormal;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            gl_PointSize = 3.5;
        }
    """

    const val FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec3 uColor;
        uniform vec3 uEmissive;
        uniform vec3 uAmbientColor;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform vec3 uFogColor;
        uniform float uFogDensity;
        uniform vec3 uCarPos;
        uniform float uHeadlightInt;
        uniform float uNvgMode;
        uniform float uIsPoint;
        varying vec3 vWorldPos;
        varying vec3 vWorldNormal;

        void main() {
            if (uIsPoint > 0.5) {
                vec3 pCol = uNvgMode > 0.5 ? vec3(0.35, 1.0, 0.55) : uColor;
                gl_FragColor = vec4(pCol, 1.0);
                return;
            }

            vec3 n = normalize(vWorldNormal);
            float diff = max(dot(n, normalize(uSunDir)), 0.0);
            vec3 lit = uColor * (uAmbientColor + uSunColor * diff) + uEmissive;

            // Player twin headlight beams projecting forward (-Z)
            vec3 toFrag = vWorldPos - vec3(uCarPos.x, 0.65, -2.0);
            if (toFrag.z < 0.0 && toFrag.z > -78.0) {
                float fwd = -toFrag.z;
                float leftDx = abs(toFrag.x + 0.65);
                float rightDx = abs(toFrag.x - 0.65);
                float minDx = min(leftDx, rightDx);
                float coneRadius = 1.6 + fwd * 0.14;
                float radial = clamp(1.0 - minDx / coneRadius, 0.0, 1.0);
                float atten = clamp(1.0 - fwd / 78.0, 0.0, 1.0);
                float heightFade = clamp(1.0 - abs(vWorldPos.y - 0.3) * 0.28, 0.25, 1.0);
                float beam = radial * radial * atten * atten * heightFade * uHeadlightInt;
                lit += uColor * vec3(1.0, 0.96, 0.82) * beam;
            }

            // Exponential fog (fogExp2)
            float dist = length(vWorldPos - vec3(uCarPos.x * 0.55, 4.2, 8.2));
            float fogFactor = 1.0 - clamp(exp(-uFogDensity * uFogDensity * dist * dist), 0.0, 1.0);
            vec3 finalColor = mix(lit, uFogColor, fogFactor);

            if (uNvgMode > 0.5) {
                float lum = dot(finalColor, vec3(0.299, 0.587, 0.114));
                float boosted = clamp(pow(lum * 1.9 + 0.04, 0.82), 0.0, 1.25);
                float scan = 0.92 + 0.08 * sin(gl_FragCoord.y * 1.4);
                finalColor = vec3(boosted * 0.14, boosted * 1.0, boosted * 0.42) * scan;
            }

            gl_FragColor = vec4(finalColor, 1.0);
        }
    """

    fun compileShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    fun linkProgram(vs: Int, fs: Int): Int {
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    fun createUnitBox(): GlMesh {
        // 36 vertices: x, y, z, nx, ny, nz
        val p = 0.5f
        val n = -0.5f
        val data = floatArrayOf(
            // Front (+Z)
            n, n, p, 0f, 0f, 1f,  p, n, p, 0f, 0f, 1f,  p, p, p, 0f, 0f, 1f,
            n, n, p, 0f, 0f, 1f,  p, p, p, 0f, 0f, 1f,  n, p, p, 0f, 0f, 1f,
            // Back (-Z)
            p, n, n, 0f, 0f, -1f, n, n, n, 0f, 0f, -1f, n, p, n, 0f, 0f, -1f,
            p, n, n, 0f, 0f, -1f, n, p, n, 0f, 0f, -1f, p, p, n, 0f, 0f, -1f,
            // Left (-X)
            n, n, n, -1f, 0f, 0f, n, n, p, -1f, 0f, 0f, n, p, p, -1f, 0f, 0f,
            n, n, n, -1f, 0f, 0f, n, p, p, -1f, 0f, 0f, n, p, n, -1f, 0f, 0f,
            // Right (+X)
            p, n, p, 1f, 0f, 0f,  p, n, n, 1f, 0f, 0f,  p, p, n, 1f, 0f, 0f,
            p, n, p, 1f, 0f, 0f,  p, p, n, 1f, 0f, 0f,  p, p, p, 1f, 0f, 0f,
            // Top (+Y)
            n, p, p, 0f, 1f, 0f,  p, p, p, 0f, 1f, 0f,  p, p, n, 0f, 1f, 0f,
            n, p, p, 0f, 1f, 0f,  p, p, n, 0f, 1f, 0f,  n, p, n, 0f, 1f, 0f,
            // Bottom (-Y)
            n, n, n, 0f, -1f, 0f, p, n, n, 0f, -1f, 0f, p, n, p, 0f, -1f, 0f,
            n, n, n, 0f, -1f, 0f, p, n, p, 0f, -1f, 0f, n, n, p, 0f, -1f, 0f,
        )
        return GlMesh(toFloatBuffer(data), 36)
    }

    fun createUnitCone(segments: Int = 8): GlMesh {
        val list = ArrayList<Float>(segments * 3 * 6)
        val step = (2.0 * PI / segments)
        for (i in 0 until segments) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val x0 = (cos(a0) * 0.5).toFloat()
            val z0 = (sin(a0) * 0.5).toFloat()
            val x1 = (cos(a1) * 0.5).toFloat()
            val z1 = (sin(a1) * 0.5).toFloat()
            val nx = cos((a0 + a1) * 0.5).toFloat()
            val nz = sin((a0 + a1) * 0.5).toFloat()
            // Side triangle (base y=-0.5, tip y=+0.5)
            list.addAll(listOf(x0, -0.5f, z0, nx, 0.4f, nz))
            list.addAll(listOf(x1, -0.5f, z1, nx, 0.4f, nz))
            list.addAll(listOf(0f, 0.5f, 0f, nx, 0.4f, nz))
        }
        return GlMesh(toFloatBuffer(list.toFloatArray()), segments * 3)
    }

    fun createStarField(count: Int = 420): GlMesh {
        val data = FloatArray(count * 6)
        val rng = java.util.Random(42L)
        for (i in 0 until count) {
            val idx = i * 6
            data[idx] = (rng.nextFloat() - 0.5f) * 240f
            data[idx + 1] = 16f + rng.nextFloat() * 75f
            data[idx + 2] = -35f - rng.nextFloat() * 175f
            data[idx + 3] = 0f
            data[idx + 4] = 1f
            data[idx + 5] = 0f
        }
        return GlMesh(toFloatBuffer(data), count, GLES20.GL_POINTS)
    }

    fun colorToRgb(hex: Int, out: FloatArray) {
        out[0] = ((hex shr 16) and 0xFF) / 255f
        out[1] = ((hex shr 8) and 0xFF) / 255f
        out[2] = (hex and 0xFF) / 255f
    }

    fun lerpRgb(hexA: Int, hexB: Int, t: Float, out: FloatArray) {
        val c = t.coerceIn(0f, 1f)
        val ar = ((hexA shr 16) and 0xFF) / 255f
        val ag = ((hexA shr 8) and 0xFF) / 255f
        val ab = (hexA and 0xFF) / 255f
        val br = ((hexB shr 16) and 0xFF) / 255f
        val bg = ((hexB shr 8) and 0xFF) / 255f
        val bb = (hexB and 0xFF) / 255f
        out[0] = ar + (br - ar) * c
        out[1] = ag + (bg - ag) * c
        out[2] = ab + (bb - ab) * c
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(arr)
                position(0)
            }
}
