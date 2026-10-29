plugins { id("com.android.application") }
android {
    namespace = "dev.project.monika.receiver"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.project.monika.receiver"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0-supabase"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation(project(":common")) }
