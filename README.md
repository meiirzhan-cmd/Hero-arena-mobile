# Hero Arena for Android

The Hero Arena prototype wrapped as an Android app. The game is one HTML page
(`app/src/main/assets/index.html`) shown full screen in a WebView. Fonts are embedded, so it
runs offline. `MainActivity.java` adds the camera and gallery picker for fighter and power pictures.

This project has not been built or run yet. Expect to fix small things on the first build.

## Get an APK

**GitHub, no tools on your computer**

1. Create an empty GitHub repository and push this folder to its `main` branch.
2. Open the repository's Actions tab and wait for "Build APK" to finish.
3. Download `hero-arena-apk` from the run and install `app-debug.apk` on your phone.

**Android Studio**

1. Open this folder in Android Studio and let it sync.
2. Build > Build App Bundle(s) / APK(s) > Build APK(s).
3. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

**Command line** (JDK 17 and the Android SDK installed)

```
./gradlew assembleDebug
```

## Notes

- Package name: `com.heroarena.app`. Change `applicationId` and `namespace` in `app/build.gradle`
  before publishing.
- The debug APK is for testing on your own phone. A store release needs a release build signed
  with your own key.
- To update the game, replace `app/src/main/assets/index.html` and rebuild.
- Saved fighters are stored in the app's WebView storage on the phone.
