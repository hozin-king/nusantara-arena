package com.hozinking.arena.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * SurfaceView utama: game loop, render Canvas 2D, input joystick +
 * tombol skill, layar menu / pilih hero / main / akhir.
 */
class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback {

    enum class Screen { MENU, PLAY, END }

    var screen = Screen.MENU
    private var selectedKit: HeroKit = Kits.BARA
    private var world: World? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastT = 0L

    // Kamera
    private var camX = 1500f
    private var camY = 1500f
    private var zoom = 1f

    // Joystick
    private var joyPid = -1
    private var joyOx = 0f; private var joyOy = 0f
    private var joyDx = 0f; private var joyDy = 0f

    // Paint (dibuat sekali)
    private val pBg = Paint().apply { color = 0xFF1B2A1B.toInt() }
    private val pLane = Paint().apply { color = 0x992A4226.toInt() }
    private val pBase = Paint().apply { color = 0xFF2E4A2E.toInt() }
    private val pText = Paint().apply { color = Color.WHITE; textSize = 42f; isAntiAlias = true }
    private val pSmall = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true }
    private val pTiny = Paint().apply { color = Color.WHITE; textSize = 22f; isAntiAlias = true }
    private val pBtn = Paint().apply { color = 0xFF33691E.toInt(); isAntiAlias = true }
    private val pBtnT = Paint().apply { color = Color.WHITE; textSize = 48f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val pHp = Paint()
    private val pHpBg = Paint().apply { color = 0xFF111111.toInt() }
    private val pJoy = Paint().apply { color = 0x88FFFFFF.toInt(); isAntiAlias = true }
    private val pJoyKnob = Paint().apply { color = 0xCCFFFFFF.toInt(); isAntiAlias = true }
    private val pCd = Paint().apply { color = 0xAA000000.toInt(); isAntiAlias = true }
    private val pMap = Paint().apply { color = 0xDD0D1B0D.toInt() }
    private val pFx = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    private val dstF = RectF()   // rect reuse untuk drawBitmap (hindari alokasi per frame)

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ---------------- loop ----------------

    override fun surfaceCreated(h: SurfaceHolder) {
        running = true
        lastT = System.nanoTime()
        thread = Thread {
            while (running) {
                val now = System.nanoTime()
                var dt = (now - lastT) / 1e9f
                lastT = now
                if (dt > 0.1f) dt = 0.1f
                if (screen == Screen.PLAY) {
                    world?.update(dt)
                    val w = world
                    if (w != null && w.winner != null) {
                        screen = Screen.END
                        if (w.winner == Team.BLUE) SoundFX.win() else SoundFX.lose()
                    }
                    updateCamera()
                }
                val c = holder.lockCanvas()
                if (c != null) {
                    try { drawAll(c) } finally { holder.unlockCanvasAndPost(c) }
                }
                try { Thread.sleep(16) } catch (_: InterruptedException) { }
            }
        }
        thread?.start()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) { }
        thread = null
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

    private fun updateCamera() {
        val w = world ?: return
        val p = w.player
        camX += (p.pos.x - camX) * 0.12f
        camY += (p.pos.y - camY) * 0.12f
        zoom = min(width / 1750f, height / 1050f)
            .coerceAtLeast(min(width, height) / 3400f)
        val vw = width / zoom / 2f
        val vh = height / zoom / 2f
        camX = camX.coerceIn(vw - 200f, World.W - vw + 200f)
        camY = camY.coerceIn(vh - 200f, World.H - vh + 200f)
    }

    // ---------------- draw ----------------

    private fun drawAll(c: Canvas) {
        when (screen) {
            Screen.MENU -> drawMenu(c)
            Screen.PLAY -> { drawWorld(c); drawHud(c) }
            Screen.END -> { drawWorld(c); drawEnd(c) }
        }
    }

    private fun drawMenu(c: Canvas) {
        c.drawColor(0xFF0D1B0D.toInt())
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 96f
        pText.color = 0xFFFFC107.toInt()
        c.drawText("NUSANTARA ARENA", width / 2f, 150f, pText)
        pSmall.textAlign = Paint.Align.CENTER
        pSmall.color = Color.WHITE
        c.drawText("MOBA 2D • 5v5 • Full Offline • Pilih heromu!", width / 2f, 215f, pSmall)

        // Kartu hero
        val cw = width / 3.6f
        val chh = 300f
        val y0 = 300f
        for ((i, kit) in Kits.ALL.withIndex()) {
            val x0 = width / 2f + (i - 1) * (cw + 40f) - cw / 2f
            val sel = kit == selectedKit
            pBtn.color = if (sel) 0xFF558B2F.toInt() else 0xFF1B3319.toInt()
            c.drawRoundRect(x0, y0, x0 + cw, y0 + chh, 24f, 24f, pBtn)
            pBtnT.textSize = 90f
            pBtnT.color = kit.color
            c.drawText(heroGlyph(kit.id), x0 + cw / 2f, y0 + 130f, pBtnT)
            pBtnT.textSize = 44f
            pBtnT.color = Color.WHITE
            c.drawText(kit.name, x0 + cw / 2f, y0 + 195f, pBtnT)
            pTiny.textAlign = Paint.Align.CENTER
            c.drawText(kit.title, x0 + cw / 2f, y0 + 240f, pTiny)
        }
        // Tombol MAIN
        pBtn.color = 0xFF2E7D32.toInt()
        c.drawRoundRect(width / 2f - 220f, y0 + chh + 60f, width / 2f + 220f, y0 + chh + 170f, 28f, 28f, pBtn)
        pBtnT.textSize = 56f
        pBtnT.color = Color.WHITE
        c.drawText("⚔ MAIN", width / 2f, y0 + chh + 140f, pBtnT)
        pTiny.textAlign = Paint.Align.CENTER
        pTiny.color = 0xFF9CCC9C.toInt()
        c.drawText("Hancurkan Nexus musuh! Joystick kiri = gerak, tombol kanan = serang & skill.", width / 2f, y0 + chh + 230f, pTiny)
    }

    private fun heroGlyph(id: String) = when (id) {
        "bara" -> "🔥"; "wulan" -> "🌙"; else -> "🌪"
    }

    private fun drawEnd(c: Canvas) {
        val w = world ?: return
        c.drawColor(0xAA000000.toInt())
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 110f
        val win = w.winner == Team.BLUE
        pText.color = if (win) 0xFFFFC107.toInt() else 0xFFEF5350.toInt()
        c.drawText(if (win) "🏆 MENANG!" else "💀 KALAH", width / 2f, height / 2f - 60f, pText)
        pSmall.textAlign = Paint.Align.CENTER
        pSmall.color = Color.WHITE
        c.drawText("Kill  ${w.blueKills} - ${w.redKills}   •   Waktu ${fmtTime(w.time)}", width / 2f, height / 2f + 20f, pSmall)
        pBtn.color = 0xFF2E7D32.toInt()
        c.drawRoundRect(width / 2f - 220f, height / 2f + 70f, width / 2f + 220f, height / 2f + 180f, 28f, 28f, pBtn)
        pBtnT.textSize = 52f
        pBtnT.color = Color.WHITE
        c.drawText("MAIN LAGI", width / 2f, height / 2f + 145f, pBtnT)
    }

    private fun fmtTime(t: Float): String {
        val s = t.toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    // ---------------- world render ----------------

    private fun drawWorld(c: Canvas) {
        val w = world ?: return
        c.drawColor(0xFF101D10.toInt())
        c.save()
        c.translate(width / 2f, height / 2f)
        c.scale(zoom, zoom)
        c.translate(-camX, -camY)

        // Background: rumput ubin (fallback warna datar bila sprite belum termuat)
        val gp = Sprites.grassPaint()
        if (gp != null) c.drawRect(0f, 0f, World.W, World.H, gp)
        else c.drawRect(0f, 0f, World.W, World.H, pBg)

        // Lanes (semi-transparan di atas rumput)
        for (y in World.LANES) {
            c.drawRect(0f, y - 90f, World.W, y + 90f, pLane)
        }
        // Base
        c.drawCircle(World.BLUE_X, 1500f, 260f, pBase)
        c.drawCircle(World.RED_X, 1500f, 260f, pBase)

        // Unit
        for (u in w.units) {
            if (!u.alive) continue
            when (u) {
                is Nexus -> {
                    val bmp = Sprites.nexus()
                    if (bmp != null) {
                        val half = u.radius * 1.35f
                        dstF.set(u.pos.x - half, u.pos.y - half, u.pos.x + half, u.pos.y + half)
                        c.drawBitmap(bmp, null, dstF, Sprites.tintPaint(u.team.color()))
                    } else {
                        val pp = Paint().apply { color = u.team.color(); isAntiAlias = true }
                        val s = u.radius
                        c.drawRect(u.pos.x - s, u.pos.y - s, u.pos.x + s, u.pos.y + s, pp)
                    }
                    drawHpBar(c, u, 130f)
                }
                is Tower -> {
                    val bmp = Sprites.tower()
                    if (bmp != null) {
                        val half = u.radius * 1.5f
                        dstF.set(u.pos.x - half, u.pos.y - half, u.pos.x + half, u.pos.y + half)
                        c.drawBitmap(bmp, null, dstF, Sprites.tintPaint(u.team.color()))
                    } else {
                        val pp = Paint().apply { color = u.team.darkColor(); isAntiAlias = true }
                        c.drawRect(u.pos.x - 34f, u.pos.y - 46f, u.pos.x + 34f, u.pos.y + 46f, pp)
                        val pp2 = Paint().apply { color = u.team.color(); isAntiAlias = true }
                        c.drawRect(u.pos.x - 34f, u.pos.y - 46f, u.pos.x + 34f, u.pos.y - 20f, pp2)
                    }
                    drawHpBar(c, u, 100f)
                }
                is Minion -> {
                    val bmp = Sprites.minion(u.ranged)
                    if (bmp != null) {
                        val half = u.radius * 1.7f
                        dstF.set(u.pos.x - half, u.pos.y - half, u.pos.x + half, u.pos.y + half)
                        c.drawBitmap(bmp, null, dstF, Sprites.tintPaint(u.team.color()))
                    } else {
                        val pp = Paint().apply { color = u.team.color(); isAntiAlias = true }
                        c.drawCircle(u.pos.x, u.pos.y, u.radius, pp)
                    }
                    drawHpBar(c, u, 44f, 26f)
                }
                is Hero -> {
                    val bmp = Sprites.hero(u.kit.id)
                    if (bmp != null) {
                        // Sprite menghadap arah gerak (sprite menghadap atas)
                        val ang = Math.toDegrees(atan2(u.faceDir.y.toDouble(), u.faceDir.x.toDouble())).toFloat() + 90f
                        val half = u.radius * 1.6f
                        c.save()
                        c.rotate(ang, u.pos.x, u.pos.y)
                        dstF.set(u.pos.x - half, u.pos.y - half, u.pos.x + half, u.pos.y + half)
                        c.drawBitmap(bmp, null, dstF, Sprites.plainPaint)
                        c.restore()
                    } else {
                        val pp = Paint().apply { color = u.team.color(); isAntiAlias = true }
                        c.drawCircle(u.pos.x, u.pos.y, u.radius, pp)
                        val f = Paint().apply { color = Color.WHITE; strokeWidth = 5f; isAntiAlias = true }
                        c.drawLine(u.pos.x, u.pos.y, u.pos.x + u.faceDir.x * (u.radius + 12f), u.pos.y + u.faceDir.y * (u.radius + 12f), f)
                    }
                    // Ring warna kit (identitas hero)
                    val ring = Paint().apply { color = u.kit.color; style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
                    c.drawCircle(u.pos.x, u.pos.y, u.radius + 4f, ring)
                    drawHpBar(c, u, 70f, 44f)
                    // Nama + level
                    pTiny.textAlign = Paint.Align.CENTER
                    pTiny.color = if (u.isPlayer) 0xFFFFC107.toInt() else Color.WHITE
                    c.drawText("${if (u.isPlayer) "▶ " else ""}${u.name} Lv${u.level}", u.pos.x, u.pos.y - u.radius - 26f, pTiny)
                }
            }
        }
        // Proyektil
        for (p in w.projectiles) {
            val pp = Paint().apply { color = p.color; isAntiAlias = true }
            c.drawCircle(p.pos.x, p.pos.y, p.radius + 3f, pp)
        }
        // Efek
        for (e in w.effects) {
            val f = 1f - e.life / e.maxLife
            pFx.color = e.color
            pFx.alpha = (255 * (1f - f)).toInt()
            c.drawCircle(e.pos.x, e.pos.y, e.r0 + (e.r1 - e.r0) * f, pFx)
        }
        // Pemain mati -> teks respawn
        val pl = w.player
        if (!pl.alive) {
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 40f / zoom.coerceAtLeast(0.5f)
            pText.color = Color.WHITE
            c.drawText("Respawn ${pl.respawnT.toInt() + 1} dtk...", camX, camY - 60f, pText)
        }
        c.restore()
    }

    private fun drawHpBar(c: Canvas, u: Unit, wdt: Float, yOff: Float = 34f) {
        val x = u.pos.x - wdt / 2f
        val y = u.pos.y - u.radius - yOff
        c.drawRect(x, y, x + wdt, y + 8f, pHpBg)
        pHp.color = when {
            u.hpFrac() > 0.5f -> 0xFF4CAF50.toInt()
            u.hpFrac() > 0.25f -> 0xFFFFC107.toInt()
            else -> 0xFFF44336.toInt()
        }
        c.drawRect(x, y, x + wdt * u.hpFrac(), y + 8f, pHp)
    }

    // ---------------- HUD ----------------

    private data class CircleBtn(val x: Float, val y: Float, val r: Float, val label: String)
    private val btns = mutableListOf<CircleBtn>()

    private fun drawHud(c: Canvas) {
        val w = world ?: return
        btns.clear()
        // Bar atas
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 36f
        pText.color = Color.WHITE
        c.drawText("${fmtTime(w.time)}   🔵 ${w.blueKills} - ${w.redKills} 🔴", width / 2f, 48f, pText)
        pTiny.textAlign = Paint.Align.CENTER
        c.drawText("🏰 ${w.towersAlive(Team.BLUE)} - ${w.towersAlive(Team.RED)} 🏰", width / 2f, 82f, pTiny)

        drawMinimap(c, w)

        // Joystick
        if (joyPid != -1) {
            c.drawCircle(joyOx, joyOy, 110f, pJoy)
            c.drawCircle(joyOx + joyDx, joyOy + joyDy, 48f, pJoyKnob)
        } else {
            pSmall.textAlign = Paint.Align.LEFT
            pSmall.color = 0x88FFFFFF.toInt()
            c.drawText("◀ joystick", 40f, height - 60f, pSmall)
        }

        // Tombol kanan: ATK + 3 skill
        val bx = width - 130f
        val by = height - 140f
        val pl = w.player
        addBtn(c, bx, by, 78f, "⚔", null, null)                       // attack
        addBtn(c, bx - 170f, by - 40f, 56f, "1", pl.skillCd[0], pl.kit.skills[0].cooldown)
        addBtn(c, bx - 90f, by - 150f, 56f, "2", pl.skillCd[1], pl.kit.skills[1].cooldown)
        addBtn(c, bx + 10f, by - 230f, 62f, "3", pl.skillCd[2], pl.kit.skills[2].cooldown)

        // HP bar pemain
        val bw = 420f
        val bx0 = width / 2f - bw / 2f
        val by0 = height - 52f
        c.drawRect(bx0, by0, bx0 + bw, by0 + 26f, pHpBg)
        pHp.color = 0xFF4CAF50.toInt()
        c.drawRect(bx0, by0, bx0 + bw * pl.hpFrac(), by0 + 26f, pHp)
        pTiny.textAlign = Paint.Align.CENTER
        pTiny.color = Color.WHITE
        c.drawText("${pl.kit.name} Lv${pl.level}  ${pl.hp.toInt()}/${pl.maxHp.toInt()}", width / 2f, by0 - 8f, pTiny)
    }

    private fun addBtn(c: Canvas, x: Float, y: Float, r: Float, label: String, cd: Float?, cdMax: Float?) {
        btns.add(CircleBtn(x, y, r, label))
        val pp = Paint().apply { color = 0xAA1B5E20.toInt(); isAntiAlias = true }
        c.drawCircle(x, y, r, pp)
        val tp = Paint().apply { color = Color.WHITE; textSize = r * 0.8f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        c.drawText(label, x, y + r * 0.28f, tp)
        if (cd != null && cdMax != null && cd > 0f) {
            c.drawArc(x - r, y - r, x + r, y + r, -90f, 360f * (cd / cdMax), true, pCd)
            val sp = Paint().apply { color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
            c.drawText(cd.toInt().plus(1).toString(), x, y + 10f, sp)
        }
    }

    private fun drawMinimap(c: Canvas, w: World) {
        val ms = 200f
        val mx = width - ms - 16f
        val my = 16f
        c.drawRect(mx, my, mx + ms, my + ms, pMap)
        val s = ms / World.W
        for (u in w.units) {
            val pp = Paint().apply { color = u.team.color() }
            val r = when (u) {
                is Hero -> 5f; is Tower -> 6f; is Nexus -> 8f; else -> 2.5f
            }
            c.drawCircle(mx + u.pos.x * s, my + u.pos.y * s, r, pp)
        }
        // Kotak kamera
        val vw = width / zoom
        val vh = height / zoom
        val cp = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
        c.drawRect(mx + (camX - vw / 2f) * s, my + (camY - vh / 2f) * s,
            mx + (camX + vw / 2f) * s, my + (camY + vh / 2f) * s, cp)
    }

    // ---------------- input ----------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = world
        when (screen) {
            Screen.MENU -> {
                if (e.action == MotionEvent.ACTION_DOWN) handleMenuTap(e.x, e.y)
                return true
            }
            Screen.END -> {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    // Tombol MAIN LAGI
                    if (e.x in width / 2f - 220f..width / 2f + 220f &&
                        e.y in height / 2f + 70f..height / 2f + 180f) {
                        screen = Screen.MENU
                    }
                }
                return true
            }
            Screen.PLAY -> { /* lanjut */ }
        }
        if (w == null) return true
        val pl = w.player
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pi = e.actionIndex
                val x = e.getX(pi); val y = e.getY(pi)
                // Cek tombol dulu
                var hitBtn = false
                for (b in btns) {
                    val d = kotlin.math.sqrt((x - b.x) * (x - b.x) + (y - b.y) * (y - b.y))
                    if (d <= b.r + 20f) {
                        onButton(b.label, w, pl)
                        hitBtn = true
                        break
                    }
                }
                if (!hitBtn && x < width * 0.45f && joyPid == -1) {
                    joyPid = e.getPointerId(pi)
                    joyOx = x; joyOy = y; joyDx = 0f; joyDy = 0f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (pi in 0 until e.pointerCount) {
                    if (e.getPointerId(pi) == joyPid) {
                        var dx = e.getX(pi) - joyOx
                        var dy = e.getY(pi) - joyOy
                        val len = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (len > 110f) { dx = dx / len * 110f; dy = dy / len * 110f }
                        joyDx = dx; joyDy = dy
                        pl.moveDir = Vec2(dx / 110f, dy / 110f)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pi = e.actionIndex
                if (e.getPointerId(pi) == joyPid) {
                    joyPid = -1
                    pl.moveDir = Vec2(0f, 0f)
                }
            }
        }
        return true
    }

    private fun onButton(label: String, w: World, pl: Hero) {
        when (label) {
            "⚔" -> {
                // Serang musuh terdekat dalam jangkauan
                var best: Unit? = null
                var bd = pl.range * 1.6f + 60f
                for (u in w.units) {
                    if (!u.alive || u.team == pl.team) continue
                    val d = pl.pos.dist(u.pos)
                    if (d < bd) { bd = d; best = u }
                }
                if (best != null) {
                    pl.target = best
                    pl.faceDir = (best.pos - pl.pos).norm()
                    SoundFX.hit()
                }
            }
            "1" -> if (w.castSkill(pl, 0)) SoundFX.skill()
            "2" -> if (w.castSkill(pl, 1)) SoundFX.skill()
            "3" -> if (w.castSkill(pl, 2)) SoundFX.ult()
        }
    }

    private fun handleMenuTap(x: Float, y: Float) {
        val cw = width / 3.6f
        val chh = 300f
        val y0 = 300f
        for ((i, kit) in Kits.ALL.withIndex()) {
            val x0 = width / 2f + (i - 1) * (cw + 40f) - cw / 2f
            if (x in x0..x0 + cw && y in y0..y0 + chh) {
                selectedKit = kit
                SoundFX.hit()
                return
            }
        }
        if (x in width / 2f - 220f..width / 2f + 220f && y in y0 + chh + 60f..y0 + chh + 170f) {
            Sprites.preload(context)   // cache sprite sebelum match mulai
            world = World(selectedKit)
            camX = world!!.player.pos.x
            camY = world!!.player.pos.y
            screen = Screen.PLAY
            SoundFX.start()
        }
    }
}
