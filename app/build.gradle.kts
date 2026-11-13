plugins { id("com.android.application") }
android {
 namespace = "br.com.centralv720"
 compileSdk = 35
 defaultConfig { applicationId = "br.com.centralv720"; minSdk = 24; targetSdk = 28; versionCode = 3; versionName = "0.3" }
}

dependencies { implementation("com.journeyapps:zxing-android-embedded:4.3.0") }
