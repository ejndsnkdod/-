# ProGuard rules
# Keep source file names and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Keep public API
-keep public class com.example.albumcleaner.** {
    public *;
}
