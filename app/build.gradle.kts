plugins {
    id("com.android.application")
}

android {
    namespace = "com.rallydash.wireless"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rallydash.wireless"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
