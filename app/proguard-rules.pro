# MyMeditation Player ProGuard Rules

# Keep data models
-keep class com.mymeditation.player.DirectoryItem { *; }
-keep class com.mymeditation.player.FileItem { *; }
-keep class com.mymeditation.player.ThemeManager$ThemeColors { *; }

# Keep callback interfaces
-keepclassmembers interface com.mymeditation.player.MusicService$ProgressCallback { *; }
-keepclassmembers interface com.mymeditation.player.MusicService$TimerCallback { *; }
-keepclassmembers interface com.mymeditation.player.DirectoryAdapter$OnDirectoryClickListener { *; }
-keepclassmembers interface com.mymeditation.player.FileAdapter$OnFileClickListener { *; }

# MediaSessionCompat
-keep class android.support.v4.media.session.** { *; }
-keep class androidx.media.session.** { *; }

# Standard Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
