import 'call_exception.dart';
import 'call_model.dart';

abstract class BaseCallEvent {

  final CallModel callModel;

  const BaseCallEvent(this.callModel);
}

class BaseRinging extends BaseCallEvent {

  BaseRinging(CallModel callModel) : super(callModel);
}

class BaseConnectFailure extends BaseCallEvent {

  final CallException exception;

  BaseConnectFailure(CallModel callModel, this.exception) : super(callModel);
}

class BaseConnected extends BaseCallEvent {

  BaseConnected(CallModel callModel) : super(callModel);
}

class BaseReconnecting extends BaseCallEvent {

  final CallException exception;

  BaseReconnecting(CallModel callModel, this.exception) : super(callModel);
}

class BaseReconnected extends BaseCallEvent {

  BaseReconnected(CallModel callModel) : super(callModel);
}

class BaseMuted extends BaseCallEvent {

  BaseMuted(CallModel callModel) : super(callModel);
}

class BaseOnHold extends BaseCallEvent {

  BaseOnHold(CallModel callModel) : super(callModel);
}

class BaseDisconnected extends BaseCallEvent {

  final CallException exception;

  BaseDisconnected(CallModel callModel, this.exception) : super(callModel);
}

class BaseSkippableCallEvent extends BaseCallEvent {
  const BaseSkippableCallEvent() : super(null);
}