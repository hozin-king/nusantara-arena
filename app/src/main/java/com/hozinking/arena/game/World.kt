package com.hozinking.arena.game

import kotlin.math.min
import kotlin.random.Random

/** Efek visual sementara (ring, ledakan) — digambar renderer. */
data class Fx(
    var pos: Vec2, val r0: Float, val r1: Float,
    var life: Float, val maxLife: Float, val color: Int
)

/**
 * Dunia permainan: unit, proyektil, wave minion, combat, skill, menang/kalah.
 * Full offline — semua simulasi lokal.
 */
class World(val playerKit: HeroKit) {

    companion object {
        const val W = 3000f
        const val H = 3000f
        val LANES = floatArrayOf(600f, 1500f, 2400f)
        const val BLUE_X = 250f
        const val RED_X = 2750f
        const val WAVE_INTERVAL = 18f
    }

    val units = mutableListOf<Unit>()
    val projectiles = mutableListOf<Projectile>()
    val effects = mutableListOf<Fx>()
    val heroes = mutableListOf<Hero>()
    lateinit var player: Hero
    lateinit var nexusBlue: Nexus
    lateinit var nexusRed: Nexus
    var time = 0f
    var waveT = 3f
    var winner: Team? = null
    var blueKills = 0
    var redKills = 0

    init {
        // Nexus
        nexusBlue = Nexus(Vec2(BLUE_X, 1500f), Team.BLUE)
        nexusRed = Nexus(Vec2(RED_X, 1500f), Team.RED)
        units += nexusBlue
        units += nexusRed
        // Tower: 2 per lane per sisi, simetris
        for ((li, y) in LANES.withIndex()) {
            units += Tower(Vec2(800f, y), Team.BLUE, li)
            units += Tower(Vec2(1400f, y), Team.BLUE, li)
            units += Tower(Vec2(2200f, y), Team.RED, li)
            units += Tower(Vec2(1600f, y), Team.RED, li)
        }
        // Hero: pemain + 4 bot sekutu vs 5 bot musuh
        val kits = Kits.ALL
        fun mkKit(i: Int) = kits[i % kits.size]
        player = Hero(Vec2(BLUE_X + 150f, 1500f), Team.BLUE, playerKit, "Kamu", true)
        player.lane = 1
        heroes += player; units += player
        val allyLanes = intArrayOf(0, 1, 2, 1)
        val allyNames = arrayOf("Bot-A1", "Bot-A2", "Bot-A3", "Bot-A4")
        for (i in 0 until 4) {
            val h = Hero(Vec2(BLUE_X + 150f, LANES[allyLanes[i]]), Team.BLUE, mkKit(i + 1), allyNames[i], false)
            h.lane = allyLanes[i]
            heroes += h; units += h
        }
        val enemyLanes = intArrayOf(0, 0, 1, 2, 2)
        val enemyNames = arrayOf("Musuh-1", "Musuh-2", "Musuh-3", "Musuh-4", "Musuh-5")
        for (i in 0 until 5) {
            val h = Hero(Vec2(RED_X - 150f, LANES[enemyLanes[i]]), Team.RED, mkKit(i), enemyNames[i], false)
            h.lane = enemyLanes[i]
            heroes += h; units += h
        }
    }

    fun nexusOf(team: Team): Nexus =
        units.filterIsInstance<Nexus>().first { it.team == team }

    fun towersAlive(team: Team): Int =
        units.filterIsInstance<Tower>().count { it.team == team && it.alive }

    // ---------------- update ----------------

