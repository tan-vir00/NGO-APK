@echo off
rem Convenience launcher (same behaviour as ./gradlew in this project).
rem The genuine Gradle wrapper JAR (gradle/wrapper/gradle-wrapper.jar) is NOT bundled, so this script only
rem forwards to a locally installed Gradle. To generate the real wrapper files for the pinned version, run once:
rem     gradle wrapper --gradle-version 8.9
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed and the standard wrapper JAR is not included. Open this project in Android Studio to sync/build, or install Gradle 8.9. 1>&2
exit /b 1
