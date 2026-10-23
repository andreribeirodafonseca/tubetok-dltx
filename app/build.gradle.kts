plugins { id("com.android.application") }
android {
 namespace = "com.mediadownloader.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.mediadownloader.app"; minSdk = 26; targetSdk = 28; versionCode = 5; versionName = "0.5" }
}
dependencies {
 implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
 implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
