# Release signing — Ringtone Pick Your Sound

Google Play uses **Play App Signing**: you sign uploads with your own **upload key**, and Google re-signs
the app with the app signing key it keeps. This file explains how to make and protect the upload key.

> Never commit a keystore or its passwords. `*.jks`, `*.keystore` and `keystore.properties` are in `.gitignore`.

## 1. Create the upload keystore (once)
Run this **outside the repo folder** and replace the placeholders with your own strong passwords:

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload \
  -dname "CN=Dr Paul Magic, O=24 Hour Computers, L=Sooke, ST=British Columbia, C=CA" \
  -storepass 'CHANGE_ME_STORE_PASSWORD' \
  -keypass   'CHANGE_ME_STORE_PASSWORD'
```

- With PKCS12 keystores the key password must equal the store password.
- Leave out `-storepass`/`-keypass` to be prompted instead, so the password stays out of your shell history.
- Validity of 10,000 days (about 27 years) is what Play expects (it must run past 22 Oct 2033).

## 2. Point the build at it
Create `keystore.properties` in the repo root (it is git-ignored) **or anywhere else**:

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=CHANGE_ME_STORE_PASSWORD
keyAlias=upload
keyPassword=CHANGE_ME_STORE_PASSWORD
```

`app/build.gradle.kts` looks for signing details in this order:
1. `-PkeystoreProperties=/path/to/file.properties`, or the `KEYSTORE_PROPERTIES` env var, or `./keystore.properties`
2. env vars `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`

If none are found, the build still works: debug builds are unchanged and release builds come out **unsigned**
(Gradle prints “Release signing not configured”).

## 3. Build and check
```bash
./gradlew bundleRelease      # app/build/outputs/bundle/release/app-release.aab  -> upload this to Play
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk     -> for sideload testing
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

## 4. Keep it safe
- **Two offline backups** of the `.jks` file in separate places (e.g. an encrypted USB drive at home and one off-site).
- **A password manager entry** with the store password, key alias (`upload`), key password, and the certificate
  SHA-256 fingerprint (`keytool -list -v -keystore upload-keystore.jks`).
- Never commit it, email it, or paste it into chat or an issue. Don't keep the only copy on a laptop or CI runner.
- **Lost or leaked upload key?** With Play App Signing, Google keeps the real app signing key, so the account owner
  can request an **upload key reset** in Play Console (Test and release › App integrity › App signing) and register a
  new upload certificate. Your app and its users are not affected. (Without Play App Signing, a lost key means you
  can never update the app again, so keep Play App Signing turned on.)

## 5. Optional: sign in GitHub Actions
Only if you want CI to build signed bundles. The secrets stay encrypted in GitHub; nothing is committed.
1. `base64 -w0 upload-keystore.jks > upload-keystore.b64`
2. Repo › Settings › Secrets and variables › Actions › New repository secret:
   `RELEASE_KEYSTORE_BASE64` (contents of the .b64 file), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` (`upload`),
   `RELEASE_KEY_PASSWORD`. Then delete the `.b64` file.
3. Add `env: { HAS_KEY: ${{ secrets.RELEASE_KEYSTORE_BASE64 != '' }} }` to the job, then a step like:
   ```yaml
   - name: Build signed bundle
     # secrets can't be used directly in `if:`, so expose a flag through env first
     if: ${{ env.HAS_KEY == 'true' }}
     env:
       RELEASE_STORE_FILE: ${{ runner.temp }}/upload-keystore.jks
       RELEASE_STORE_PASSWORD: ${{ secrets.RELEASE_STORE_PASSWORD }}
       RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
       RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
     run: |
       echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 -d > "$RELEASE_STORE_FILE"
       ./gradlew --no-daemon bundleRelease
   ```
   Don't upload the signed `.aab` as a public artifact on a public repo; download it from a private place instead.
