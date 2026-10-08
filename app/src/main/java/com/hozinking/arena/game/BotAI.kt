package com.hozinking.arena.game

import kotlin.random.Random

/**
 * AI bot sederhana: farming minion, jaga jarak dari tower,
 * mundur saat sekarat, pakai skill saat ada target.
 */
object BotAI {

    fun think(bot: Hero, w: World, dt: Float) {
        if (!bot.alive) return
        bot.aiT -= dt
        if (bot.aiT > 0f) return
        bot.aiT = 0.25f

        val enemyBaseX = if (bot.team == Team.BLUE) World.RED_X else World.BLUE_X
        val laneY = World.LANES[bot.lane]

        // 1. Sekarat -> mundur ke base
        if (bot.hp < bot.maxHp * 0.32f) {
            val home = w.nexusOf(bot.team).pos
            bot.moveDir = (home - bot.pos).norm()
            bot.target = null
            return
        }

        val enemies = w.units.filter { it.team != bot.team && it.alive }
        val myMinions = w.units.filterIsInstance<Minion>().filter { it.team == bot.team && it.alive }

        // 2. Cek tower musuh terdekat di lane
        val enemyTower = enemies.filterIsInstance<Tower>()
            .filter { it.lane == bot.lane }
            .minByOrNull { it.pos.dist(bot.pos) }

        // 3. Jangan dive tower sendirian: mundur bila tower mengincar tanpa minion
        if (enemyTower != null) {
            val dTower = bot.pos.dist(enemyTower.pos)
            val minionsNearTower = myMinions.any { it.pos.dist(enemyTower.pos) < enemyTower.range }
            if (dTower < enemyTower.range + 60f && !minionsNearTower) {
                bot.moveDir = (bot.pos - enemyTower.pos).norm()
                bot.target = null
                return
            }
        }

        // 4. Pilih target: hero musuh bila sehat, else minion, else tower (bila ada minion), else jalan
        val enemyHero = enemies.filterIsInstance<Hero>()
            .minByOrNull { it.pos.dist(bot.pos) }
        var chosen: Unit? = null
        if (enemyHero != null && bot.pos.dist(enemyHero.pos) < 620f && bot.hp > bot.maxHp * 0.5f) {
            chosen = enemyHero
        } else {
            val minion = enemies.filterIsInstance<Minion>()
                .minByOrNull { it.pos.dist(bot.pos) }
            if (minion != null && bot.pos.dist(minion.pos) < 750f) {
                chosen = minion
            } else if (enemyTower != null) {
                val minionsNear = myMinions.any { it.pos.dist(enemyTower.pos) < enemyTower.range + 100f }
                if (minionsNear && bot.pos.dist(enemyTower.pos) < bot.range + 120f) {
                    chosen = enemyTower
                }
            }
        }
        bot.target = chosen

        // 5. Gerak: dekati target sampai dalam jarak, else dorong lane
        if (chosen != null) {
            val d = bot.pos.dist(chosen.pos)
            bot.moveDir = if (d > bot.range * 0.85f) {
                (chosen.pos - bot.pos).norm()
            } else {
                Vec2(0f, 0f)
            }
        } else {
            // Dorong lane ke base musuh, tapi jangan lewati tower depan sendirian
            val frontTower = enemies.filterIsInstance<Tower>()
                .filter { it.lane == bot.lane }
                .minByOrNull { it.pos.dist(bot.pos) }
            val hold = frontTower != null &&
                    bot.pos.dist(frontTower.pos) < 520f &&
                    myMinions.none { it.pos.dist(frontTower.pos) < frontTower.range }
            bot.moveDir = if (hold && frontTower != null) {
                // Tunggu minion: geser sedikit ke belakang
                (bot.pos - frontTower.pos).norm() * 0.4f
            } else {
                Vec2(enemyBaseX - bot.pos.x, laneY - bot.pos.y).norm()
            }
        }

        // 6. Pakai skill bila ada target dalam jarak
        val t = bot.target
        if (t != null) {
            val d = bot.pos.dist(t.pos)
            // S1/S2: pakai saat cooldown siap & target dalam jarak
            for (i in 0..1) {
                val sd = bot.kit.skills[i]
                if (bot.skillCd[i] <= 0f && d <= sd.range) {
                    w.castSkill(bot, i)
                    break
                }
            }
            // Ulti: target hero sekarat atau >=2 musuh dekat
            if (bot.skillCd[2] <= 0f) {
                val sd = bot.kit.skills[2]
                val useUlt = (t is Hero && t.hp < t.maxHp * 0.55f && d <= sd.range) ||
                        enemies.count { it.pos.dist(bot.pos) < 340f } >= 2
                if (useUlt && d <= sd.range) w.castSkill(bot, 2)
            }
        }

        // 7. Sedikit acak biar tidak kaku
        if (Random.nextFloat() < 0.06f) {
            bot.moveDir = bot.moveDir + Vec2(Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f) * 0.5f
        }
    }
}
