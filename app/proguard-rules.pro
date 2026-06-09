# Proguard rules for Dalyly App

# Keep our models, activities, managers, and all class structures intact to prevent reflection/serialization errors
-keep class com.dalyly.** { *; }
-keepclassmembers class com.dalyly.** { *; }

# Firebase Firestore keep rules
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

