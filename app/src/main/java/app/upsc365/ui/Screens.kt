package app.upsc365.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.upsc365.data.DayPlan
import app.upsc365.data.MathInfo
import app.upsc365.data.dayOfKey
import app.upsc365.engine.Engine
import app.upsc365.vm.MainViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TAB_NAMES = listOf("Today", "Plan", "Syllabus", "Notes", "Settings")

// ---------------------------------------------------------------- root

@Composable
fun Upsc365Root(vm: MainViewModel) {
    val s = vm.state
    val dark = when (s.theme) {
        "light" -> false
        "system" -> androidx.compose.foundation.isSystemInDarkTheme()
        else -> true
    }
    AppTheme(dark) {
        var tab by rememberSaveable { mutableStateOf(0) }
        var detail by rememberSaveable { mutableStateOf(0) }
        var showQueue by rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = detail != 0 || showQueue) {
            if (detail != 0) detail = 0 else showQueue = false
        }

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                if (detail != 0) {
                    DayDetailScreen(vm, detail) { detail = 0 }
                } else if (showQueue) {
                    QueueScreen(vm, { showQueue = false }, { d -> detail = d })
                } else {
                    when (tab) {
                        0 -> TodayScreen(vm, { d -> detail = d }, { showQueue = true })
                        1 -> PlanScreen(vm) { d -> detail = d }
                        2 -> SyllabusScreen(vm) { d -> detail = d }
                        3 -> NotesScreen(vm) { d -> detail = d }
                        else -> SettingsScreen(vm)
                    }
                }
            }
            BottomBar(tab) { i -> tab = i; detail = 0; showQueue = false }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().background(p.surface)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth()) {
            TAB_NAMES.forEachIndexed { i, name ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(i) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(
                        Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .background(if (i == selected) p.accent else Color.Transparent)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        name, fontSize = 12.sp,
                        color = if (i == selected) p.text else p.dim,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared helpers

private fun mathObjective(m: MathInfo): String = when (m.type) {
    "learn" -> "Understand and be able to prove or solve: ${m.title}"
    "problems" -> "Solve timed problems and review every error"
    "consolidation" -> "Solve past-paper questions on this topic and log errors"
    else -> "Sit the paper under timed conditions and analyse mistakes"
}

private fun tierColor(tier: String, p: Pal): Color = when (tier) {
    "core" -> p.accent
    "extended" -> p.warn
    else -> p.dim
}

private fun credit(s: Engine.Status?): Double = when (s) {
    Engine.Status.DONE -> 1.0
    Engine.Status.SKIMMED -> 0.5
    else -> 0.0
}

private fun srcName(vm: MainViewModel, id: String): String =
    if (id == "-") "All sources" else (vm.programme.sources[id]?.title ?: id)

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
private fun MathUnitCard(vm: MainViewModel, plan: DayPlan, open: (Int) -> Unit) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val key = Engine.Item.key(plan.day, Engine.Stream.MATH)
    val m = plan.math
    val blockedBy = Engine.firstPendingPrereq(prog.byKey.getValue(key), prog.byKey, s.status)
    val onBlocked: (() -> Unit)? = if (blockedBy != null) {
        { open(blockedBy.day) }
    } else null
    UnitCard(
        label = "Mathematics · 2.5 h",
        title = m.title,
        meta = "${m.topic} · session ${m.session}/${m.of}",
        chips = listOf(Pair(m.type, p.accent)),
        lines = m.sources.mapIndexed { i, id ->
            Pair(if (i == 0) "Primary" else "Also", prog.sources[id]?.title ?: id)
        },
        objective = mathObjective(m),
        status = s.status[key] ?: Engine.Status.PENDING,
        blocked = if (blockedBy != null) "Finish Day ${blockedBy.day} first: ${prog.day(blockedBy.day).math.title}" else null,
        onOpenBlocked = onBlocked,
        onDone = { vm.setStatus(key, Engine.Status.DONE) },
        onSkim = { vm.setStatus(key, Engine.Status.SKIMMED) },
        onUndo = { vm.setStatus(key, Engine.Status.PENDING) },
        onOpen = { open(plan.day) }
    )
}

@Composable
private fun GsUnitCard(vm: MainViewModel, plan: DayPlan, open: (Int) -> Unit) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val key = Engine.Item.key(plan.day, Engine.Stream.GS)
    val g = plan.gs
    val blockedBy = Engine.firstPendingPrereq(prog.byKey.getValue(key), prog.byKey, s.status)
    val onBlocked: (() -> Unit)? = if (blockedBy != null) {
        { open(blockedBy.day) }
    } else null
    val myRef = vm.field(key, "ref")
    val lecture = vm.field(key, "lecture")
    val lines = ArrayList<Pair<String, String>>()
    lines.add(Pair("Primary", srcName(vm, g.primarySource) + " — " + (if (myRef.isNotEmpty()) myRef else g.primaryRef)))
    if (g.suppSource != "-") lines.add(Pair("Also", srcName(vm, g.suppSource) + " — " + g.suppRef))
    if (lecture.isNotEmpty()) lines.add(Pair("Lecture", lecture))
    val chips = ArrayList<Pair<String, Color>>()
    chips.add(Pair(g.tier, tierColor(g.tier, p)))
    if (g.kind != "learn") chips.add(Pair(g.kind, p.good))
    UnitCard(
        label = "GS · 4.75 h · ${g.block}",
        title = g.title,
        meta = "",
        chips = chips,
        lines = lines,
        objective = g.objective,
        status = s.status[key] ?: Engine.Status.PENDING,
        blocked = if (blockedBy != null) "Finish Day ${blockedBy.day} first: ${prog.day(blockedBy.day).gs.title}" else null,
        onOpenBlocked = onBlocked,
        onDone = { vm.setStatus(key, Engine.Status.DONE) },
        onSkim = { vm.setStatus(key, Engine.Status.SKIMMED) },
        onUndo = { vm.setStatus(key, Engine.Status.PENDING) },
        onOpen = { open(plan.day) }
    )
}

@Composable
private fun RevisionCard(vm: MainViewModel, pyq: String?) {
    val s = vm.state
    val p = LocalPal.current
    Panel {
        Label("Revision and PYQ · 0.75 h")
        Spacer(Modifier.height(8.dp))
        if (s.reviews.shown.isEmpty()) {
            Text("No spaced reviews due.", color = p.dim, fontSize = 14.sp)
        }
        s.reviews.shown.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tick(done = false, size = 24.dp) { vm.markReview(r) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "${vm.programme.streamLabel(r.key)} · ${vm.programme.titleOf(r.key)}",
                        color = p.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text("Review +${r.interval} days", color = p.dim, fontSize = 12.sp)
                }
            }
        }
        if (s.reviews.extra > 0) {
            Text("${s.reviews.extra} more reviews waiting", color = p.dim, fontSize = 12.sp)
        }
        if (s.reviews.lapsed > 0) {
            Text("${s.reviews.lapsed} reviews lapsed (overdue by more than 10 days)", color = p.dim, fontSize = 12.sp)
        }
        if (pyq != null) {
            Spacer(Modifier.height(8.dp))
            Text("PYQ focus: $pyq", color = p.text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RecoveryRow(vm: MainViewModel, e: Engine.QueueEntry, open: (Int) -> Unit) {
    val p = LocalPal.current
    val prog = vm.programme
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open(e.item.day) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tick(done = false, size = 26.dp) { vm.setStatus(e.item.key(), if (e.skim) Engine.Status.SKIMMED else Engine.Status.DONE) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${prog.streamLabel(e.item.key())} · Day ${e.item.day} · ${prog.titleOf(e.item.key())}",
                color = p.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                "Overdue ${e.ageDays}d · ${e.item.tier.name.lowercase()} · ${e.cost} h" + (if (e.skim) " · skim" else ""),
                color = if (e.skim) p.warn else p.dim, fontSize = 12.sp
            )
        }
    }
}

// ---------------------------------------------------------------- Today

@Composable
fun TodayScreen(vm: MainViewModel, openDay: (Int) -> Unit, openQueue: () -> Unit) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val cal = s.calendar.firstOrNull { it.date == s.today }
    val first = s.calendar.first().date
    val last = s.calendar.last().date

    val subtitle: String = if (s.today.isBefore(first)) {
        "Starts ${first.format(FULL_DATE)}, in ${ChronoUnit.DAYS.between(s.today, first)} days"
    } else if (s.today.isAfter(last)) {
        "Programme complete. Move on to the revision phase."
    } else if (cal == null) {
        ""
    } else if (cal.type == Engine.DayType.SPINE) {
        "Day ${cal.spineDay} of ${prog.spineDays} · ${prog.day(cal.spineDay).gs.block}"
    } else if (cal.type == Engine.DayType.SUNDAY) {
        "Sunday · recovery and consolidation"
    } else {
        "Flex day · catch up or rest"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Label("Today")
                Text(s.today.format(FULL_DATE), color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = p.dim, fontSize = 14.sp)
            }
        }

        if (s.queue.isNotEmpty()) {
            item {
                val color = when (s.level) {
                    Engine.Level.RED -> p.bad
                    Engine.Level.YELLOW -> p.warn
                    else -> p.dim
                }
                Panel(onClick = openQueue, borderColor = color) {
                    Label("Recovery queue")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${s.queue.size} items · ${"%.1f".format(s.backlog)} h overdue",
                        color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("Tap to see what to clear and when.", color = p.dim, fontSize = 13.sp)
                }
            }
        }

        if (cal != null && cal.type == Engine.DayType.SPINE) {
            val plan = prog.day(cal.spineDay)
            item {
                Panel {
                    Label("Evening plan")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "19:00 Mathematics → 22:00 GS-A → 00:45 GS-B → 03:00 Revision → 03:45 close day",
                        color = p.text, fontSize = 13.sp
                    )
                }
            }
            item { MathUnitCard(vm, plan, openDay) }
            item { GsUnitCard(vm, plan, openDay) }
            item { RevisionCard(vm, plan.pyq) }
        } else if (cal != null) {
            val cap = if (cal.type == Engine.DayType.SUNDAY) Engine.SUNDAY_CAP else Engine.FLEX_CAP
            val pack = Engine.pack(s.queue, cap)
            if (s.queue.isEmpty()) {
                item {
                    Panel {
                        Label(if (cal.type == Engine.DayType.SUNDAY) "Consolidation Sunday" else "Free flex day")
                        Spacer(Modifier.height(6.dp))
                        if (cal.type == Engine.DayType.SUNDAY) {
                            Text("• Weekly review: re-read this week's one-page summaries", color = p.text, fontSize = 14.sp)
                            Text("• Timed Mathematics set (2.5 h): mixed problems from earlier topics", color = p.text, fontSize = 14.sp)
                            Text("• Context capsule (60 min): key static-linked developments only", color = p.text, fontSize = 14.sp)
                            Text("• Skim next week's topics", color = p.text, fontSize = 14.sp)
                        } else {
                            Text("Nothing is overdue. Rest, or add extra Mathematics practice.", color = p.text, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                item {
                    Panel {
                        Label("Recovery plan · ${"%.2f".format(pack.hoursUsed)} of ${cap} h")
                        Spacer(Modifier.height(6.dp))
                        pack.picked.forEach { e -> RecoveryRow(vm, e, openDay) }
                        if (pack.leftover.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${pack.leftover.size} items carry over to the next recovery day.",
                                color = p.dim, fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            item { RevisionCard(vm, null) }
        }
    }
}

// ---------------------------------------------------------------- Plan

private sealed interface PlanRow {
    data class Header(val text: String) : PlanRow
    data class Entry(val cal: Engine.CalDay) : PlanRow
}

private fun buildRows(cal: List<Engine.CalDay>): List<PlanRow> {
    val out = ArrayList<PlanRow>()
    val f = DateTimeFormatter.ofPattern("MMMM yyyy")
    var lastMonth = ""
    for (c in cal) {
        val m = c.date.format(f)
        if (m != lastMonth) {
            out.add(PlanRow.Header(m))
            lastMonth = m
        }
        out.add(PlanRow.Entry(c))
    }
    return out
}

@Composable
fun PlanScreen(vm: MainViewModel, openDay: (Int) -> Unit) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val rows = remember(s.calendar) { buildRows(s.calendar) }
    val todayIdx = rows.indexOfFirst { it is PlanRow.Entry && it.cal.date == s.today }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (todayIdx > 0) listState.scrollToItem(todayIdx)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Label("Plan")
                Text("365 days", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { if (todayIdx >= 0) scope.launch { listState.animateScrollToItem(todayIdx) } }) {
                Text("Jump to today", color = p.accent)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows) { row ->
                when (row) {
                    is PlanRow.Header -> {
                        Spacer(Modifier.height(6.dp))
                        Label(row.text)
                    }
                    is PlanRow.Entry -> PlanEntry(vm, row.cal, row.cal.date == s.today, openDay)
                }
            }
        }
    }
}

@Composable
private fun PlanEntry(vm: MainViewModel, c: Engine.CalDay, isToday: Boolean, open: (Int) -> Unit) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val dateFmt = DateTimeFormatter.ofPattern("d MMM · EEE")
    val onClick: (() -> Unit)? = if (c.type == Engine.DayType.SPINE) {
        { open(c.spineDay) }
    } else null
    Panel(onClick = onClick, borderColor = if (isToday) p.accent else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.date.format(dateFmt), color = p.dim, fontSize = 12.sp)
                if (c.type == Engine.DayType.SPINE) {
                    val plan = prog.day(c.spineDay)
                    Text(
                        "Day ${c.spineDay} · ${plan.gs.block}", color = p.dim, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Math: ${plan.math.title}", color = p.text, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "GS: ${plan.gs.title}", color = p.text, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                } else if (c.type == Engine.DayType.SUNDAY) {
                    Text("Sunday · recovery and consolidation", color = p.text, fontSize = 14.sp)
                } else {
                    Text("Flex day · catch up or rest", color = p.text, fontSize = 14.sp)
                }
            }
            if (c.type == Engine.DayType.SPINE) {
                val mDone = (s.status[Engine.Item.key(c.spineDay, Engine.Stream.MATH)] ?: Engine.Status.PENDING) != Engine.Status.PENDING
                val gDone = (s.status[Engine.Item.key(c.spineDay, Engine.Stream.GS)] ?: Engine.Status.PENDING) != Engine.Status.PENDING
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(mDone)
                    Dot(gDone)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Syllabus

@Composable
fun SyllabusScreen(vm: MainViewModel, openDay: (Int) -> Unit) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    fun unitCredit(day: Int, stream: Engine.Stream): Double = credit(s.status[Engine.Item.key(day, stream)])

    var mathTotal = 0.0
    var gsTotal = 0.0
    for (d in 1..prog.spineDays) {
        mathTotal += unitCredit(d, Engine.Stream.MATH)
        gsTotal += unitCredit(d, Engine.Stream.GS)
    }
    val overall = (mathTotal + gsTotal) / (2.0 * prog.spineDays)
    val hoursDone = mathTotal * Engine.MATH_HOURS + gsTotal * Engine.GS_HOURS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Label("Syllabus")
                Text("${(overall * 100).toInt()}% complete", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Mathematics ${(mathTotal / prog.spineDays * 100).toInt()}% · GS ${(gsTotal / prog.spineDays * 100).toInt()}% · about ${hoursDone.toInt()} study hours logged",
                    color = p.dim, fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Bar(overall.toFloat())
            }
        }
        if (s.queue.isNotEmpty()) {
            item {
                Panel {
                    Label("Pending and overdue")
                    Text("${s.queue.size} items overdue · ${"%.1f".format(s.backlog)} h", color = p.warn, fontSize = 15.sp)
                }
            }
        }
        item { Label("GS blocks") }
        items(prog.gsBlocks) { b ->
            val days = (b.startDay..b.endDay).toList()
            val done = days.sumOf { unitCredit(it, Engine.Stream.GS) }
            val pending = days.filter { unitCredit(it, Engine.Stream.GS) == 0.0 }
            BlockCard(
                name = b.name, done = done, total = days.size, open = expanded[b.id] == true,
                onToggle = { expanded[b.id] = expanded[b.id] != true },
                pending = pending.take(10).map { Pair(it, prog.day(it).gs.title) },
                pendingCount = pending.size, onOpen = openDay
            )
        }
        item { Label("Mathematics") }
        items(prog.mathWindows) { b ->
            val days = (b.startDay..b.endDay).toList()
            val done = days.sumOf { unitCredit(it, Engine.Stream.MATH) }
            val pending = days.filter { unitCredit(it, Engine.Stream.MATH) == 0.0 }
            BlockCard(
                name = b.name, done = done, total = days.size, open = expanded["m" + b.id] == true,
                onToggle = { expanded["m" + b.id] = expanded["m" + b.id] != true },
                pending = pending.take(10).map { Pair(it, prog.day(it).math.title) },
                pendingCount = pending.size, onOpen = openDay
            )
        }
    }
}

@Composable
private fun BlockCard(
    name: String,
    done: Double,
    total: Int,
    open: Boolean,
    onToggle: () -> Unit,
    pending: List<Pair<Int, String>>,
    pendingCount: Int,
    onOpen: (Int) -> Unit
) {
    val p = LocalPal.current
    Panel(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(done / total * 100).toInt()}%", color = p.dim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Bar((done / total).toFloat())
        Spacer(Modifier.height(6.dp))
        Text("$pendingCount of $total topics pending", color = p.dim, fontSize = 12.sp)
        if (open && pending.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            pending.forEach { item ->
                Text(
                    "Day ${item.first} · ${item.second}", color = p.text, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(item.first) }.padding(vertical = 4.dp)
                )
            }
            if (pendingCount > pending.size) {
                Text("and ${pendingCount - pending.size} more", color = p.dim, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Notes

@Composable
fun NotesScreen(vm: MainViewModel, openDay: (Int) -> Unit) {
    val s = vm.state
    val p = LocalPal.current
    val notes = s.fields.entries
        .filter { it.key.endsWith("|note") && it.value.isNotBlank() }
        .map { Pair(it.key.removeSuffix("|note"), it.value) }
        .sortedBy { dayOfKey(it.first) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Label("Notes")
                Text("${notes.size} notes", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Write notes inside any day view. They are stored only on this device.", color = p.dim, fontSize = 13.sp)
            }
        }
        items(notes) { n ->
            Panel(onClick = { openDay(dayOfKey(n.first)) }) {
                Label("${vm.programme.streamLabel(n.first)} · Day ${dayOfKey(n.first)}")
                Text(vm.programme.titleOf(n.first), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(n.second, color = p.dim, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- Day detail

@Composable
fun DayDetailScreen(vm: MainViewModel, day: Int, onBack: () -> Unit) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val plan = prog.day(day)
    val date = s.calendar.firstOrNull { it.type == Engine.DayType.SPINE && it.spineDay == day }?.date
    val mKey = Engine.Item.key(day, Engine.Stream.MATH)
    val gKey = Engine.Item.key(day, Engine.Stream.GS)

    androidx.compose.runtime.key(day) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = p.accent) }
            Spacer(Modifier.weight(1f))
            Text("Day $day" + (if (date != null) " · ${date.format(FULL_DATE)}" else ""), color = p.dim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        // Mathematics
        val m = plan.math
        Panel {
            Label("Mathematics · ${m.topic} · session ${m.session}/${m.of}")
            Spacer(Modifier.height(6.dp))
            Text(m.title, color = p.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Objective: ${mathObjective(m)}", color = p.dim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            m.sources.forEachIndexed { i, id ->
                val src = prog.sources[id]
                Text(
                    (if (i == 0) "Primary: " else "Also: ") + (src?.title ?: id) + (if (src != null && !src.verified) "  (confirm)" else ""),
                    color = p.text, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            StatusRow(vm, mKey)
            Spacer(Modifier.height(10.dp))
            FieldBox("My reference (chapter or video)", vm.field(mKey, "ref")) { vm.saveField(mKey, "ref", it) }
            Spacer(Modifier.height(8.dp))
            FieldBox("Notes", vm.field(mKey, "note"), singleLine = false) { vm.saveField(mKey, "note", it) }
        }

        Spacer(Modifier.height(12.dp))

        // GS
        val g = plan.gs
        Panel {
            Label("GS · ${g.block}")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(g.tier, tierColor(g.tier, p))
                if (g.kind != "learn") Pill(g.kind, p.good)
            }
            Spacer(Modifier.height(6.dp))
            Text(g.title, color = p.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Objective: ${g.objective}", color = p.dim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Primary: " + srcName(vm, g.primarySource) + " — " + g.primaryRef +
                    (if (g.primaryLevel == "topic") "  (topic-level: bind your own chapter below)" else ""),
                color = p.text, fontSize = 13.sp
            )
            if (g.suppSource != "-") {
                Text(
                    "Also: " + srcName(vm, g.suppSource) + " — " + g.suppRef,
                    color = p.text, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("PYQ focus: ${plan.pyq}", color = p.dim, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            StatusRow(vm, gKey)
            Spacer(Modifier.height(10.dp))
            FieldBox("My chapter reference", vm.field(gKey, "ref")) { vm.saveField(gKey, "ref", it) }
            Spacer(Modifier.height(8.dp))
            FieldBox("Lecture reference (StudyIQ)", vm.field(gKey, "lecture")) { vm.saveField(gKey, "lecture", it) }
            Spacer(Modifier.height(8.dp))
            FieldBox("Notes", vm.field(gKey, "note"), singleLine = false) { vm.saveField(gKey, "note", it) }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun StatusRow(vm: MainViewModel, key: String) {
    val p = LocalPal.current
    val st = vm.state.status[key] ?: Engine.Status.PENDING
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (st == Engine.Status.PENDING) {
            PrimaryButton("Done", { vm.setStatus(key, Engine.Status.DONE) })
            GhostButton("Skim", { vm.setStatus(key, Engine.Status.SKIMMED) })
        } else {
            Text(
                if (st == Engine.Status.DONE) "Completed" else "Skimmed",
                color = p.good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            GhostButton("Undo", { vm.setStatus(key, Engine.Status.PENDING) })
        }
    }
}

// ---------------------------------------------------------------- Recovery queue

@Composable
fun QueueScreen(vm: MainViewModel, onBack: () -> Unit, openDay: (Int) -> Unit) {
    val s = vm.state
    val p = LocalPal.current
    val levelColor = when (s.level) {
        Engine.Level.RED -> p.bad
        Engine.Level.YELLOW -> p.warn
        else -> p.good
    }
    val levelText = when (s.level) {
        Engine.Level.RED -> "Over capacity"
        Engine.Level.YELLOW -> "Watch"
        else -> "Manageable"
    }
    val end = s.calendar.last().date
    val next = s.calendar.firstOrNull {
        !it.date.isBefore(s.today) && (it.type == Engine.DayType.SUNDAY || it.type == Engine.DayType.FLEX)
    }
    val pack = if (next != null) {
        Engine.pack(s.queue, if (next.type == Engine.DayType.SUNDAY) Engine.SUNDAY_CAP else Engine.FLEX_CAP)
    } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back", color = p.accent) }
            }
        }
        item {
            Panel(borderColor = levelColor) {
                Label("Recovery queue")
                Spacer(Modifier.height(4.dp))
                Text("${"%.1f".format(s.backlog)} h overdue", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("${s.queue.size} items · $levelText", color = levelColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                if (s.delayDays > 0) {
                    Text(
                        "At this backlog the programme would finish about ${s.delayDays} days late (around ${end.plusDays(s.delayDays.toLong()).format(FULL_DATE)}).",
                        color = p.text, fontSize = 13.sp
                    )
                } else {
                    Text("The backlog fits within the remaining Sundays and flex days.", color = p.text, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Order: Mathematics first, then core, standard and extended topics, always after their prerequisites. Extended topics unresolved across two Sundays drop to a 1.5 h skim. Core topics are never dropped.",
                    color = p.dim, fontSize = 12.sp
                )
            }
        }
        if (next != null && pack != null && s.queue.isNotEmpty()) {
            item {
                Panel {
                    Label("Next recovery day · ${next.date.format(FULL_DATE)}")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${pack.picked.size} items fit (${"%.2f".format(pack.hoursUsed)} h); ${pack.leftover.size} carry over.",
                        color = p.text, fontSize = 14.sp
                    )
                }
            }
        }
        if (s.queue.isEmpty()) {
            item { Text("Nothing is overdue.", color = p.dim, fontSize = 14.sp) }
        }
        items(s.queue) { e ->
            Panel { RecoveryRow(vm, e, openDay) }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = vm.state
    val prog = vm.programme
    val p = LocalPal.current
    val ctx = LocalContext.current
    var startText by remember { mutableStateOf(s.start.toString()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportBackup().toByteArray()) }
                Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not save the backup", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (vm.importBackup(text)) {
                    Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "That file is not a UPSC 365 backup", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not read the file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(ctx, "Notifications are blocked in system settings", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label("Settings")

        Panel {
            Label("Start date")
            Spacer(Modifier.height(6.dp))
            FieldBox("Start date (yyyy-mm-dd)", startText) { startText = it }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Apply", {
                if (vm.setStart(startText)) {
                    Toast.makeText(ctx, "Start date updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Use the format yyyy-mm-dd", Toast.LENGTH_SHORT).show()
                }
            })
            Spacer(Modifier.height(6.dp))
            Text(
                "Changing the start date moves every day on the calendar. Your completed items stay attached to their programme days.",
                color = p.dim, fontSize = 12.sp
            )
        }

        Panel {
            Label("Appearance")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { opt ->
                    if (s.theme == opt.first) {
                        PrimaryButton(opt.second, { vm.setTheme(opt.first) })
                    } else {
                        GhostButton(opt.second, { vm.setTheme(opt.first) })
                    }
                }
            }
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("Daily reminder")
                    Text("Before your study window starts", color = p.dim, fontSize = 13.sp)
                }
                Switch(
                    checked = s.remOn,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.setReminder(on, s.remHour, s.remMinute)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = p.accent)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("−", { vm.setReminder(s.remOn, (s.remHour + 23) % 24, s.remMinute) })
                Text("%02d".format(s.remHour), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                GhostButton("+", { vm.setReminder(s.remOn, (s.remHour + 1) % 24, s.remMinute) })
                Spacer(Modifier.width(12.dp))
                GhostButton("−", { vm.setReminder(s.remOn, s.remHour, (s.remMinute + 45) % 60) })
                Text("%02d".format(s.remMinute), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                GhostButton("+", { vm.setReminder(s.remOn, s.remHour, (s.remMinute + 15) % 60) })
            }
        }

        Panel {
            Label("Backup")
            Spacer(Modifier.height(6.dp))
            Text(
                "Your progress lives only on this phone. Export a backup file regularly and keep it somewhere safe.",
                color = p.dim, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Export", { exportLauncher.launch("upsc365-backup-${LocalDate.now()}.json") })
                GhostButton("Import", { importLauncher.launch(arrayOf("*/*")) })
            }
        }

        Panel {
            Label("About")
            Spacer(Modifier.height(6.dp))
            Text(
                "Fully offline. This app declares no internet permission, needs no account, and stores everything in a local database. ${prog.days.size} programme days are bundled inside the app.",
                color = p.dim, fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
