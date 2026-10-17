dependencies {
    // Compose & Material 3 (Elegant Design & Dark Theme)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Database (For saving lists and caching dictionary)
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // JSON Parsing (For Makemeahanzi graphics.txt)
    implementation("com.google.code.gson:gson:2.10.1")
}
