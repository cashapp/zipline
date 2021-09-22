# Type fully-qualified name is resolved from JNI code.
-keepnames class app.cash.zipline.MemoryUsage

# Type constructor is resolved from JNI code.
-keepclassmembers class app.cash.zipline.MemoryUsage {
  <init>(...);
}
