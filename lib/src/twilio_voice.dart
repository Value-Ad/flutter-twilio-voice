import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'base_call_event.dart';
import 'call.dart';
import 'call_exception.dart';
import 'call_model.dart';

class TwilioVoice {

  static const MethodChannel _methodChannel = MethodChannel('twilio_voice/methods');
  static final EventChannel _callEventChannel = EventChannel('twilio_voice/call');
  static final EventChannel _incomingCallEventChannel = EventChannel('twilio_voice/incoming_call');

  static TwilioVoice _instance = TwilioVoice._();

  static TwilioVoice get instance => _instance;

  Stream<BaseCallEvent> _callStream;
  Stream<Call> _incomingCallStream;

  TwilioVoice._();

  Stream<BaseCallEvent> callStream() {
    _callStream ??= _callEventChannel.receiveBroadcastStream()
        .map(_handleCallEvent);
    return _callStream;
  }

  Stream<Call> incomingCallStream() {
    _incomingCallStream ??= _incomingCallEventChannel.receiveBroadcastStream()
        .map(_handleIncomingCallEvent);
    return _incomingCallStream;
  }

  Future<bool> register({@required String accessToken, @required String fcmToken}) {
    assert(accessToken != null);
    assert(fcmToken != null);

    try {
      return _methodChannel.invokeMethod(
        'register',
        {
          'accessToken': accessToken,
          'fcmToken': fcmToken,
        },
      );
    } on PlatformException catch(error) {
      throw _convertException(error);
    }
  }

  Future<bool> unregister() {
    try {
      return _methodChannel.invokeMethod('unregister');
    } on PlatformException catch(error) {
      throw _convertException(error);
    }
  }

  Future<Call> connect(Map<String, String> options) async {
    assert(options != null);

    try {
      await _methodChannel.invokeMethod('connect', options);
      return Call();
    } on PlatformException catch (error) {
      throw _convertException(error);
    }
  }

  Future<bool> answer() {
    return _methodChannel.invokeMethod('answer');
  }

  Future<bool> holdCall() {
    return _methodChannel.invokeMethod('holdCall');
  }

  Future<bool> muteCall() {
    return _methodChannel.invokeMethod('muteCall');
  }

  Future<bool> sendDigits(String digits) {
    assert(digits != null);
    return _methodChannel.invokeMethod('sendDigits', {
      'digits': digits,
    });
  }

  Future<bool> disconnect() {
    return _methodChannel.invokeMethod('disconnect');
  }

  Future<bool> isOnCall() {
    return _methodChannel.invokeMethod('isOnCall');
  }

  BaseCallEvent _handleCallEvent(dynamic event) {
    final String eventName = event['name'];
    final data = Map<String, dynamic>.from(event['data']);

    if (data['call'] == null) {
      return const BaseSkippableCallEvent();
    }

    CallModel call = CallModel(
      from: data['from'],
      to: data['to'],
      direction: data['direction'] == 'incoming'
          ? CallDirection.incoming
          : CallDirection.outgoing,
      isOnHold: data['isOnHold'],
      isMuted: data['isMuted'],
    );

    CallException exception;
    if(data['exception'] != null) {
      exception = CallException(
        data['exception']['errorCode'],
        data['exception']['errorMessage'],
      );
    }

    switch (eventName) {
      case 'ringing':
        return BaseRinging(call);
      case 'connectFailure':
        return BaseConnectFailure(call, exception);
      case 'connected':
        return BaseConnected(call);
      case 'reconnecting':
        return BaseReconnecting(call, exception);
      case 'reconnected':
        return BaseReconnected(call);
      case 'muted':
        return BaseMuted(call);
      case 'onHold':
        return BaseOnHold(call);
      case 'disconnected':
        return BaseDisconnected(call, exception);
      default:
        return const BaseSkippableCallEvent();
    }
  }

  Call _handleIncomingCallEvent(dynamic event) {
    return Call();
  }

  static Exception _convertException(PlatformException error) {
    int code = int.tryParse(error.code);
    // If code is an integer, then it is a Twilio exception.
    if (code != null) {
      return CallException(code, error.message);
    }
    return error;
  }
}