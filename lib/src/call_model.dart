import 'package:flutter/foundation.dart';

enum CallDirection {
  incoming,
  outgoing,
}

class CallModel {

  final String from;
  final String to;
  final CallDirection direction;
  final bool isOnHold;
  final bool isMuted;

  CallModel({
    @required this.from,
    @required this.to,
    @required this.direction,
    @required this.isOnHold,
    @required this.isMuted,
  });
}