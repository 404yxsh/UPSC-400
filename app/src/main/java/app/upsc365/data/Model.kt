package app.upsc365.data

import android.content.Context
import app.upsc365.engine.Engine
import org.json.JSONArray
import org.json.JSONObject

data class SourceInfo(val id: String, val title: String, val verified: Boolean)

data class MathInfo(
    val topicId: String,
    val topic: String,
    val session: Int,
    val of: Int,
    val title: String,
    val type: String,
    val sources: List<String>,
    val prereq: List<Int>
)

data class GsInfo(
    val blockId: String,
    val block: String,
    val title: String,
    val kind: String,
    val tier: String,
    val primarySource: String,
    val primaryRef: String,
    val primaryLevel: String,
    val suppSource: String,
    val suppRef: String,
    val objective: String,
    val prereq: List<Int>
)

data class DayPlan(val day: Int, val math: MathInfo, val gs: GsInfo, val pyq: String)

data class BlockInfo(val id: String, val name: String, val startDay: Int, val endDay: Int)

class Programme(
    val days: List<DayPlan>,
    val sources: Map<String, SourceInfo>,
    val flexAfter: List<Int>,
    val gsBlocks: List<BlockInfo>,
    val mathWindows: List<BlockInfo>,
    val defaultStart: String,
    val spineDays: Int,
    val items: List<Engine.Item>
) {
    val byKey: Map<String, Engine.Item> = Engine.index(items)

    fun day(n: Int): DayPlan = days[n - 1]

    /** Plain title of the unit behind a key such as "d12:G". */
    fun titleOf(key: String): String {
        val plan = day(dayOfKey(key))
        return if (key.endsWith(":M")) plan.math.title else plan.gs.title
    }

    fun streamLabel(key: String): String = if (key.endsWith(":M")) "Math" else "GS"
}

fun dayOfKey(key: String): Int = key.substring(1, key.indexOf(':')).toInt()

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun JSONArray.strs(): List<String> = (0 until length()).map { getString(it) }

private fun parseBlocks(arr: JSONArray): List<BlockInfo> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    BlockInfo(o.getString("id"), o.getString("name"), o.getInt("startDay"), o.getInt("endDay"))
}

object ProgrammeLoader {
    fun load(context: Context): Programme {
        val text = context.assets.open("programme.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val meta = root.getJSONObject("meta")

        val sources = HashMap<String, SourceInfo>()
        val srcObj = root.getJSONObject("sources")
        val ids = srcObj.keys()
        while (ids.hasNext()) {
            val id = ids.next()
            val o = srcObj.getJSONObject(id)
            sources[id] = SourceInfo(id, o.getString("title"), o.optBoolean("verifiedMapping", false))
        }

        val days = ArrayList<DayPlan>()
        val items = ArrayList<Engine.Item>()
        val arr = root.getJSONArray("days")
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            val day = d.getInt("day")
            val m = d.getJSONObject("math")
            val g = d.getJSONObject("gs")
            val mp = MathInfo(
                m.getString("topicId"), m.getString("topic"), m.getInt("session"), m.getInt("of"),
                m.getString("title"), m.getString("type"), m.getJSONArray("sources").strs(),
                m.getJSONArray("prereq").ints()
            )
            val primary = g.getJSONObject("primary")
            val supp = g.getJSONObject("supplementary")
            val gp = GsInfo(
                g.getString("blockId"), g.getString("block"), g.getString("title"), g.getString("kind"),
                g.getString("tier"), primary.getString("source"), primary.getString("ref"),
                primary.optString("refLevel", "topic"), supp.getString("source"), supp.getString("ref"),
                g.getString("objective"), g.getJSONArray("prereq").ints()
            )
            days.add(DayPlan(day, mp, gp, d.getJSONObject("revision").getString("pyq")))
            items.add(Engine.Item(day, Engine.Stream.MATH, Engine.Tier.CORE, mp.prereq))
            items.add(Engine.Item(day, Engine.Stream.GS, Engine.Tier.valueOf(gp.tier.uppercase()), gp.prereq))
        }

        return Programme(
            days = days,
            sources = sources,
            flexAfter = meta.getJSONArray("flexAfterSpineDays").ints(),
            gsBlocks = parseBlocks(meta.getJSONArray("gsBlocks")),
            mathWindows = parseBlocks(meta.getJSONArray("mathWindows")),
            defaultStart = meta.getString("startDate"),
            spineDays = meta.getInt("spineDays"),
            items = items
        )
    }
}
