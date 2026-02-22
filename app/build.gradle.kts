plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)

    id("com.google.devtools.ksp")


}

android {
    namespace = "com.hrishipvt.scantopdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hrishipvt.scantopdf"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.storage.ktx)
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation(libs.localagents.rag)
    implementation(libs.firebase.ai)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    implementation("com.google.guava:guava:31.1-android")

    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
// Required to handle the XML structure of .docx
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")






    // Material 3
    implementation("com.google.android.material:material:1.11.0")



    // PDF
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.mlkit:text-recognition:16.0.0")

    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("com.github.gcacace:signature-pad:1.3.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("com.google.android.gms:play-services-auth:21.0.0")

    implementation("com.google.firebase:firebase-storage-ktx")

    implementation("com.google.firebase:firebase-functions-ktx:21.0.0")

    implementation("com.itextpdf:itextg:5.5.10")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.google.firebase:firebase-ai")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:${room_version}")

    // REPLACE kapt with ksp
    ksp("androidx.room:room-compiler:$room_version")

    implementation("androidx.gridlayout:gridlayout:1.0.0")





    // 🔒 Fix duplicate BouncyCastle classes












}
configurations.configureEach {
    exclude(group = "org.bouncycastle")
}


