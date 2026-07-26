# Play Store Release Checklist — Aladin CCTV v1.3

## Artifact and signing

- Upload the signed `release` AAB; do not upload an APK to the production track.
- Enroll in Play App Signing and register the certificate from the upload key.
- Back up `D:\Development\Signing\aladin-cctv-upload-v1.jks` and its matching `.properties` file in two secure locations.
- Archive the AAB, `mapping.txt`, native debug symbols and SHA-256 hashes for every production release.

## App content declarations

- Privacy policy URL: publish `PRIVACY_POLICY.md` at a stable public HTTPS address.
- Ads: No, provided no advertising SDK is added.
- Data collection/sharing: No developer collection or sharing is implemented in v1.3. Re-evaluate if analytics, crash reporting, cloud sync or remote services are added.
- Account requirement/deletion: The app does not create developer-operated user accounts.
- App access: No developer login is required. Reviewers need cameras on the same LAN to exercise live RTSP/ONVIF functions; provide clear review notes and a demonstration video if requested.
- Target audience: utility/CCTV operators; not designed for children.
- Complete the content rating questionnaire honestly, including local camera/web-viewing functionality.

## Android TV listing

- Opt in to Android TV distribution.
- Upload at least one current 16:9 Android TV screenshot without development data or real camera credentials.
- Use the 320×180 TV banner included in the app and provide the required Play Store graphic assets.
- Verify all listing text, screenshots and privacy policy in both Turkish and English.
- Verify D-pad-only navigation, Back behavior, focus visibility and no touch-only dead ends.

## Test tracks

- Internal test: install from Play on the real TV and validate Play-delivered language/resources.
- Closed test: exercise fresh install and upgrade, credential re-entry after config import, DHCP recovery, PTZ and 6–12 hour playback.
- Review Play pre-launch, Android vitals, ANR and native crash reports before production.
- Start production with a staged rollout and stop rollout if crash/ANR or playback regressions appear.

## Mandatory manual items

- Add the developer support email and website in Play Console.
- Host the privacy policy at a stable public HTTPS URL.
- Store the upload key backup outside the development computer.
- Do not publish diagnostic logs, real LAN screenshots, IP/MAC/UUID values or credential-bearing configuration files.
