# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson stores profiles and sends native-core configurations using DTO field names.
# Preserve the released JSON schema, including unannotated fields and enum values.
-keep,allowoptimization class com.v2ray.ang.dto.** { *; }
-keep,allowoptimization enum com.v2ray.ang.enums.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Gomobile resolves its Java bridge and callback interfaces through JNI.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class * implements libv2ray.CoreCallbackHandler { *; }
-keep class * implements libv2ray.ProcessFinder { *; }

# HEV exports JNI symbols with this class and these method names.
-keep class com.v2ray.ang.service.TProxyService { *; }
-keep class com.v2ray.ang.service.TProxyService$Companion { *; }
