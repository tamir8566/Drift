package com.tamir.drift.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.model.TransactionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Captures incoming SMS from the configured senders (default max / MAX / Max). Multipart
 * messages are joined. Matching is on the display originating address, case-insensitive.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val sender = messages.first().displayOriginatingAddress.orEmpty()
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return
        val receivedAt = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val graph = AppGraph.get(context.applicationContext)
                val senders = graph.settings.current().smsSenders
                if (matches(sender, senders)) {
                    graph.captureProcessor.process(TransactionSource.SMS, sender, body, receivedAt)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Case-insensitive exact match on the sender id (so "max", "MAX" and "Max" are one entry). */
        fun matches(sender: String, configured: Set<String>): Boolean {
            val s = sender.trim().lowercase()
            if (s.isEmpty()) return false
            return configured.any { c -> c.trim().lowercase() == s }
        }
    }
}
