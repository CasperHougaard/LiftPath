# Building AAB for Google Play Console

This guide walks you through building an Android App Bundle (AAB) for uploading to Google Play Console.

## Prerequisites

- Android Studio and Android SDK installed
- Java Development Kit (JDK) 11 or higher
- Keystore file: `app/liftpath-release-key.jks`
- Keystore passwords and key alias

## Version Information

- **Version Name**: 1.00.001
- **Version Code**: 3
- **Certificate SHA1**: `C3:3C:A1:A8:48:EC:24:37:66:8B:B8:C4:73:9C:5F:4C:D3:21:0F:A7`

## Setup Signing Configuration

**IMPORTANT**: Signing is **REQUIRED** for Google Play Console. You cannot upload unsigned AAB files.

Before building the AAB, you **must** configure your keystore credentials. You have two options:

### Option 1: Create keystore.properties File (Recommended)

Create a file named `keystore.properties` in the project root directory with the following content:

```properties
storeFile=liftpath-release-key.jks
storePassword=your_keystore_password_here
keyAlias=your_key_alias_here
keyPassword=your_key_password_here
```

**Important**: 
- The `keystore.properties` file is excluded from version control (via `.gitignore`) to protect your credentials.
- If you don't know your key alias, you can check it using the helper script:
  ```powershell
  .\check-keystore.ps1 -KeystorePassword 'your_keystore_password'
  ```

### Option 2: Use Environment Variables

Instead of using a properties file, you can set environment variables:

- `KEYSTORE_PASSWORD`: Your keystore password
- `KEY_ALIAS`: Your key alias
- `KEY_PASSWORD`: Your key password

On Windows (PowerShell):
```powershell
$env:KEYSTORE_PASSWORD="your_keystore_password"
$env:KEY_ALIAS="your_key_alias"
$env:KEY_PASSWORD="your_key_password"
```

On Linux/Mac:
```bash
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="your_key_alias"
export KEY_PASSWORD="your_key_password"
```

## Building the AAB

### Method 1: Using the Build Script (Windows)

1. Open a terminal/command prompt in the project root
2. Run the build script:
   ```batch
   build-release.bat
   ```

The script will:
- Set the JAVA_HOME environment variable
- Build the release AAB bundle
- Display the output location

### Method 2: Using Gradle Directly

1. Open a terminal in the project root directory
2. Run the Gradle command:

**On Windows:**
```batch
gradlew.bat bundleRelease
```

**On Linux/Mac:**
```bash
./gradlew bundleRelease
```

## Output Location

After a successful build, your AAB file will be located at:

```
app/build/outputs/bundle/release/app-release.aab
```

## Verifying the Build

### Check Version Information

You can verify the version information of your AAB using the `bundletool`:

1. Download bundletool from: https://github.com/google/bundletool/releases
2. Extract the AAB to check its contents:
   ```bash
   bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab --output=app.apks --mode=universal
   unzip app.apks
   aapt dump badging universal.apk | grep -E "package|versionCode|versionName"
   ```

### Verify Certificate

To verify the certificate SHA1 fingerprint matches the expected value:

```bash
keytool -list -v -keystore app/liftpath-release-key.jks
```

Look for the SHA1 fingerprint in the output and verify it matches:
```
C3:3C:A1:A8:48:EC:24:37:66:8B:B8:C4:73:9C:5F:4C:D3:21:0F:A7
```

## Uploading to Google Play Console

1. **Sign in to Google Play Console**
   - Go to https://play.google.com/console
   - Select your app

2. **Navigate to Release Management**
   - Go to "Production" (or "Internal testing" / "Closed testing" / "Open testing")
   - Click "Create new release"

3. **Upload the AAB**
   - Drag and drop `app-release.aab` or click "Upload"
   - Wait for Google Play to process the bundle (this may take a few minutes)

4. **Review Release Details**
   - Verify the version name shows as "1.00.001"
   - Verify the version code shows as "3"
   - Review the app size and features

5. **Add Release Notes**
   - Fill in "What's new in this release" section
   - This information will be visible to users

6. **Review and Rollout**
   - Review all changes
   - Click "Review release"
   - If everything looks good, click "Start rollout to Production" (or appropriate testing track)

## Troubleshooting

### Build Fails with "Signing config not found"

- Ensure you've created `keystore.properties` with correct credentials, OR
- Set the required environment variables (KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

### Build Fails with "Keystore file not found"

- Verify that `app/liftpath-release-key.jks` exists
- Check that the path in `keystore.properties` (if using) is correct relative to the project root

### Build Fails with "Invalid keystore format"

- Ensure you're using the correct keystore file
- Verify the keystore file is not corrupted

### Wrong Version in AAB

- Check `app/build.gradle.kts` and verify:
  - `versionCode = 3`
  - `versionName = "1.00.001"`
- Clean and rebuild:
  ```batch
  gradlew.bat clean bundleRelease
  ```

### Certificate Mismatch

- If Google Play Console reports a certificate mismatch:
  - Verify you're using the correct keystore file
  - Check the SHA1 fingerprint matches the expected value
  - If this is your first upload, Google Play will accept the certificate
  - If this is an update, the certificate must match the previous upload

## Additional Notes

- The AAB format is Google Play's preferred format for app distribution
- AABs are smaller than APKs and allow Google Play to optimize downloads for each device
- Always test your release build before uploading to Google Play Console
- Keep your keystore file and passwords secure - if lost, you cannot update your app on Google Play

## Build Configuration Reference

The build configuration is located in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.liftpath"
    versionCode = 3
    versionName = "1.00.001"
    // ...
}

signingConfigs {
    create("release") {
        // Configured via keystore.properties or environment variables
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ...
    }
}
```

