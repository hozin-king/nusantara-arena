package com.hozinking.arena.game

/**
 * Kit hero orisinal Nusantara Arena.
 * 3 hero: Bara (warrior api), Wulan (pemanah bulan), Bayu (penyihir angin).
 */
data class SkillDef(
    val name: String,
    val desc: String,
    val cooldown: Float,
    val range: Float
)

data class HeroKit(
    val id: String,
    val name: String,
    val title: String,
    val color: Int,
    val hp: Float,
    val atk: Float,
    val range: Float,   // jarak serang dasar
    val speed: Float,
    val melee: Boolean,
    val skills: List<SkillDef>
)

object Kits {
    val BARA = HeroKit(
        id = "bara", name = "Bara", title = "Pendekar Api",
        color = 0xFFFF6F00.toInt(), hp = 1250f, atk = 78f,
        range = 90f, speed = 235f, melee = true,
        skills = listOf(
            SkillDef("Tebasan Api", "Tebasan area di depan", 6f, 220f),
            SkillDef("Dash Membara", "Dash + tameng 3 dtk", 11f, 320f),
            SkillDef("Pusaran Inferno", "ULTI: pusaran api AoE besar", 42f, 300f)
        )
    )
    val WULAN = HeroKit(
        id = "wulan", name = "Wulan", title = "Pemanah Bulan",
        color = 0xFF7C4DFF.toInt(), hp = 880f, atk = 92f,
        range = 380f, speed = 245f, melee = false,
        skills = listOf(
            SkillDef("Panah Penetrasi", "Panah lurus menembus", 6f, 560f),
            SkillDef("Hujan Anak Panah", "Kipas 5 panah", 10f, 480f),
            SkillDef("Gerhana", "ULTI: hujan panah area", 45f, 520f)
        )
    )
    val BAYU = HeroKit(
        id = "bayu", name = "Bayu", title = "Penyihir Angin",
        color = 0xFF00E5FF.toInt(), hp = 940f, atk = 85f,
        range = 360f, speed = 240f, melee = false,
        skills = listOf(
            SkillDef("Bolt Angin", "Proyektil cepat", 5f, 560f),
            SkillDef("Pusaran Topan", "Cyclone: slow + damage area", 12f, 420f),
            SkillDef("Badai Nusantara", "ULTI: badai DoT area besar", 48f, 480f)
        )
    )
    val ALL = listOf(BARA, WULAN, BAYU)
}
