# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable

# SLF4J (Ktor/Supabase)
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# Supabase / Ktor / kotlinx.serialization
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class helium314.keyboard.latin.dogakdogak.**$$serializer { *; }
-keepclassmembers class helium314.keyboard.latin.dogakdogak.** {
    *** Companion;
}
-keepclasseswithmembers class helium314.keyboard.latin.dogakdogak.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
