@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.1"
set "ANDROID_HOME=D:\software\android-sdk"
set "ANDROID_SDK_ROOT=D:\software\android-sdk"
set "GRADLE_HOME=E:\tmp\lock\.gradle-bootstrap\gradle-8.11.1"

echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%

echo.
echo === Step 1: Generate Gradle Wrapper ===
call "%GRADLE_HOME%\bin\gradle.bat" wrapper --gradle-version 8.11.1 --no-daemon
if %ERRORLEVEL% neq 0 (
    echo Wrapper generation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo === Step 2: Build Debug APK ===
call gradlew.bat assembleDebug --no-daemon --stacktrace
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo.
echo === Build Successful! ===
dir /s /b app\build\outputs\apk\debug\*.apk
