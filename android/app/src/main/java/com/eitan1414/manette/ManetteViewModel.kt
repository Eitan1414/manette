package com.eitan1414.manette

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.eitan1414.manette.data.LayoutRepository
import com.eitan1414.manette.model.ControlPlacement
import com.eitan1414.manette.model.ControllerButton
import com.eitan1414.manette.model.DefaultLayout
import com.eitan1414.manette.model.InputSnapshot
import com.eitan1414.manette.network.UdpControllerClient
import kotlin.math.roundToInt

class ManetteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LayoutRepository(application)
    private val network = UdpControllerClient()
    private val inputLock = Any()

    var layout by mutableStateOf(repository.loadLayout())
        private set

    var editMode by mutableStateOf(false)
        private set

    var selectedControlId by mutableStateOf<String?>(null)
        private set

    var targetIp by mutableStateOf(repository.loadIp())
        private set

    var networkConfigured by mutableStateOf(false)
        private set

    private var buttons = 0
    private var leftX: Short = 0
    private var leftY: Short = 0
    private var rightX: Short = 0
    private var rightY: Short = 0

    init {
        if (targetIp.isNotBlank()) {
            networkConfigured = network.configure(targetIp)
        }
    }

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        if (!enabled) {
            selectedControlId = null
            repository.saveLayout(layout)
        }
    }

    fun selectControl(id: String) {
        selectedControlId = id
    }

    fun moveControl(id: String, dxFraction: Float, dyFraction: Float) {
        layout = layout.map { control ->
            if (control.id != id) control else control.copy(
                x = (control.x + dxFraction).coerceIn(0.02f, 0.98f),
                y = (control.y + dyFraction).coerceIn(0.02f, 0.98f)
            )
        }
    }

    fun resizeControl(id: String, sizeDp: Float) {
        layout = layout.map { control ->
            if (control.id == id) control.copy(sizeDp = sizeDp.coerceIn(38f, 190f)) else control
        }
    }

    fun persistLayout() {
        repository.saveLayout(layout)
    }

    fun resetLayout() {
        repository.resetLayout()
        layout = DefaultLayout.controls
        selectedControlId = null
    }

    fun connect(ip: String): Boolean {
        val normalized = ip.trim()
        if (normalized.isBlank()) {
            network.clearTarget()
            targetIp = ""
            repository.saveIp("")
            networkConfigured = false
            return false
        }
        val ok = network.configure(normalized)
        if (ok) {
            targetIp = normalized
            repository.saveIp(normalized)
        }
        networkConfigured = ok
        return ok
    }

    fun setButton(button: ControllerButton, pressed: Boolean) {
        synchronized(inputLock) {
            buttons = if (pressed) buttons or button.mask else buttons and button.mask.inv()
            publishLocked()
        }
    }

    fun setStick(left: Boolean, x: Float, y: Float) {
        val sx = toShortAxis(x)
        val sy = toShortAxis(y)
        synchronized(inputLock) {
            if (left) {
                leftX = sx
                leftY = sy
            } else {
                rightX = sx
                rightY = sy
            }
            publishLocked()
        }
    }

    private fun publishLocked() {
        network.update(InputSnapshot(buttons, leftX, leftY, rightX, rightY))
    }

    private fun toShortAxis(value: Float): Short {
        return (value.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
    }

    override fun onCleared() {
        network.close()
        super.onCleared()
    }
}
