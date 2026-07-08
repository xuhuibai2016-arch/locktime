@echo off
set "JAVA_HOME=D:\Program Files\Android\Android Studio\jbr"
set "ANDROID_HOME=D:\software\android-sdk"
echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%
"%JAVA_HOME%\bin\java.exe" -version
echo.
echo === Building TimeLock ===
call "E:\tmp\lock\gradlew.bat" assembleDebug --no-daemon
