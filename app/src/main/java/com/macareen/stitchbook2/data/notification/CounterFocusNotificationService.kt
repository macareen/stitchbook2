package com.macareen.stitchbook2.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.macareen.stitchbook2.MainActivity
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.StitchbookApplication
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.usecase.IncrementCounterUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A foreground service scoped to one active Focus Mode session
 * (PRODUCT_SPEC.md 6.3's "persistent notifications", the final Phase 3
 * item): [GuideFocusScreen] starts this when it enters [InProgress] with at
 * least one project counter, and stops it when the screen is left --
 * there is deliberately no always-on background tracking (this app has no
 * WorkManager/AlarmManager usage anywhere, and the user chose the
 * Focus-Mode-scoped option over a longer-lived background service).
 *
 * Only the first counter (by the same order the on-screen strip already
 * uses) gets quick increment/decrement actions on the notification itself --
 * Android's own action-button limit and the awkwardness of one PendingIntent
 * per counter made a full action per counter impractical. Every counter's
 * current value is still visible in the notification text either way.
 */
class CounterFocusNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observationJob: Job? = null
    private var observedProjectId: String? = null
    private var guideName: String = ""
    private var latestCounters: List<Counter> = emptyList()

    private val counterRepository: CounterRepository
        get() = (application as StitchbookApplication).container.counterRepository

    private val incrementCounter by lazy { IncrementCounterUseCase(counterRepository) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_INCREMENT -> handleIncrement(intent)
            ACTION_DECREMENT -> handleDecrement(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observationJob?.cancel()
        serviceScope.cancel()
        observedProjectId = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStart(intent: Intent) {
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return
        guideName = intent.getStringExtra(EXTRA_GUIDE_NAME).orEmpty()
        startForeground(NOTIFICATION_ID, buildNotification(latestCounters))

        if (projectId == observedProjectId) {
            // Same session already being observed -- just refresh the
            // title/text with the latest guideName without restarting the
            // Flow collection below.
            updateNotification(latestCounters)
            return
        }

        observedProjectId = projectId
        observationJob?.cancel()
        observationJob = serviceScope.launch {
            counterRepository.observeCountersByProject(projectId).collect { counters ->
                latestCounters = counters
                if (counters.isEmpty()) {
                    // Nothing left to show -- e.g. the project's only
                    // counter was deleted from the Counters screen while
                    // Focus Mode stayed open. Stop rather than show an
                    // empty persistent notification.
                    stopSelf()
                } else {
                    updateNotification(counters)
                }
            }
        }
    }

    private fun handleIncrement(intent: Intent) {
        val counterId = intent.getStringExtra(EXTRA_COUNTER_ID) ?: return
        serviceScope.launch {
            val counter = counterRepository.observeCounter(counterId).first() ?: return@launch
            incrementCounter(counter)
        }
    }

    private fun handleDecrement(intent: Intent) {
        val counterId = intent.getStringExtra(EXTRA_COUNTER_ID) ?: return
        serviceScope.launch {
            val counter = counterRepository.observeCounter(counterId).first() ?: return@launch
            val newValue = (counter.currentValue - 1).coerceAtLeast(0)
            if (newValue == counter.currentValue) return@launch
            try {
                counterRepository.saveCounter(
                    counter.copy(currentValue = newValue, updatedAt = System.currentTimeMillis())
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same rationale as GuideFocusViewModel.onDecrementCounter.
            }
        }
    }

    private fun updateNotification(counters: List<Counter>) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(counters))
    }

    private fun buildNotification(counters: List<Counter>): Notification {
        val contentText = counterSummaryText(counters)
            ?: getString(R.string.focus_notification_no_counters)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_counter)
            .setContentTitle(guideName.ifBlank { getString(R.string.focus_notification_default_title) })
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        counters.firstOrNull()?.let { primary ->
            builder.addAction(
                R.drawable.ic_notification_counter,
                getString(R.string.counters_decrement_action),
                counterActionPendingIntent(ACTION_DECREMENT, primary.id, requestCode = 1)
            )
            builder.addAction(
                R.drawable.ic_notification_counter,
                getString(R.string.counters_increment_action),
                counterActionPendingIntent(ACTION_INCREMENT, primary.id, requestCode = 2)
            )
        }

        return builder.build()
    }

    private fun counterActionPendingIntent(action: String, counterId: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, CounterFocusNotificationService::class.java).apply {
            this.action = action
            putExtra(EXTRA_COUNTER_ID, counterId)
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.focus_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.focus_notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "counter_focus_channel"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.macareen.stitchbook2.action.START_COUNTER_FOCUS"
        private const val ACTION_INCREMENT = "com.macareen.stitchbook2.action.INCREMENT_COUNTER"
        private const val ACTION_DECREMENT = "com.macareen.stitchbook2.action.DECREMENT_COUNTER"

        private const val EXTRA_PROJECT_ID = "extra_project_id"
        private const val EXTRA_GUIDE_NAME = "extra_guide_name"
        private const val EXTRA_COUNTER_ID = "extra_counter_id"

        /** Starts (or, if already running, retargets) the notification for [projectId]'s counters. */
        fun start(context: Context, projectId: String, guideName: String) {
            val intent = Intent(context, CounterFocusNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_GUIDE_NAME, guideName)
            }
            context.startForegroundService(intent)
        }

        /** No-ops if the service isn't currently running. */
        fun stop(context: Context) {
            context.stopService(Intent(context, CounterFocusNotificationService::class.java))
        }
    }
}

/**
 * The notification's content text: every counter's current value (and goal,
 * if set), in the same left-to-right order as the on-screen strip. Null if
 * there are no counters, so the caller can fall back to an explanatory
 * string instead of an empty notification body.
 */
fun counterSummaryText(counters: List<Counter>): String? {
    if (counters.isEmpty()) return null
    return counters.joinToString(separator = "  ·  ") { counter ->
        val goal = counter.goal
        if (goal != null) {
            "${counter.name}: ${counter.currentValue}/$goal"
        } else {
            "${counter.name}: ${counter.currentValue}"
        }
    }
}
