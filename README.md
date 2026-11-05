![](.github/readme/banner.svg)

# Biometric SDK Android example

A small Android app that shows the [Iriscan Biometric SDK](https://github.com/biometric-technologies/biometric-sdk) at work: enroll a person by face or by iris, and search for them among everyone enrolled on the device. Enrollment searches first, so a second enrollment of the same person is flagged; that is de-duplication. Everything runs on the phone or tablet, with no server.

## What it does

- **Face.** Take a photo with the camera or choose one from the gallery. The SDK finds the face (ML Kit), aligns it and turns it into a 128-value embedding with the FaceNet model in `app/src/main/assets/facenet.tflite`. Two faces match when the distance between their embeddings is below 1.0.
- **Iris.** Scan an eye with a [Biomatiques EPI-1000](https://www.iriscan.com/iris-scanners) USB scanner on an OTG cable, or choose a near-infrared eye image. The scanner is driven by the maker's "Biomatiques Iris Capture" app, which this app calls with an intent and which returns the image; the SDK segments the iris and encodes it.
- **Enroll** asks for a name after checking that the person is not enrolled already, and keeps the template (not the photo) in the app's private storage. **Search** shows the closest enrolled person and whether the score passes the threshold.

The code is three files in `app/src/main/kotlin/net/iriscan/example/`: `Biometrics.kt` (SDK setup, encoding, 1:N search), `Gallery.kt` (the enrolled templates) and `MainActivity.kt` (the screen).

## Run it

Download the APK from the latest run of the [Build workflow](../../actions/workflows/build.yml) (artifact `iriscan-sdk-demo`) and install it, or build it:

```sh
git clone https://github.com/biometric-technologies/biometric-sdk-android-example
cd biometric-sdk-android-example
# the SDK source, until version 0.7.0 is on Maven Central
git clone --branch iris-v2 https://github.com/biometric-technologies/biometric-sdk sdk
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

With a `sdk/` checkout the app is built against that source (`settings.gradle.kts` includes it); without one Gradle takes `net.iriscan:biometric-sdk:0.7.0` from Maven Central. JDK 17 and the Android SDK (API 35) are needed; Android Studio provides both.

For iris with a Biomatiques scanner, install the maker's capture app on the device first and connect the scanner with an OTG cable. Other scanners (IriTech IriShield, Mantra) work with their makers' Android SDKs in the same way: capture an image, then call `Biometrics.encode`.

## Related repositories

- [biometric-sdk](https://github.com/biometric-technologies/biometric-sdk): the SDK itself (Android, iOS, JVM).
- [biometric-sdk-ios-example](https://github.com/biometric-technologies/biometric-sdk-ios-example): face demo in SwiftUI.
- [biometric-sdk-react-native](https://github.com/biometric-technologies/biometric-sdk-react-native): React Native wrapper.
- [docs.iriscan.com](https://docs.iriscan.com/sdk): developer documentation.

## License

GPL-3.0, like the Biometric SDK. A commercial license for closed-source products is available: https://www.iriscan.com/commercial-license.

## Contact

info@iriscan.com, https://www.iriscan.com
