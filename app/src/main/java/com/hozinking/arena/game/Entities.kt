package com.hozinking.arena.game

enum class Team { BLUE, RED }

fun Team.enemy() = if (this == Team.BLUE) Team.RED else Team.BLUE

fun Team.color(): Int = if (this == Team.BLUE) 0xFF4FC3F7.toInt() else 0xFFEF5350.toInt()
fun Team.darkColor(): Int = if (this == Team.BLUE) 0xFF0277BD.toInt() else 0xFFB71C1C.toInt()

/** Basis semua unit yang bisa diserang. */
open class Unit(
    var pos: Vec2,
    var hp: Float,
    var maxHp: Float,
    var atk: Float,
    val range: Float,
    val speed: Float,
    val team: Team,
    val radius: Float
) {
    var attackCd: Float = 0f
    var target: Unit? = null
    val alive get() = hp > 0f
    fun hpFrac() = (hp / maxHp).coerceIn(0f, 1f)
}

class Minion(
    pos: Vec2, team: Team, val lane: Int, val ranged: Boolean
) : Unit(
    pos = pos,
    hp = if (ranged) 200f else 340f,
    maxHp = if (ranged) 200f else 340f,
    atk = if (ranged) 32f else 26f,
    range = if (ranged) 280f else 70f,
    speed = 130f,
    team = team,
    radius = if (ranged) 14f else 16f
)

class Tower(
    pos: Vec2, team: Team, val lane: Int
) : Unit(
    pos = pos,
    hp = 1300f, maxHp = 1300f,
    atk = 95f, range = 430f, speed = 0f,
    team = team, radius = 34f
)

class Nexus(
    pos: Vec2, team: Team
) : Unit(
    pos = pos,
    hp = 1600f, maxHp = 1600f,
    atk = 0f, range = 0f, speed = 0f,
    team = team, radius = 52f
)

class Hero(
    pos: Vec2, team: Team,
    val kit: HeroKit,
    val name: String,
    val isPlayer: Boolean
) : Unit(
    pos = pos,
    hp = kit.hp, maxHp = kit.hp,
    atk = kit.atk, range = kit.range, speed = kit.speed,
    team = team, radius = 22f
) {
    var level = 1
    var xp = 0
    var xpAcc = 0f
    var xpNext = 100
    var respawnT = 0f
    var moveDir = Vec2(0f, 0f)   // diisi joystick / AI
    var faceDir = Vec2(1f, 0f)
    val skillCd = FloatArray(3) { 0f }
    var lane = 1
    var aiT = 0f                 // timer berpikir AI
    var slowT = 0f               // kena slow

    fun gainXp(amount: Int) {
        if (!alive) return
        xp += amount
        while (xp >= xpNext && level < 12) {
            xp -= xpNext
            level++
            xpNext = (xpNext * 1.35f).toInt()
            atk *= 1.09f
            maxHp *= 1.12f
            hp = (hp + maxHp * 0.12f).coerceAtMost(maxHp)
        }
    }
}

/** Proyektil: panah, bolt sihir, dsb. */
class Projectile(
    var pos: Vec2,
    var vel: Vec2,
    var dmg: Float,
    val team: Team,
    var life: Float,
    val radius: Float = 8f,
    val fromHero: Hero? = null,
    val aoe: Float = 0f,
    val slow: Float = 0f,
    val color: Int = 0xFFFFFF00.toInt()
) {
    var pierce: Boolean = false
}
