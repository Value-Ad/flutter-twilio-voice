package com.twilio_voice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.twilio.audioswitch.selection.AudioDeviceSelector
import com.twilio.voice.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** TwilioVoicePlugin */
public class TwilioVoicePlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.NewIntentListener {

	companion object {
		private const val methodChannelName = "twilio_voice/methods"
		private const val callEventChannelName = "twilio_voice/call"
		private const val incomingCallEventChannelName = "twilio_voice/incoming_call"

		private const val TAG = "TwilioVoicePlugin"
		private const val MIC_PERMISSION_REQUEST_CODE = 1

		@JvmStatic
		private fun register(messenger: BinaryMessenger, plugin: TwilioVoicePlugin, context: Context) {
			plugin.methodChannel = MethodChannel(messenger, methodChannelName)
			plugin.methodChannel.setMethodCallHandler(plugin)

			plugin.callEventChannel = EventChannel(messenger, callEventChannelName)
			plugin.callEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
				override fun onListen(o: Any?, eventSink: EventChannel.EventSink?) {
					Log.i(TAG, "Setting call event sink")
					plugin.callEventSink = eventSink
				}

				override fun onCancel(o: Any?) {
					Log.i(TAG, "Removing call event sink")
					plugin.callEventSink = null
				}
			})

			plugin.incomingCallEventChannel = EventChannel(messenger, incomingCallEventChannelName)
			plugin.incomingCallEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
				override fun onListen(o: Any?, eventSink: EventChannel.EventSink?) {
					Log.i(TAG, "Setting incoming call event sink")
					plugin.incomingCallEventSink = eventSink
				}

