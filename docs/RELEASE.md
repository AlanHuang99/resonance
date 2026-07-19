# Release Process

Resonance has two coordinated distribution paths:

- GitHub Releases host APKs signed with the project's release key.
- F-Droid rebuilds the tagged source, verifies it against the GitHub APK through the `Binaries` directive, checks the expected developer signing key with `AllowedAPKSigningKeys`, and publishes the verified developer-signed APK.

Keep the release inputs aligned. Do not commit keystores, generated APKs, local signing files, or F-Droid build output. F-Droid's reproducible-build flow is documented at [f-droid.org/docs/Reproducible_Builds](https://f-droid.org/docs/Reproducible_Builds/).

## GitHub Actions secrets

Configure these repository secrets before publishing a GitHub release:

- `SIGNING_KEYSTORE_BASE64`: Base64-encoded release keystore file.
- `SIGNING_STORE_PASSWORD`: Keystore password.
- `SIGNING_KEY_ALIAS`: Release key alias.
- `SIGNING_KEY_PASSWORD`: Release key password.

The release workflow decodes the keystore into the runner's temporary directory. Gradle only uses the release signing config when the keystore path is present, so normal source builds remain unsigned and do not require secrets.

## CI

Every push and pull request to `master` runs:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --stacktrace
```

The CI workflow uploads debug APKs and verification reports as short-lived artifacts. It does not publish a release.

## Prepare a release

1. Increment `versionCode` and set `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` with no more than 500 characters.
3. Add the same release to `CHANGELOG.md` and refresh README or store features only when user-facing behavior changed.
4. Run a clean local gate: `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease --stacktrace`.
5. Commit the complete release preparation, then create a `v<versionName>` tag on that commit.
6. Push the release commit and tag only after receiving explicit approval to publish.

The tag workflow verifies that the tag matches `versionName`, requires every signing secret, builds from a fresh checkout, publishes `resonance-v<versionName>.apk` and `SHA256SUMS.txt`, and leaves the release key material only in the runner's temporary directory.

## F-Droid metadata

The repository mirror of the accepted recipe is `metadata/com.resonance.music.yml`. The canonical copy is maintained in [fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.resonance.music.yml).

`AutoUpdateMode: Version` with `UpdateCheckMode: Tags` lets F-Droid discover later version tags. After F-Droid adds a release, sync this repository mirror from the canonical file rather than guessing a commit hash or editing ahead of the bot.
