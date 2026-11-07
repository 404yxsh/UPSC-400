package app.upsc365

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import app.upsc365.notify.Reminder
import app.upsc365.ui.Upsc365Root
import app.upsc365.vm.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminder.ensureChannel(this)
        setContent { Upsc365Root(vm) }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        vm.applyReminder()
    }
}