				override fun onCancel(o: Any?) {
					Log.i(TAG, "Removing incoming call event sink")
					plugin.incomingCallEventSink = null
				}
			})

			plugin.context = context
			plugin.notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

			/*
			 * Setup the broadcast receiver to be notified of FCM Token updates
			 * or incoming call invite in this Activity.
			 */
			plugin.voiceBroadcastReceiver = VoiceBroadcastReceiver(plugin)
			plugin.registerReceiver()

			/*
			 * Setup audio device management and set the volume control stream
			 */

			plugin.audioDeviceSelector = AudioDeviceSelector(context)

			/*
			 * Create custom audio device FileAndMicAudioDevice and set the audio device
			 */
			// fileAndMicAudioDevice = FileAndMicAudioDevice(context)
			// Voice.setAudioDevice(fileAndMicAudioDevice)
		}
	}

	private lateinit var methodChannel: MethodChannel
	private lateinit var callEventChannel: EventChannel
	private lateinit var incomingCallEventChannel: EventChannel
	private lateinit var context: Context
	private var activity: Activity? = null
	private lateinit var voiceBroadcastReceiver: VoiceBroadcastReceiver
	private lateinit var notificationManager: NotificationManager
	private var alertDialog: AlertDialog? = null

	/*
	 * Audio device management
	 */
	private lateinit var audioDeviceSelector: AudioDeviceSelector
	//private var savedVolumeControlStream = 0

	private var callEventSink: EventChannel.EventSink? = null
	private var incomingCallEventSink: EventChannel.EventSink? = null
	private var activeCall: Call? = null
	private var activeCallInvite: CallInvite? = null
	private var activeCallNotificationId: Int = 0

	private var accessToken: String? = null
	private var fcmToken: String? = null
	private var isRegister = false
	private var isReceiverRegistered = false
	private var callOutgoing = false

	override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
		try {
			register(flutterPluginBinding.binaryMessenger, this, flutterPluginBinding.applicationContext)
		} catch (e: Exception) {
			print(e)
			return
		}
	}

	override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
		if (call.method != "register" && call.method != "unregister") {
			if (!checkPermissionForMicrophone()) {
				requestPermissionForMicrophone()
				result.error("RECORD_AUDIO_PERMISSION", "RECORD_AUDIO permission not granted", "")
				return
			}

			val accessToken = this.accessToken
			if (!isRegister || accessToken == null) {
				result.error("NOT_REGISTERED", "Not registered", "")
				return
			}
		}

		val arguments = (call.arguments as? HashMap<String, String>) ?: HashMap<String, String>()

		when (call.method) {
			"register" -> register(arguments, result)
			"unregister" -> unregister(result)
			"connect" -> connect(arguments, result)
			"answer" -> answer(activeCallInvite, result)
			"holdCall" -> holdCall(activeCall, result)
			"muteCall" -> muteCall(activeCall, result)
			"sendDigits" -> sendDigits(arguments, activeCall, result)
			"disconnect" -> disconnect(result)
			"isOnCall" -> isOnCall(result)
			else -> result.notImplemented()
		}
	}

	override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
		methodChannel.setMethodCallHandler(null)
	}

	/**
	 * [PluginRegistry.NewIntentListener.onNewIntent]
	 */
	override fun onNewIntent(intent: Intent?): Boolean {
		handleIncomingCallIntent(intent)
		return false
	}

	/**
	 * [ActivityAware.onAttachedToActivity]
	 */
	override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
		/*
		 * Enable changing the volume using the up/down keys during a conversation
		 */
		//savedVolumeControlStream = activityPluginBinding.activity.volumeControlStream
		//activityPluginBinding.activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL

		activity = activityPluginBinding.activity
		activityPluginBinding.addOnNewIntentListener(this)
		registerReceiver()
	}

	/**
	 * [ActivityAware.onDetachedFromActivityForConfigChanges]
	 */
	override fun onDetachedFromActivityForConfigChanges() {
		activity = null
		unregisterReceiver()
	}

	/**
	 * [ActivityAware.onReattachedToActivityForConfigChanges]
	 */
	override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
		activity = activityPluginBinding.activity
		activityPluginBinding.addOnNewIntentListener(this)
		registerReceiver()
	}

	/**
	 * [ActivityAware.onDetachedFromActivity]
	 */
	override fun onDetachedFromActivity() {
		/*
		 * Tear down audio device management and restore previous volume stream
		 */
		audioDeviceSelector.stop()
		//activity?.volumeControlStream = savedVolumeControlStream
		SoundPoolManager.getInstance(context)?.release()

		unregisterReceiver()
		activity = null
	}

	/*
	 * Register your FCM token with Twilio to receive incoming call invites
	 *
	 * If a valid google-services.json has not been provided or the FirebaseInstanceId has not been
	 * initialized the fcmToken will be null.
	 *
	 * In the case where the FirebaseInstanceId has not yet been initialized the
	 * VoiceFirebaseInstanceIDService.onTokenRefresh should result in a LocalBroadcast to this
	 * activity which will attempt registerForCallInvites again.
	 */
	private fun register(params: HashMap<String, String>, result: Result) {
		Log.i(TAG, "Registering with FCM")

		Voice.register(
                params["accessToken"].toString(),
				Voice.RegistrationChannel.FCM,
				params["fcmToken"].toString(),
				object : RegistrationListener {
					override fun onRegistered(rAccessToken: String, rFcmToken: String) {
						Log.d(TAG, "Successfully registered FCM $rFcmToken")
						accessToken = rAccessToken
						fcmToken = rFcmToken
						isRegister = true
						result.success(true)
					}

					override fun onError(error: RegistrationException, accessToken: String, fcmToken: String) {
						val message = String.format("Registration Error: %d, %s", error.errorCode, error.message)
						Log.e(TAG, message)
						isRegister = false
						result.error(error.errorCode.toString(), error.message, null)
					}
				}
		)
	}

	private fun unregister(result: Result) {
		Log.i(TAG, "Un-registering with FCM")

		val accessToken = this.accessToken
		val fcmToken = this.fcmToken

		if (accessToken != null && fcmToken != null) {
			Voice.unregister(
					accessToken,
					Voice.RegistrationChannel.FCM,
					fcmToken,
					object : UnregistrationListener {
						override fun onUnregistered(accessToken: String, fcmToken: String) {
							Log.d(TAG, "Successfully unregistered")
							result.success(true)
						}

						override fun onError(error: RegistrationException, accessToken: String, fcmToken: String) {
							val message = String.format("Unregistration Error: %d, %s", error.errorCode, error.message)
							Log.e(TAG, message)
							result.error(error.errorCode.toString(), error.message, null)
						}
					}
			)
		}
	}

	private fun connect(params: HashMap<String, String>, result: Result) {
		Log.d(TAG, "Making new call")

		val accessToken = this.accessToken ?: ""

		callOutgoing = true
		val connectOptions = ConnectOptions.Builder(accessToken)
				.params(params)
				.build()
		activeCall = Voice.connect(context, connectOptions, callListener())
		result.success(true)
	}

	private fun answer(activeCallInvite: CallInvite?, result: Result?) {
		Log.d(TAG, "Answering call")
		if (activeCallInvite != null) {
			SoundPoolManager.getInstance(context)?.stopRinging()
			activeCall = activeCallInvite.accept(context, callListener())
			notificationManager.cancel(activeCallNotificationId)

			val alertDialog = this.alertDialog
			if (alertDialog != null && alertDialog.isShowing) {
				alertDialog.dismiss()
			}

			incomingCallEventSink?.success(true)

			result?.success(true)
			return
		}
		result?.success(false)
	}

	private fun holdCall(activeCall: Call?, result: Result) {
		Log.d(TAG, "Hold call invoked")
		if (activeCall != null) {
			val hold = activeCall.isOnHold
			activeCall.hold(!hold)
			callEventSink?.success(onHoldEventMap(activeCall))
			result.success(true)
			return
		}
		result.success(false)
	}

	private fun muteCall(activeCall: Call?, result: Result) {
		Log.d(TAG, "Muting call")
		if (activeCall != null) {
			val mute = activeCall.isMuted
			activeCall.mute(!mute)
			callEventSink?.success(mutedEventMap(activeCall))
			result.success(true)
			return
		}
		result.success(false)
	}

	private fun sendDigits(params: HashMap<String, String>, activeCall: Call?, result: Result) {
		val digits = params["digits"].toString()
		Log.d(TAG, "Sending digits $digits")

		if (activeCall != null) {
			activeCall.sendDigits(digits)
			result.success(true)
			return
		}
		result.success(false)
	}

	private fun disconnect(result: Result) {
		Log.d(TAG, "Disconnecting")
		activeCall?.disconnect()
		result.success(true)
	}

	private fun isOnCall(result: Result) {
		Log.d(TAG, "Is on call invoked")
		result.success(this.activeCall != null)
	}

	private fun callListener(): Call.Listener {
		return object : Call.Listener {
			/*
			 * This callback is emitted once before the Call.Listener.onConnected() callback when
			 * the callee is being alerted of a Call. The behavior of this callback is determined by
			 * the answerOnBridge flag provided in the Dial verb of your TwiML application
			 * associated with this client. If the answerOnBridge flag is false, which is the
			 * default, the Call.Listener.onConnected() callback will be emitted immediately after
			 * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
			 * call to emit the onConnected callback only after the call is answered.
			 * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
			 * twiML response contains a Say verb, then the call will emit the
			 * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
			 * raised, irrespective of the value of answerOnBridge being set to true or false
			 */
			override fun onRinging(call: Call) {
				Log.d(TAG, "Ringing")
				/*
				 * When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge)
				 * is enabled in the <Dial> TwiML verb, the caller will not hear the ringback while
				 * the call is ringing and awaiting to be accepted on the callee's side. The application
				 * can use the `SoundPoolManager` to play custom audio files between the
				 * `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks.
				 */
				activeCall = call
				SoundPoolManager.getInstance(context)?.playRinging()
				callEventSink?.success(ringingEventMap(call))
			}

			override fun onConnectFailure(call: Call, callException: CallException) {
				Log.d(TAG, "Connect failure")
				audioDeviceSelector.deactivate()
				SoundPoolManager.getInstance(context)?.stopRinging()
				val message = String.format("Call Error: %d, %s", callException.errorCode, callException.message)
				Log.e(TAG, message)
				activeCall = null
				callEventSink?.success(connectFailureEventMap(call, callException))
			}

			override fun onConnected(call: Call) {
				Log.d(TAG, "Connected")
				//audioDeviceSelector.activate()
				SoundPoolManager.getInstance(context)?.stopRinging()
				activeCall = call
				callEventSink?.success(connectedEventMap(call))
			}

			override fun onReconnecting(call: Call, callException: CallException) {
				Log.d(TAG, "onReconnecting")
				activeCall = call
				callEventSink?.success(reconnectingEventMap(call, callException))
			}

			override fun onReconnected(call: Call) {
				Log.d(TAG, "onReconnected")
				activeCall = call
				callEventSink?.success(reconnectedEventMap(call))
			}

			override fun onDisconnected(call: Call, callException: CallException?) {
				Log.d(TAG, "Disconnected")
				audioDeviceSelector.deactivate()
				SoundPoolManager.getInstance(context)?.stopRinging()

				if (callException != null) {
					val message = String.format("Call Error: %d, %s", callException.errorCode, callException.message)
					Log.e(TAG, message)
				}
				activeCall = null
				callEventSink?.success(disconnectedEventMap(call, callException))
			}
		}
	}

	private fun handleIncomingCallIntent(intent: Intent?) {
		if (intent != null && intent.action != null) {
			val action = intent.action
			Log.d(TAG, "Handling incoming call intent for action $action")

			activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE)
			activeCallNotificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0)
			callOutgoing = false

			when (action) {
				Constants.ACTION_INCOMING_CALL -> handleIncomingCall(activeCallInvite)
				Constants.ACTION_INCOMING_CALL_NOTIFICATION -> showIncomingCallDialog(activeCallInvite)
				Constants.ACTION_CANCEL_CALL -> handleCancel()
				Constants.ACTION_FCM_TOKEN -> {
				}
				Constants.ACTION_ACCEPT -> answer(activeCallInvite, null)
				else -> {
				}
			}
		}
	}

	private fun showIncomingCallDialog(callInvite: CallInvite?) {
		SoundPoolManager.getInstance(context)?.playRinging()
		if (callInvite != null) {
			val alertDialog = createIncomingCallDialog(callInvite)
			alertDialog.show()

			this.alertDialog = alertDialog

			/*
			 * Ensure the microphone permission is enabled
			 */
			if (!this.checkPermissionForMicrophone()) {
				this.requestPermissionForMicrophone()
			}
		}
	}

	private fun handleIncomingCall(callInvite: CallInvite?) {
		if (callInvite != null) {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
				showIncomingCallDialog(callInvite)
			} else {
				if (isAppVisible()) {
					showIncomingCallDialog(callInvite)
				}
			}
		}
	}

	private fun registerReceiver() {
		if (!isReceiverRegistered) {
			val intentFilter = IntentFilter()
			intentFilter.addAction(Constants.ACTION_INCOMING_CALL)
			intentFilter.addAction(Constants.ACTION_INCOMING_CALL_NOTIFICATION)
			intentFilter.addAction(Constants.ACTION_CANCEL_CALL)
			intentFilter.addAction(Constants.ACTION_FCM_TOKEN)
			LocalBroadcastManager.getInstance(context)
					.registerReceiver(voiceBroadcastReceiver, intentFilter)
			isReceiverRegistered = true
		}
	}

	private fun unregisterReceiver() {
		if (isReceiverRegistered) {
			LocalBroadcastManager.getInstance(context).unregisterReceiver(voiceBroadcastReceiver)
			isReceiverRegistered = false
		}
	}

	private fun handleCancel() {
		val alertDialog = this.alertDialog
		if (alertDialog != null && alertDialog.isShowing) {
			callOutgoing = false
			SoundPoolManager.getInstance(context)?.stopRinging()
			alertDialog.cancel()
		}
	}

	private fun ringingEventMap(call: Call): Map<String, Any?> {
		return hashMapOf(
				"name" to "ringing",
				"data" to hashMapOf(
						"call" to callToMap(call)
				)
		)
	}

	private fun connectFailureEventMap(call: Call, callException: CallException): Map<String, Any?> {
		return hashMapOf(
				"name" to "connectFailure",
				"data" to hashMapOf(
						"call" to callToMap(call),
						"exception" to callExceptionToMap(callException)
				)
		)
	}

	private fun connectedEventMap(call: Call): Map<String, Any?> {
		return hashMapOf(
				"name" to "connected",
				"data" to hashMapOf(
						"call" to callToMap(call)
				)
		)
	}

	private fun reconnectingEventMap(call: Call, callException: CallException): Map<String, Any?> {
		return hashMapOf(
				"name" to "reconnecting",
				"data" to hashMapOf(
						"call" to callToMap(call),
						"exception" to callExceptionToMap(callException)
				)
		)
	}

	private fun reconnectedEventMap(call: Call): Map<String, Any?> {
		return hashMapOf(
				"name" to "reconnected",
				"data" to hashMapOf(
						"call" to callToMap(call)
				)
		)
	}

	private fun disconnectedEventMap(call: Call, callException: CallException?): Map<String, Any?> {
		var exceptionMap: Map<String, Any?>? = null
		if (callException != null) {
			exceptionMap = callExceptionToMap(callException)
		}
		return hashMapOf(
				"name" to "disconnected",
				"data" to hashMapOf(
						"call" to callToMap(call),
						"exception" to exceptionMap
				)
		)
	}

	private fun onHoldEventMap(call: Call): Map<String, Any?> {
		return hashMapOf(
				"name" to "onHold",
				"data" to hashMapOf(
						"call" to callToMap(call)
				)
		)
	}

	private fun mutedEventMap(call: Call): Map<String, Any?> {
		return hashMapOf(
				"name" to "muted",
				"data" to hashMapOf(
						"call" to callToMap(call)
				)
		)
	}

	private fun callToMap(call: Call): Map<String, Any?> {
		return hashMapOf(
				"from" to call.from,
				"to" to call.to,
				"direction" to if (callOutgoing) "outgoing" else "incoming",
				"isOnHold" to call.isOnHold,
				"isMuted" to call.isMuted
		)
	}

	private fun callExceptionToMap(error: CallException): Map<String, Any?> {
		return hashMapOf(
				"errorCode" to error.errorCode,
				"errorMessage" to error.message
		)
	}

	private fun isAppVisible(): Boolean {
		return ProcessLifecycleOwner
				.get()
				.lifecycle
				.currentState
				.isAtLeast(Lifecycle.State.STARTED)
	}

	private fun checkPermissionForMicrophone(): Boolean {
		val resultMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
		return resultMic == PackageManager.PERMISSION_GRANTED
	}

	private fun requestPermissionForMicrophone() {
		val activity = this.activity
		if (activity != null) {
			ActivityCompat.requestPermissions(
					activity,
					arrayOf(Manifest.permission.RECORD_AUDIO),
					MIC_PERMISSION_REQUEST_CODE
			)
		}
	}

	private fun createIncomingCallDialog(callInvite: CallInvite): AlertDialog {
		val alertDialogBuilder = AlertDialog.Builder(context)
		alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp)
		alertDialogBuilder.setTitle("Incoming Call")
		alertDialogBuilder.setPositiveButton("Accept") { _: DialogInterface?, _: Int ->
			Log.d(TAG, "Clicked accept")
			val acceptIntent = Intent(context, IncomingCallNotificationService::class.java)
			acceptIntent.action = Constants.ACTION_ACCEPT
			acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite)
			acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, activeCallNotificationId)
			context.startService(acceptIntent)
		}
		alertDialogBuilder.setNegativeButton("Reject") { _: DialogInterface?, _: Int ->
			Log.d(TAG, "Clicked reject")
			SoundPoolManager.getInstance(context)?.stopRinging()
			if (activeCallInvite != null) {
				val intent = Intent(context, IncomingCallNotificationService::class.java)
				intent.action = Constants.ACTION_REJECT
				intent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite)
				context.startService(intent)
			}

			val alertDialog = this.alertDialog
			if (alertDialog != null && alertDialog.isShowing) {
				alertDialog.dismiss()
			}
		}
		alertDialogBuilder.setMessage(callInvite.from + " is calling with " + callInvite.callerInfo.isVerified + " status")
		return alertDialogBuilder.create()
	}

	private class VoiceBroadcastReceiver internal constructor(
			private val plugin: TwilioVoicePlugin
	) : BroadcastReceiver() {

		override fun onReceive(context: Context, intent: Intent) {
			val action = intent.action
			Log.d(TAG, "Received broadcast for action $action")
			if (action != null
					&& (action == Constants.ACTION_INCOMING_CALL
							|| action == Constants.ACTION_CANCEL_CALL
							|| action == Constants.ACTION_INCOMING_CALL_NOTIFICATION)) {
				/*
				 * Handle the incoming or cancelled call invite
				 */
				plugin.handleIncomingCallIntent(intent)
			}
		}
	}
}