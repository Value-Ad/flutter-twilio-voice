import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:twilio_voice/twilio_voice.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {

  final FirebaseMessaging _firebaseMessaging = FirebaseMessaging();
  String _platformVersion = 'Unknown';
  StreamSubscription<CallEvent> _streamSubscription;
  Call _call;

  @override
  void initState() {
    super.initState();
    _initTwilio();
  }

  Future<void> _initTwilio() async {
    String fcmToken = await _firebaseMessaging.getToken();

    try {
      TwilioVoice.instance.register(
        accessToken: 'accessToken',
        fcmToken: fcmToken,
      );
    } catch (error, stackTrace) {
      print(error);
      print(stackTrace);
    }
  }

  @override
  void dispose() {
    _streamSubscription.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Running on: $_platformVersion\n'),
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            if(_call == null) {
              _makeCall();
            } else {
              _hangUp();
            }
          },
        ),
      ),
    );
  }

  void _makeCall() async {
    _call = await TwilioVoice.instance.connect({
      'from': '+15005550006',
      'to': '+14108675310',
      'url': 'http://demo.twilio.com/docs/voice.xml',
    });
    _call.onCallEvent.listen((event) {
      print(event);
    });
  }

  void _hangUp() {
    _call?.disconnect();
    _call = null;
  }
}
