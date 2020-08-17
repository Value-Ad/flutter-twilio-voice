import 'call.dart';
import 'call_exception.dart';

abstract class CallEvent {

  final Call call;

  const CallEvent(this.call);
}

class Ringing extends CallEvent {

  Ringing(Call call) : super(call);
}

class ConnectFailure extends CallEvent {

  final CallException exception;

  ConnectFailure(Call call, this.exception) : super(call);
}

class Connected extends CallEvent {

  Connected(Call call) : super(call);
}

class Reconnecting extends CallEvent {

  final CallException exception;

  Reconnecting(Call call, this.exception) : super(call);
}

class Reconnected extends CallEvent {

  Reconnected(Call call) : super(call);
}

class Muted extends CallEvent {

  Muted(Call call) : super(call);
}

class OnHold extends CallEvent {

  OnHold(Call call) : super(call);
}

class Disconnected extends CallEvent {

  final CallException exception;

  Disconnected(Call call, this.exception) : super(call);
}

class SkippableCallEvent extends CallEvent {
  const SkippableCallEvent() : super(null);
}