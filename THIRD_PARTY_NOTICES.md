# Third-Party Notices

Aladin CCTV includes open-source libraries. Distribution must retain the license terms and notices supplied by those projects and by the packaged artifacts.

| Component | Project | License |
|---|---|---|
| LibVLC for Android | https://code.videolan.org/videolan/libvlc-android-samples | LGPL-2.1-or-later |
| AndroidX libraries | https://github.com/androidx/androidx | Apache-2.0 |
| Material Components for Android | https://github.com/material-components/material-components-android | Apache-2.0 |
| Kotlin and kotlinx.serialization | https://github.com/JetBrains/kotlin | Apache-2.0 |
| OkHttp | https://github.com/square/okhttp | Apache-2.0 |

The exact dependency versions used by a release are defined in `app/build.gradle.kts` and `gradle/libs.versions.toml`. Before distribution, archive the dependency report and verify the license files embedded in the final artifacts. This notice does not replace the complete license text shipped by each dependency.
