package dev.hostyn.phone.call

import dev.hostyn.phone.R
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.os.BundleCompat
import dev.hostyn.phone.data.CallerInfo
import dev.hostyn.phone.data.ContactField
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.data.loadContactPhoto
import dev.hostyn.phone.data.lookupNumber
import dev.hostyn.phone.ui.components.phoneLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CallPhase { RINGING, SELECT_ACCOUNT, DIALING, ACTIVE, HOLDING, ENDED }

/** Immutable call state, ready to render. */
data class CallUi(
    val id: Int,
    val phase: CallPhase,
    val number: String,
    val name: String,
    val label: String?,
    val photoUri: String?,
    val contactId: Long?,
    val connectTime: Long,
    val canHold: Boolean,
    val outgoing: Boolean,
    val accounts: List<PhoneAccountHandle>,
    val endReason: String?,
    val isConference: Boolean = false,
    /** Can be merged with another call into a conference. */
    val canMerge: Boolean = false,
    /** Can be rejected with a text message. */
    val canReplyWithText: Boolean = false,
    /** Still looking up who is calling (the contact's name and photo aren't known yet). */
    val identifying: Boolean = false,
)

/**
 * Bridge between the InCallService (which receives calls from the system) and the UI.
 * Everything runs on the main thread.
 */
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private class Entry(val id: Int, val outgoing: Boolean) {
        var info: CallerInfo? = null
        var photo: Bitmap? = null
        var lookedUp = false
    }

    private val entries = LinkedHashMap<Call, Entry>()
    private var nextId = 1
    private var service: CallService? = null
    private var appContext: Context? = null

    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    val calls: StateFlow<List<CallUi>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio.asStateFlow()

    /** The last call that ended, to briefly show “Call ended”. */
    private val _lastEnded = MutableStateFlow<CallUi?>(null)
    val lastEnded: StateFlow<CallUi?> = _lastEnded.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publish()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = publish()
    }

    fun attach(s: CallService) {
        service = s
        appContext = s.applicationContext
    }

    fun detach(s: CallService) {
        if (service === s) service = null
    }

    fun onCallAdded(call: Call) {
        val state = call.stateCompat()
        val entry = Entry(nextId++, outgoing = state != Call.STATE_RINGING && state != Call.STATE_SIMULATED_RINGING)
        entries[call] = entry
        call.registerCallback(callback)
        _lastEnded.value = null
        publish()

        val ctx = appContext ?: run {
            entry.lookedUp = true
            publish()
            return
        }
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        scope.launch {
            val (info, photo) = withContext(Dispatchers.IO) {
                val i = ctx.contentResolver.lookupNumber(number)
                i to i?.let { ctx.contentResolver.loadContactPhoto(it.contactId) }
            }
            if (entries[call] === entry) {
                entry.info = info
                entry.photo = photo
                entry.lookedUp = true
                publish()
            }
        }
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        val entry = entries.remove(call)
        if (entries.isEmpty() && entry != null) {
            _lastEnded.value = toUi(call, entry).copy(phase = CallPhase.ENDED)
        }
        publish()
    }

    fun onAudioState(state: CallAudioState) {
        _audio.value = state
    }

    fun photoFor(id: Int): Bitmap? = entries.values.firstOrNull { it.id == id }?.photo

    /** The call that should be featured on screen and in the notification. */
    fun primary(calls: List<CallUi>): CallUi? = calls.minByOrNull { it.phase.ordinal }

    private fun publish() {
        // Conference participants are shown inside their conference, not as separate calls.
        _calls.value = entries.filter { (call, _) -> call.parent == null }.map { (call, entry) -> toUi(call, entry) }
    }

    private fun toUi(call: Call, entry: Entry): CallUi {
        val ctx = appContext
        val d = call.details
        val number = d.handle?.schemeSpecificPart.orEmpty()
        val formatted = if (ctx != null) formatPhone(ctx, number) else number
        val info = entry.info
        val name = info?.name
            ?: d.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: formatted.ifBlank {
                ctx?.getString(if (d.handlePresentation == TelecomManager.PRESENTATION_RESTRICTED) R.string.private_number else R.string.unknown).orEmpty()
            }
        val label = when {
            info != null -> listOf(ctx?.let { ContactField(number, info.type, info.label).phoneLabel(it) }.orEmpty(), formatted)
                .filter { it.isNotBlank() }.joinToString(" · ")
            name != formatted && formatted.isNotBlank() -> formatted
            else -> null
        }
        val phase = when (call.stateCompat()) {
            Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallPhase.RINGING
            Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.SELECT_ACCOUNT
            Call.STATE_ACTIVE -> CallPhase.ACTIVE
            Call.STATE_HOLDING -> CallPhase.HOLDING
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> CallPhase.ENDED
            else -> CallPhase.DIALING
        }
        val accounts = if (phase == CallPhase.SELECT_ACCOUNT) {
            d.intentExtras?.let {
                BundleCompat.getParcelableArrayList(it, Call.AVAILABLE_PHONE_ACCOUNTS, PhoneAccountHandle::class.java)
            }.orEmpty()
        } else emptyList()

        val conference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE) || call.children.isNotEmpty()
        val participants = if (conference) {
            call.children.mapNotNull { child -> entries[child]?.let { toUi(child, it).name } }
        } else emptyList()

        return CallUi(
            id = entry.id,
            phase = phase,
            number = if (conference) "" else number,
            name = if (conference) ctx?.getString(R.string.conference).orEmpty() else name,
            label = if (conference) participants.joinToString(", ").ifBlank { null } else label,
            photoUri = info?.photoUri,
            contactId = info?.contactId,
            connectTime = d.connectTimeMillis,
            canHold = d.can(Call.Details.CAPABILITY_SUPPORT_HOLD) || d.can(Call.Details.CAPABILITY_HOLD),
            outgoing = if (Build.VERSION.SDK_INT >= 29) d.callDirection == Call.Details.DIRECTION_OUTGOING else entry.outgoing,
            accounts = accounts,
            endReason = d.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() },
            isConference = conference,
            canMerge = call.conferenceableCalls.isNotEmpty() || d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canReplyWithText = d.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT),
            identifying = !entry.lookedUp,
        )
    }

    private fun find(id: Int): Call? = entries.entries.firstOrNull { it.value.id == id }?.key

    // --- Actions ---

    fun answer(id: Int) {
        find(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject(id: Int) {
        find(id)?.reject(false, null)
    }

    /** Declines the call and sends the caller a text message. */
    fun rejectWithMessage(id: Int, message: String) {
        find(id)?.reject(true, message)
    }

    /** Call waiting: ends the current call(s) and answers the ringing one. */
    fun endAndAnswer(id: Int) {
        val ringing = find(id) ?: return
        entries.keys
            .filter { it !== ringing && it.parent == null && it.stateCompat() != Call.STATE_DISCONNECTED }
            .forEach { it.disconnect() }
        ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Merges the call with the other one into a conference. */
    fun merge(id: Int) {
        val call = find(id) ?: return
        val other = call.conferenceableCalls.firstOrNull()
        if (other != null) call.conference(other)
        else if (call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) call.mergeConference()
    }

    fun hangUp(id: Int) {
        val call = find(id) ?: return
        val state = call.stateCompat()
        if (state == Call.STATE_RINGING || state == Call.STATE_SIMULATED_RINGING) call.reject(false, null)
        else call.disconnect()
    }

    fun setHold(id: Int, hold: Boolean) {
        val call = find(id) ?: return
        if (hold) call.hold() else call.unhold()
    }

    fun selectAccount(id: Int, handle: PhoneAccountHandle) {
        find(id)?.phoneAccountSelected(handle, false)
    }

    fun startDtmf(id: Int, digit: Char) {
        find(id)?.playDtmfTone(digit)
    }

    fun stopDtmf(id: Int) {
        find(id)?.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) {
        service?.setAudioRoute(route)
    }
}

@Suppress("DEPRECATION")
fun Call.stateCompat(): Int = if (Build.VERSION.SDK_INT >= 31) details.state else state
