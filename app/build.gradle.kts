plugins { id("com.android.application") }
android {
 namespace = "com.mediadownloader.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.mediadownloader.app"; minSdk = 26; targetSdk = 28; versionCode = 10; versionName = "1.0" }
}
dependencies {
 implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
 implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
