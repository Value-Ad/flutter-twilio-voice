import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
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

  @override
  void initState() {
    super.initState();

    initPlatformState();
    _initTwilio();
  }

  Future<void> _initTwilio() async {
    TwilioVoice.onCallStateChanged.listen((event) {
      print(event);
    });

    String fcmToken = await _firebaseMessaging.getToken();
    TwilioVoice.register(
      accessToken: 'eyJjdHkiOiJ0d2lsaW8tZnBhO3Y9MSIsInR5cCI6IkpXVCIsImFsZyI6IkhTMjU2In0.eyJpc3MiOiJTSzE5Njg2YjlkYTI3MjFhMmM4MDAzODA1M2Q2YTdjMDljIiwiZXhwIjoxNTk3MTQ1NDcyLCJncmFudHMiOnsidm9pY2UiOnsiaW5jb21pbmciOm51bGwsIm91dGdvaW5nIjp7ImFwcGxpY2F0aW9uX3NpZCI6IkFQMjM1ZDdjYmE2MzllZjQxZWUxYzNlODJmYjFkMzQ3ZTQifSwicHVzaF9jcmVkZW50aWFsX3NpZCI6bnVsbCwiZW5kcG9pbnRfaWQiOm51bGx9LCJpZGVudGl0eSI6InVzZXIifSwianRpIjoiU0sxOTY4NmI5ZGEyNzIxYTJjODAwMzgwNTNkNmE3YzA5Yy0xNTk3MTQxNzYwIiwic3ViIjoiQUMwYTJiODhkYmUwMGMzNzNlYTUwNzg4MWVjMDllMmQwZCJ9.JUZLPasJDOFwOdg7iGeKIsBbQWYqUfEvgDlhD9vsk-U',
      fcmToken: fcmToken,
    );
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      platformVersion = await TwilioVoice.platformVersion;
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
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
        floatingActionButton: FloatingActionButton(onPressed: _makeCall),
      ),
    );
  }

  void _makeCall() {
    TwilioVoice.makeCall(
      from: '+15005550006',
      to: '+14108675310',
      extraOptions: {
        'url': 'http://demo.twilio.com/docs/voice.xml',
      },
    );
  }
}
