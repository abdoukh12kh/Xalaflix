plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.lagradost.cloudstream3.gradle")
}
cloudstream {
    language = "en"
    version = 1          // ← Must be an integer!
    description = "Xalaflix Plugin"
    authors = listOf("YourName")
}
