version = 1

dependencies {
    // JS motoru: Rhino, Android'de interpreted modda çalışır.
    implementation("org.mozilla:rhino:1.7.15")
}

cloudstream {
    description = "Nuvio JS provider repolarını Cloudstream içinde çalıştıran köprü. Ayarlardan (dişli) repo eklenir."
    authors = listOf("Nuvio Bridge")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    language = "en"
    iconUrl = "https://www.themoviedb.org/favicon.ico"
}

android {
    namespace = "com.example.nuviobridge"
}
