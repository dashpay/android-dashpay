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
- To use it with gradle, add `mavenLocal()` to the `repositories` list in your `build.gradle` file and add `org.dashevo:dashpay:0.16-SNAPSHOT` and `org.dashevo:platform-core:0.16-SNAPSHOT` and as dependency. 
- What to include in your build.gradle:
```
    implementation "org.dashevo:dpp:0.16-SNAPSHOT"
    implementation "org.dashevo:dapi-client:0.16-SNAPSHOT"
    implementation "org.dashj:dashj-core:0.18-SNAPSHOT"
    implementation "org.dashj:dashj-bls:0.18-SNAPSHOT"
    implementation "org.dashevo:platform-core:0.16-SNAPSHOT"
    implementation "org.dashevo:dashpay:0.16-SNAPSHOT" # if dashpay contract is required
```
# Tests
Run tests with `gradle build test`

# TODO
- Publish to jcenter/maven central
