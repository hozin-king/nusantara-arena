package com.hozinking.arena.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader

/**
 * Loader sprite orisinal Nusantara Arena (assets/sprites/*.png).
 * Bitmap dimuat sekali lalu di-cache. Unit netral (minion/tower/nexus)
 * di-tint warna tim via MULTIPLY agar satu sprite dipakai dua tim.
 */
object Sprites {
    private val cache = mutableMapOf<String, Bitmap>()
    private val tintCache = mutableMapOf<Int, Paint>()

    fun load(ctx: Context, name: String): Bitmap? {
        cache[name]?.let { return it }
        return try {
            ctx.assets.open("sprites/$name").use { ins ->
                BitmapFactory.decodeStream(ins)?.also { cache[name] = it }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Panggil sekali saat world mulai: pastikan semua sprite ter-cache. */
    fun preload(ctx: Context) {
        listOf(
            "hero_bara.png", "hero_wulan.png", "hero_bayu.png",
            "minion_melee.png", "minion_ranged.png",
            "tower.png", "nexus.png", "grass_tile.png"
        ).forEach { load(ctx, it) }
    }

    fun hero(kitId: String): Bitmap? = cache["hero_$kitId.png"]
    fun minion(ranged: Boolean): Bitmap? = cache[if (ranged) "minion_ranged.png" else "minion_melee.png"]
    fun tower(): Bitmap? = cache["tower.png"]
    fun nexus(): Bitmap? = cache["nexus.png"]
    fun grass(): Bitmap? = cache["grass_tile.png"]

    /** Paint tint warna tim (di-cache per warna). */
    fun tintPaint(color: Int): Paint = tintCache.getOrPut(color) {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
        }
    }

    val plainPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }

    private var grassPaint: Paint? = null

    /** Paint rumput ubin untuk background map. */
    fun grassPaint(): Paint? {
        grassPaint?.let { return it }
        val g = grass() ?: return null
        val shader = BitmapShader(g, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        return Paint().apply { this.shader = shader }.also { grassPaint = it }
    }
}
