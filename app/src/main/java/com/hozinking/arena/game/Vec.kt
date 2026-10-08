package com.hozinking.arena.game

import kotlin.math.sqrt

/** Vektor 2D sederhana untuk posisi & gerak. */
data class Vec2(var x: Float, var y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun len() = sqrt(x * x + y * y)
    fun norm(): Vec2 {
        val l = len()
        return if (l > 0.0001f) Vec2(x / l, y / l) else Vec2(0f, 0f)
    }
    fun dist(o: Vec2) = (this - o).len()
    fun copy() = Vec2(x, y)
}
