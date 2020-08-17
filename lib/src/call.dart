import 'dart:async';

import 'base_call_event.dart';
import 'call_event.dart';
import 'call_model.dart';
import 'twilio_voice.dart';

class Call {

  String _from;
  String _to;
  CallDirection _direction;
  bool _isOnHold;
  bool _isMuted;
  DateTime _startTime;
  DateTime _endTime;

  StreamSubscription<BaseCallEvent> _baseCallEventStream;
  final StreamController<CallEvent> _onCallEvent = StreamController<CallEvent>
      .broadcast();
  Stream<CallEvent> onCallEvent;

  Call() {
    _baseCallEventStream = TwilioVoice.instance.callStream()
        .listen(_handleCallEvents);
    onCallEvent = _onCallEvent.stream;
  }

  String get from => _from;

  String get to => _to;

  CallDirection get direction => _direction;

  bool get isOnHold => _isOnHold;

  bool get isMuted => _isMuted;

  DateTime get startTime => _startTime;

  DateTime get endTime => _endTime;

  void _handleCallEvents(BaseCallEvent event) {
    if (event is BaseSkippableCallEvent) {
      return;
    }
    _updateFromModel(event.callModel);

    if(event is BaseRinging) {
      _onCallEvent.add(Ringing(this));
    } else if(event is BaseConnectFailure) {
      _onCallEvent.add(ConnectFailure(this, event.exception));
      _unsubscribeCallEvents();
    } else if(event is BaseConnected) {
      _startTime = DateTime.now();
      _onCallEvent.add(Connected(this));
    } else if(event is BaseReconnecting) {
      _onCallEvent.add(Reconnecting(this, event.exception));
    } else if(event is BaseReconnected) {
      _onCallEvent.add(Reconnected(this));
    } else if(event is BaseMuted) {
      _onCallEvent.add(Muted(this));
    } else if(event is BaseOnHold) {
      _onCallEvent.add(OnHold(this));
    } else if(event is BaseDisconnected) {
      _endTime = DateTime.now();
      _onCallEvent.add(Disconnected(this, event.exception));
      _unsubscribeCallEvents();
    } else if(event is BaseSkippableCallEvent) {
      _onCallEvent.add(SkippableCallEvent());
    } else {
      _onCallEvent.add(SkippableCallEvent());
    }
  }

  Future<void> disconnect() async {
    await TwilioVoice.instance.disconnect();
  }

  void _updateFromModel(CallModel callModel) {
    if(callModel != null) {
      _from = callModel.from;
      _to = callModel.to;
      _direction = callModel.direction;
      _isOnHold = callModel.isOnHold;
      _isMuted = callModel.isMuted;
    }
  }

  void _unsubscribeCallEvents() async {
    await _baseCallEventStream.cancel();
    await _onCallEvent.close();
  }
}