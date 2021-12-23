# DashPay library for JVM
This library consists of two components
- platform-core (similar to DashJS which handles identities, contracts, documents and names)
- dashpay (handles the dashpay contract documents: profiles and contractRequests)

# Building
This depends on the `android-dpp` and `dapi-client-android` libraries:
```
git clone https://github.com/dashevo/android-dpp.git
cd android-dpp
./gradlew assemble
cd ..
git clone https://github.com/dashevo/dapi-client-android.git
cd dapi-client-android
./gradlew assemble
cd ..
```
Finally, build the library:
```
git clone https://github.com/dashevo/android-dashpay.git`
cd android-dashpay`
./gradlew assemble`
```
- After building, it will be available on the local Maven repository.
- To use it with gradle, add `mavenLocal()` to the `repositories` list in your `build.gradle` file and add `org.dashj.platform:dashpay:0.22-SNAPSHOT` and `org.dashj.platform:platform-core:0.21-SNAPSHOT` and as dependency. 

# Usage
- Add mavenCentral() to your `repositories`
- What to include in your build.gradle:
```
dependencies {
    implementation "org.dashj.platform:dpp:0.22-SNAPSHOT"
    implementation "org.dashj.platform:dapi-client:0.22-SNAPSHOT"
    implementation "org.dashj:dashj-core:0.18-SNAPSHOT"
    implementation "org.dashj:dashj-bls:0.18-SNAPSHOT"
    implementation "org.dashj.platform:platform-core:0.22-SNAPSHOT"
    implementation "org.dashj.platform:dashpay:0.22-SNAPSHOT" # if dashpay contract is required
}
```
# Tests
Run tests with `gradle build test`

# Publish to maven central
```  
./gradlew uploadArchives
```

