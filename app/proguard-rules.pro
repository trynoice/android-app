# crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# GSON rules https://github.com/google/gson/blob/master/examples/android-proguard-example/proguard.cfg
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation, already present in EventBus rules
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Since we only serialise/deserialise @Expose fields in Noice, keep all will with members annotated as such.
-keepclassmembers class * {
  @com.google.gson.annotations.Expose <fields>;
}

# Preserve names of all fragment classes for analytics
-keepnames class com.github.ashutoshgngwr.noice.fragment.*
