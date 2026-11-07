package app.upsc365.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.upsc365.data.Programme
import app.upsc365.data.ProgrammeLoader
import app.upsc365.data.Store
import app.upsc365.engine.Engine
import app.upsc365.notify.Reminder
import java.time.LocalDate

data class UiState(
    val today: LocalDate,
    val start: LocalDate,
    val calendar: List<Engine.CalDay>,
    val status: Map<String, Engine.Status>,
    val doneOn: Map<String, LocalDate>,
    val queue: List<Engine.QueueEntry>,
    val backlog: Double,
    val level: Engine.Level,
    val delayDays: Int,
    val reviews: Engine.ReviewResult,
    val fields: Map<String, String>,
    val theme: String,
    val remOn: Boolean,
    val remHour: Int,
    val remMinute: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = Store(application)
    val programme: Programme = ProgrammeLoader.load(application)

    var state by mutableStateOf(build())
        private set

    private fun readStart(): LocalDate {
        val saved = store.getKv("start")
        if (saved != null) {
            try {
                return LocalDate.parse(saved)
            } catch (e: Exception) {
                // fall through to the default start date
            }
        }
        return LocalDate.parse(programme.defaultStart)
    }

    private fun build(): UiState {
        val start = readStart()
        val cal = Engine.buildCalendar(start, programme.flexAfter, programme.spineDays)
        val dates = Engine.spineDates(cal)
        val progress = store.loadProgress()
        val status = progress.first
        val doneOn = progress.second
        val today = LocalDate.now()
        val queue = Engine.recoveryQueue(programme.items, dates, status, today, cal)
        val backlog = Engine.backlogHours(queue)
        val reviews = Engine.dueReviews(doneOn, store.loadCompletedReviews(), today)
        return UiState(
            today = today,
            start = start,
            calendar = cal,
            status = status,
            doneOn = doneOn,
            queue = queue,
            backlog = backlog,
            level = Engine.level(backlog),
            delayDays = Engine.projectedDelayDays(backlog, today, cal),
            reviews = reviews,
            fields = store.loadFields(),
            theme = store.getKv("theme") ?: "dark",
            remOn = store.getKv("rem_on") == "1",
            remHour = store.getKv("rem_h")?.toIntOrNull() ?: 18,
            remMinute = store.getKv("rem_m")?.toIntOrNull() ?: 45
        )
    }

    fun refresh() {
        state = build()
    }

    fun setStatus(key: String, status: Engine.Status) {
        if (status == Engine.Status.PENDING) {
            store.setProgress(key, Engine.Status.PENDING, null)
            store.deleteReviews(key)
        } else {
            store.setProgress(key, status, LocalDate.now().toString())
        }
        state = build()
    }

    fun markReview(review: Engine.Review) {
        store.markReview(review.key, review.interval, LocalDate.now().toString())
        state = build()
    }

    fun saveField(key: String, name: String, value: String) {
        store.setField(key, name, value)
        val map = HashMap(state.fields)
        val k = "$key|$name"
        if (value.isBlank()) map.remove(k) else map[k] = value
        state = state.copy(fields = map)
    }

    fun field(key: String, name: String): String = state.fields["$key|$name"] ?: ""

    fun setStart(text: String): Boolean {
        val date = try {
            LocalDate.parse(text.trim())
        } catch (e: Exception) {
            return false
        }
        store.setKv("start", date.toString())
        state = build()
        return true
    }

    fun setTheme(theme: String) {
        store.setKv("theme", theme)
        state = build()
    }

    fun setReminder(on: Boolean, hour: Int, minute: Int) {
        store.setKv("rem_on", if (on) "1" else "0")
        store.setKv("rem_h", hour.toString())
        store.setKv("rem_m", minute.toString())
        state = build()
        applyReminder()
    }

    fun applyReminder() {
        val app = getApplication<Application>()
        if (state.remOn) Reminder.schedule(app, state.remHour, state.remMinute) else Reminder.cancel(app)
    }

    fun exportBackup(): String = store.exportJson()

    fun importBackup(text: String): Boolean {
        val ok = store.importJson(text)
        if (ok) {
            state = build()
            applyReminder()
        }
        return ok
    }
}
