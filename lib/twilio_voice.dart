import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

enum CallState { ringing, connected, callEnded, unhold, hold, unmute, mute, speakerOn, speakerOff }
enum CallDirection { incoming, outgoing }

class TwilioCall {

  final CallState state;
  final CallDirection direction;

  TwilioCall(this.state, this.direction);
}

class TwilioVoice {

  static const String _methodChannelName = 'twilio_voice/methods';
  static const String _eventChannelName = 'twilio_voice/events';

  static const MethodChannel _channel = MethodChannel(_methodChannelName);
  static const EventChannel _eventChannel = EventChannel(_eventChannelName);

  static Stream<CallState> _onCallStateChanged;
  static String _callFrom;
  static String _callTo;
  static int _callStartedOn;
  static CallDirection _callDirection = CallDirection.incoming;

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Stream<CallState> get onCallStateChanged {
    if (_onCallStateChanged == null) {
      _onCallStateChanged = _eventChannel.receiveBroadcastStream()
          .map((dynamic event) => _parseCallState(event));
    }
    return _onCallStateChanged;
  }

  static Future<bool> register({@required String accessToken, @required String fcmToken}) {
    assert(accessToken != null);
    assert(fcmToken != null);

    try {
      return _channel.invokeMethod(
        'register',
        {
          'accessToken': accessToken,
          'fcmToken': fcmToken,
        },
      );
    } on PlatformException catch(error, stackTrace) {
      print(error);
      print(stackTrace);
    }
    return Future.value(false);
  }

  static Future<bool> unregister() {
    try {
      return _channel.invokeMethod('unregister');
    } on PlatformException catch(error, stackTrace) {
      print(error);
      print(stackTrace);
    }
    return Future.value(false);
  }

  static Future<bool> makeCall({@required String from, @required String to, String toDisplayName, Map<String, dynamic> extraOptions}) {
    assert(to != null);
    assert(from != null);

    var options = extraOptions != null
        ? extraOptions
        : Map<String, dynamic>();
    options['from'] = from;
    options['to'] = to;
    if(toDisplayName != null) {
      options['toDisplayName'] = toDisplayName;
    }

    _callFrom = from;
    _callTo = to;
    _callDirection = CallDirection.outgoing;

    try {
      return _channel.invokeMethod('makeCall', options);
    } on PlatformException catch(error, stackTrace) {
      print(error);
      print(stackTrace);
    }
    return Future.value(false);
  }

  static Future<bool> hangUp() {
    return _channel.invokeMethod('hangUp');
  }

  static Future<bool> answer() {
    return _channel.invokeMethod('answer');
  }

  static Future<bool> holdCall() {
    return _channel.invokeMethod('holdCall');
  }

  static Future<bool> muteCall() {
    return _channel.invokeMethod('muteCall');
  }

  static Future<bool> toggleSpeaker(bool speakerIsOn) {
    assert(speakerIsOn != null);
    return _channel.invokeMethod('toggleSpeaker', {
      'speakerIsOn': speakerIsOn
    });
  }

  static Future<bool> sendDigits(String digits) {
    assert(digits != null);
    return _channel.invokeMethod('sendDigits', {
      'digits': digits,
    });
  }

  static Future<bool> isOnCall() {
    return _channel.invokeMethod('isOnCall');
  }

  static String getFrom() {
    return _callFrom;
  }

  static String getTo() {
    return _callTo;
  }

  static int getCallStartedOn() {
    return _callStartedOn;
  }

  static CallDirection getCallDirection() {
    return _callDirection;
  }

  static CallState _parseCallState(String state) {
    if (state.startsWith('Connected|')) {
      List<String> tokens = state.split('|');
      _callFrom = _prettyPrintNumber(tokens[1]);
      _callTo = _prettyPrintNumber(tokens[2]);
      _callDirection = ('Incoming' == tokens[3] ? CallDirection.incoming : CallDirection.outgoing);
      if (_callStartedOn == null) {
        _callStartedOn = DateTime.now().millisecondsSinceEpoch;
      }
      print('Connected - From: $_callFrom, To: $_callTo, StartOn: $_callStartedOn, Direction: $_callDirection');
      return CallState.connected;
    } else if (state.startsWith('Ringing|')) {
      List<String> tokens = state.split('|');
      _callFrom = _prettyPrintNumber(tokens[1]);
      _callTo = _prettyPrintNumber(tokens[2]);
      _callDirection = ('Incoming' == tokens[3] ? CallDirection.incoming : CallDirection.outgoing);
      _callStartedOn = DateTime.now().millisecondsSinceEpoch;
      print('Ringing - From: $_callFrom, To: $_callTo, StartOn: $_callStartedOn, Direction: $_callDirection');
      return CallState.ringing;
    }
    switch (state) {
      case 'Ringing':
        return CallState.ringing;
      case 'Connected':
        return CallState.connected;
      case 'Call Ended':
        _callStartedOn = null;
        _callFrom = null;
        _callTo = null;
        _callDirection = CallDirection.incoming;
        return CallState.callEnded;
      case 'Unhold':
        return CallState.unhold;
      case 'Hold':
        return CallState.hold;
      case 'Unmute':
        return CallState.unmute;
      case 'Mute':
        return CallState.mute;
      case 'Speaker On':
        return CallState.speakerOn;
      case 'Speaker Off':
        return CallState.speakerOff;
      default:
        print('$state is not a valid CallState.');
        throw ArgumentError('$state is not a valid CallState.');
    }
  }

  static String _prettyPrintNumber(String phoneNumber) {
    if (phoneNumber.indexOf('client:') > -1) {
      return phoneNumber.split(':')[1];
    }
    if (phoneNumber.substring(0, 1) == '+') {
      phoneNumber = phoneNumber.substring(1);
    }
    if (phoneNumber.length == 7) {
      return phoneNumber.substring(0, 3) + '-' + phoneNumber.substring(3);
    }
    if (phoneNumber.length < 10) {
      return phoneNumber;
    }
    int start = 0;
    if (phoneNumber.length == 11) {
      start = 1;
    }
    return '(${phoneNumber.substring(start, start + 3)})'
        ' ${phoneNumber.substring(start + 3, start + 6)}'
        '-${phoneNumber.substring(start + 6)}';
  }
}