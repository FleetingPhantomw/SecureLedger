package com.secureledger.integration.accessibility

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.secureledger.SecureLedgerApplication
import com.secureledger.core.accessibility.DetectionSource
import com.secureledger.core.accessibility.PaymentNotificationParser

/** Explicitly opt-in payment notifications complement inaccessible protected payment screens. */
class PaymentNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val app = application as SecureLedgerApplication
        val settings = app.accessibilityPreferences.state.value
        if (!settings.enabled || !settings.notificationCapture || sbn.packageName !in settings.selectedPackages) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val candidate = PaymentNotificationParser.parse(sbn.packageName,
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(), sbn.postTime) ?: return
        val result = app.recognitionCoordinator.accept(candidate, DetectionSource.NOTIFICATION,
            "${sbn.key}|${sbn.postTime}|${candidate.amountCents}") ?: return
        if (result.isNew) PromptDispatcher(this).dispatch(result.candidate, settings.promptMode)
        else if (CandidateInbox.pending.value?.id == result.candidate.id) CandidateInbox.offer(result.candidate)
    }
}
