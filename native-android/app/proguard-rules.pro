# SQLCipher (JNI + reflection)
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**
-keepclasseswithmembernames class * { native <methods>; }
# Room entities/DAOs are generated; keep entity field names used by migrations/queries
-keep class in.paanagam.business.data.** { *; }
