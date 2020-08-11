package com.twilio_voice.fcm

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twilio.voice.*
import com.twilio_voice.Constants
import com.twilio_voice.IncomingCallNotificationService

class VoiceFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "VoiceFCMService"
    }

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Received onMessageReceived()")
        Log.d(TAG, "Bundle data: " + remoteMessage.data)
        Log.d(TAG, "From: " + remoteMessage.from)

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            val valid: Boolean = Voice.handleMessage(this, remoteMessage.data, object : MessageListener {
                override fun onCallInvite(callInvite: CallInvite) {
                    val notificationId = System.currentTimeMillis().toInt()
                    handleInvite(callInvite, notificationId)
                }

                override fun onCancelledCallInvite(cancelledCallInvite: CancelledCallInvite, callException: CallException?) {
                    handleCanceledCallInvite(cancelledCallInvite)
                }
            })
            if (!valid) {
                Log.e(TAG, "The message was not a valid Twilio Voice SDK payload: " +
                        remoteMessage.data)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val intent = Intent(Constants.ACTION_FCM_TOKEN)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun handleInvite(callInvite: CallInvite, notificationId: Int) {
        val intent = Intent(this, IncomingCallNotificationService::class.java)
        intent.action = Constants.ACTION_INCOMING_CALL
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId)
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite)

        startService(intent)
    }

    private fun handleCanceledCallInvite(cancelledCallInvite: CancelledCallInvite) {
        val intent = Intent(this, IncomingCallNotificationService::class.java)
        intent.action = Constants.ACTION_CANCEL_CALL
        intent.putExtra(Constants.CANCELLED_CALL_INVITE, cancelledCallInvite)

        startService(intent)
    }
}