plugins { id("com.android.application") }
android {
 namespace = "com.mediadownloader.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.mediadownloader.app"; minSdk = 26; targetSdk = 28; versionCode = 3; versionName = "0.3" }
}
dependencies {
 implementation("com.github.yausername.youtubedl-android:library:0.18.1")
 implementation("com.github.yausername.youtubedl-android:ffmpeg:0.18.1")
}
