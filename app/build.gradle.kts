import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// Release signing. Credentials are NEVER committed. They are read from, in order:
//   1. a properties file: -PkeystoreProperties=/path/file, or env KEYSTORE_PROPERTIES, or ./keystore.properties
//      (keys: storeFile, storePassword, keyAlias, keyPassword; see SIGNING.md)
//   2. env vars: RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
// If nothing is found, release builds are produced unsigned and debug/CI builds are unaffected.
val signingProps: Map<String, String>? = run {
    val path = (findProperty("keystoreProperties") as String?) ?: System.getenv("KEYSTORE_PROPERTIES")
    val file = path?.let { file(it) } ?: rootProject.file("keystore.properties")
    if (file.isFile) {
        val p = Properties().apply { file.inputStream().use { load(it) } }
        val store = p.getProperty("storeFile")?.let { f -> if (File(f).isAbsolute) f else file.parentFile.resolve(f).path }
        mapOf("storeFile" to store, "storePassword" to p.getProperty("storePassword"),
            "keyAlias" to p.getProperty("keyAlias"), "keyPassword" to p.getProperty("keyPassword"))
            .filterValues { it != null }.mapValues { it.value!! }
    } else mapOf(
        "storeFile" to System.getenv("RELEASE_STORE_FILE"), "storePassword" to System.getenv("RELEASE_STORE_PASSWORD"),
        "keyAlias" to System.getenv("RELEASE_KEY_ALIAS"), "keyPassword" to System.getenv("RELEASE_KEY_PASSWORD"))
        .filterValues { !it.isNullOrBlank() }.mapValues { it.value!! }
}.takeIf { it.size == 4 && File(it.getValue("storeFile")).isFile }

android {
    namespace = "com.drmagic.ringtonerecorder"
    compileSdk = 36
    defaultConfig { applicationId = "com.drmagic.ringtonerecorder"; minSdk = 29; targetSdk = 36; versionCode = 3; versionName = "1.2" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (signingProps != null) create("release") {
            storeFile = file(signingProps.getValue("storeFile"))
            storePassword = signingProps.getValue("storePassword")
            keyAlias = signingProps.getValue("keyAlias")
            keyPassword = signingProps.getValue("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}
if (signingProps == null) logger.lifecycle("Release signing not configured (see SIGNING.md); release outputs will be unsigned.")
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
android { lint { abortOnError = true; warningsAsErrors = false; disable += setOf("ProtectedPermissions") } }
