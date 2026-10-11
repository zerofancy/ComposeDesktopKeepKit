# JNI exported symbols encode bridge class and method names. Unused methods may shrink.
-keepclasseswithmembernames,includedescriptorclasses class com.llamatik.library.platform.*Bridge {
    native <methods>;
}
# JNI GetMethodID resolves these callbacks on the actual callback object.
-keepnames interface com.llamatik.library.platform.GenStream
-keepclassmembers class * implements com.llamatik.library.platform.GenStream {
    public void onDelta(java.lang.String);
    public void onComplete();
    public void onError(java.lang.String);
}
