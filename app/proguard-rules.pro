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
-dontwarn javax.swing.tree.DefaultMutableTreeNode
-dontwarn javax.swing.tree.DefaultTreeModel
-dontwarn javax.swing.tree.MutableTreeNode
-dontwarn javax.swing.tree.TreeNode
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTIT2 { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTPE1 { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTALB { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTYER { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTDRC { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTCON { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTRCK { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTPOS { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyTSSE { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyAPIC { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT { <init>(...); }
-keep class org.jaudiotagger.tag.id3.framebody.FrameBodyUnsupported { <init>(...); }
-keep class org.jaudiotagger.audio.mp3.MP3File { *; }
-keep class org.jaudiotagger.audio.mp3.MP3AudioHeader { *; }
-keep class org.jaudiotagger.audio.opus.** { *; }
-dontwarn org.jaudiotagger.**