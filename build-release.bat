@echo off
echo Building Android App Bundle for Google Play Console...
echo.

set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
call gradlew.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo BUILD SUCCESSFUL!
    echo ========================================
    echo.
    echo Your AAB file is located at:
    echo app\build\outputs\bundle\release\app-release.aab
    echo.
    echo You can now upload this file to Google Play Console for internal testing.
    echo.
) else (
    echo.
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    echo.
    echo Please check the error messages above.
    echo.
)

pause

