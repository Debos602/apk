# Keep the SMS receiver and worker (referenced from the manifest / WorkManager).
-keep class com.freedev.smsotpforwarder.SmsReceiver { *; }
-keep class com.freedev.smsotpforwarder.ForwardWorker { *; }