    fun update(dt: Float) {
        if (winner != null) return
        time += dt
        waveT -= dt
        if (waveT <= 0f) {
            waveT = WAVE_INTERVAL
            spawnWave()
        }
        // Efek visual
        val ei = effects.iterator()
        while (ei.hasNext()) {
            val e = ei.next()
            e.life -= dt
            if (e.life <= 0f) ei.remove()
        }
        // Hero mati -> respawn; heal di base
        val nexusB = nexusOf(Team.BLUE); val nexusR = nexusOf(Team.RED)
        for (h in heroes) {
            if (!h.alive) {
                h.respawnT -= dt
                if (h.respawnT <= 0f) {
                    val n = if (h.team == Team.BLUE) nexusB else nexusR
                    h.hp = h.maxHp
                    h.pos = Vec2(n.pos.x + Random.nextFloat() * 120f - 60f, n.pos.y + Random.nextFloat() * 120f - 60f)
                    h.skillCd[0] = 0f; h.skillCd[1] = 0f; h.skillCd[2] = 0f
                }
                continue
            }
            for (i in 0..2) if (h.skillCd[i] > 0f) h.skillCd[i] -= dt
            if (h.slowT > 0f) h.slowT -= dt
            // XP pasif + heal di base
            h.xpAcc += 5f * dt
            if (h.xpAcc >= 1f) {
                h.gainXp(h.xpAcc.toInt())
                h.xpAcc -= h.xpAcc.toInt()
            }
            val home = if (h.team == Team.BLUE) nexusB else nexusR
            if (h.pos.dist(home.pos) < 320f) h.hp = min(h.maxHp, h.hp + h.maxHp * 0.06f * dt)
            // AI bot
            if (!h.isPlayer) BotAI.think(h, this, dt)
        }
        // Unit: target & serang & gerak
        for (u in units) {
            if (!u.alive) continue
            if (u.attackCd > 0f) u.attackCd -= dt
            when (u) {
                is Hero -> updateHero(u, dt)
                is Minion -> updateMinion(u, dt)
                is Tower -> updateTower(u, dt)
                is Nexus -> { /* diam */ }
            }
        }
        // Proyektil
        val pi = projectiles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dt
            p.pos = p.pos + p.vel * dt
            var dead = p.life <= 0f || p.pos.x < 0 || p.pos.x > W || p.pos.y < 0 || p.pos.y > H
            if (!dead) {
                for (u in units) {
                    if (!u.alive || u.team == p.team) continue
                    if (u.pos.dist(p.pos) < u.radius + p.radius) {
                        if (p.aoe > 0f) {
                            burst(p.pos, p.aoe, p.dmg, p.team, p.fromHero, p.slow)
                        } else {
                            damage(u, p.dmg, p.fromHero)
                            if (p.slow > 0f && u is Hero) u.slowT = 2f
                        }
                        if (!p.pierce) { dead = true; break }
                    }
                }
            }
            if (dead) pi.remove()
        }
        // Bersihkan yang mati (hero mati dipertahankan untuk respawn)
        val ui = units.iterator()
        while (ui.hasNext()) {
            val u = ui.next()
            if (!u.alive && u !is Hero) ui.remove()
        }
        // Cek menang/kalah (pakai referensi langsung — nexus mati sudah keluar dari units)
        if (!nexusBlue.alive) winner = Team.RED
        else if (!nexusRed.alive) winner = Team.BLUE
    }

    private fun spawnWave() {
        for ((li, y) in LANES.withIndex()) {
            for (team in Team.values()) {
                val bx = if (team == Team.BLUE) BLUE_X + 120f else RED_X - 120f
                repeat(2) {
                    units += Minion(Vec2(bx + Random.nextFloat() * 60f - 30f, y + Random.nextFloat() * 80f - 40f), team, li, false)
                }
                units += Minion(Vec2(bx, y + 90f), team, li, true)
            }
        }
    }

    private fun nearestEnemy(u: Unit, range: Float, onlyMinions: Boolean = false): Unit? {
        var best: Unit? = null
        var bd = range
        for (o in units) {
            if (!o.alive || o.team == u.team) continue
            if (onlyMinions && o !is Minion) continue
            val d = u.pos.dist(o.pos)
            if (d < bd) { bd = d; best = o }
        }
        return best
    }

    private fun updateHero(h: Hero, dt: Float) {
        // Target: musuh terdekat dalam aggro
        if (h.target == null || !h.target!!.alive || h.pos.dist(h.target!!.pos) > 800f) {
            h.target = nearestEnemy(h, 700f)
        }
        val t = h.target
        val spd = h.speed * (if (h.slowT > 0f) 0.5f else 1f)
        if (h.isPlayer) {
            // Gerak dari joystick
            if (h.moveDir.len() > 0.1f) {
                h.faceDir = h.moveDir.norm()
                h.pos = h.pos + h.moveDir.norm() * spd * dt
            }
        } else {
            if (h.moveDir.len() > 0.1f) {
                h.faceDir = h.moveDir.norm()
                h.pos = h.pos + h.moveDir.norm() * spd * dt
            }
        }
        clampPos(h)
        // Serang bila dalam jarak
        if (t != null && h.attackCd <= 0f && h.pos.dist(t.pos) <= h.range + t.radius) {
            h.attackCd = 0.85f
            h.faceDir = (t.pos - h.pos).norm()
            if (h.kit.melee) {
                damage(t, h.atk, h)
                effects += Fx(t.pos.copy(), 10f, 40f, 0.18f, 0.18f, 0xFFFFFFFF.toInt())
            } else {
                val dir = (t.pos - h.pos).norm()
                projectiles += Projectile(h.pos.copy(), dir * 950f, h.atk, h.team, 1.2f, 8f, h, 0f, 0f, h.kit.color)
            }
        }
    }

    private fun updateMinion(m: Minion, dt: Float) {
        if (m.target == null || !m.target!!.alive) {
            m.target = nearestEnemy(m, 560f)
        }
        val t = m.target
        if (t != null && m.pos.dist(t.pos) <= m.range + t.radius) {
            if (m.attackCd <= 0f) {
                m.attackCd = 1.1f
                if (m.ranged) {
                    val dir = (t.pos - m.pos).norm()
                    projectiles += Projectile(m.pos.copy(), dir * 700f, m.atk, m.team, 1.2f, 7f, null, 0f, 0f, 0xFFFFEB3B.toInt())
                } else {
                    damage(t, m.atk, null)
                }
            }
        } else if (t != null) {
            // Kejar target yang di luar jangkauan
            val dir = (t.pos - m.pos).norm()
            m.pos = m.pos + dir * m.speed * dt
            clampPos(m)
        } else {
            // Jalan di lane menuju base musuh
            val goalX = if (m.team == Team.BLUE) RED_X else BLUE_X
            val dir = Vec2(goalX - m.pos.x, LANES[m.lane] - m.pos.y).norm()
            m.pos = m.pos + dir * m.speed * dt
            clampPos(m)
        }
    }

    private fun updateTower(tw: Tower, dt: Float) {
        // Prioritas: minion dulu, baru hero
        var t = nearestEnemy(tw, tw.range, onlyMinions = true)
        if (t == null) t = nearestEnemy(tw, tw.range)
        tw.target = t
        if (t != null && tw.attackCd <= 0f) {
            tw.attackCd = 1.0f
            val dir = (t.pos - tw.pos).norm()
            val dmg = if (t is Hero) tw.atk * 1.35f else tw.atk
            projectiles += Projectile(tw.pos.copy(), dir * 750f, dmg, tw.team, 1.4f, 10f, null, 0f, 0f, tw.team.color())
        }
    }

    private fun clampPos(u: Unit) {
        u.pos.x = u.pos.x.coerceIn(40f, W - 40f)
        u.pos.y = u.pos.y.coerceIn(40f, H - 40f)
    }

    // ---------------- damage & kill ----------------

    fun damage(target: Unit, amount: Float, from: Hero?) {
        if (!target.alive || winner != null) return
        target.hp -= amount
        if (target.hp <= 0f) {
            target.hp = 0f
            onKill(target, from)
        }
    }

    private fun burst(at: Vec2, radius: Float, dmg: Float, team: Team, from: Hero?, slow: Float) {
        effects += Fx(at.copy(), 20f, radius, 0.3f, 0.3f, 0xFFFF9800.toInt())
        for (u in units) {
            if (!u.alive || u.team == team) continue
            if (u.pos.dist(at) <= radius + u.radius) {
                damage(u, dmg, from)
                if (slow > 0f && u is Hero) u.slowT = 2f
            }
        }
    }

    private fun onKill(dead: Unit, from: Hero?) {
        // XP untuk hero satu tim yang dekat
        val xpReward = when (dead) {
            is Hero -> 130
            is Tower -> 110
            is Minion -> 26
            else -> 60
        }
        for (h in heroes) {
            if (h.team == dead.team.enemy() && h.alive && h.pos.dist(dead.pos) < 650f) {
                h.gainXp(xpReward)
            }
        }
        if (dead is Hero) {
            dead.respawnT = 8f
            dead.moveDir = Vec2(0f, 0f)
            dead.target = null
            effects += Fx(dead.pos.copy(), 20f, 120f, 0.5f, 0.5f, 0xFFFF0000.toInt())
            if (dead.team == Team.RED) blueKills++ else redKills++
        }
    }

    // ---------------- skill ----------------

    /** Pemain/AI memanggil ini. Return true bila skill keluar. */
    fun castSkill(hero: Hero, idx: Int): Boolean {
        if (!hero.alive || winner != null) return false
        if (idx !in 0..2 || hero.skillCd[idx] > 0f) return false
        val def = hero.kit.skills[idx]
        // Cari target dalam jarak skill
        val tgt = nearestEnemy(hero, def.range) ?: return false
        hero.faceDir = (tgt.pos - hero.pos).norm()
        hero.skillCd[idx] = def.cooldown
        val dir = hero.faceDir
        when (hero.kit.id) {
            "bara" -> when (idx) {
                0 -> { // Tebasan Api: cone depan
                    burst(hero.pos + dir * 140f, 200f, hero.atk * 1.7f, hero.team, hero, 0f)
                }
                1 -> { // Dash Membara + heal
                    hero.pos = hero.pos + dir * 300f
                    clampPos(hero)
                    hero.hp = min(hero.maxHp, hero.hp + 140f + hero.level * 20f)
                    effects += Fx(hero.pos.copy(), 20f, 130f, 0.3f, 0.3f, 0xFFFF6F00.toInt())
                }
                2 -> { // Pusaran Inferno
                    burst(hero.pos.copy(), 330f, hero.atk * 2.6f, hero.team, hero, 0f)
                }
            }
            "wulan" -> when (idx) {
                0 -> { // Panah Penetrasi
                    projectiles += Projectile(hero.pos.copy(), dir * 1100f, hero.atk * 1.9f, hero.team, 1.0f, 9f, hero, 70f, 0f, 0xFF7C4DFF.toInt()).also { it.pierce = true }
                }
                1 -> { // Kipas 5 panah
                    for (k in -2..2) {
                        val a = Math.toRadians((k * 12).toDouble()).toFloat()
                        val d = Vec2(
                            dir.x * kotlin.math.cos(a) - dir.y * kotlin.math.sin(a),
                            dir.x * kotlin.math.sin(a) + dir.y * kotlin.math.cos(a)
                        )
                        projectiles += Projectile(hero.pos.copy(), d * 1000f, hero.atk * 0.95f, hero.team, 0.9f, 8f, hero, 0f, 0f, 0xFFB388FF.toInt())
                    }
                }
                2 -> { // Gerhana: hujan panah di posisi target
                    burst(tgt.pos.copy(), 280f, hero.atk * 3.1f, hero.team, hero, 0f)
                    effects += Fx(tgt.pos.copy(), 40f, 280f, 0.5f, 0.5f, 0xFF7C4DFF.toInt())
                }
            }
            "bayu" -> when (idx) {
                0 -> { // Bolt Angin
                    projectiles += Projectile(hero.pos.copy(), dir * 1150f, hero.atk * 1.6f, hero.team, 1.0f, 9f, hero, 0f, 0f, 0xFF00E5FF.toInt())
                }
                1 -> { // Pusaran Topan: slow + damage
                    burst(tgt.pos.copy(), 210f, hero.atk * 1.3f, hero.team, hero, 0.5f)
                }
                2 -> { // Badai Nusantara
                    burst(tgt.pos.copy(), 330f, hero.atk * 2.3f, hero.team, hero, 0.5f)
                    effects += Fx(tgt.pos.copy(), 50f, 330f, 0.6f, 0.6f, 0xFF00E5FF.toInt())
                }
            }
        }
        return true
    }
}
