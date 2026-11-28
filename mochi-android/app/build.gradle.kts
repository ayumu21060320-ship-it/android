plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mochimate.maid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mochimate.maid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // 個人用のお試しアプリなので、リポジトリに置いた固定のデバッグ鍵で署名する。
        // ビルドのたびに鍵が変わると、スマホ上で上書きインストールできなくなるため。
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        // 部品どうしで同じ名前の説明ファイルがぶつかるのをよける
        resources.excludes += setOf("META-INF/*.version", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST", "META-INF/*.kotlin_module")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // おうちに着いた・出た、を電池にやさしく見張る(ジオフェンス)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // リリの会話(Claude API)
    implementation("com.anthropic:anthropic-java:3.3.0")
    testImplementation("junit:junit:4.13.2")
}
