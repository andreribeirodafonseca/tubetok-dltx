plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.mediadownloader.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.mediadownloader.app"; minSdk = 26; targetSdk = 28; versionCode = 37; versionName = "2.17" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("com.google.mlkit:translate:17.0.3")
 implementation("com.google.mlkit:language-id:17.0.6")
 implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
 implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
