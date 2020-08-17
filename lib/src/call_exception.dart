class CallException implements Exception {

  final int errorCode;
  final String errorMessage;

  CallException(this.errorCode, this.errorMessage);
}