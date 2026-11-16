.PHONY: dev test lint android phone clean

## Run the desktop app
dev:
	./gradlew :composeApp:run

## Run all tests (common + desktop)
test:
	./gradlew :composeApp:desktopTest

## Lint Kotlin sources with ktlint
lint:
	./gradlew ktlintCheck

## Auto-format Kotlin sources
format:
	./gradlew ktlintFormat

## Build the Android debug APK (needs an Android SDK; see README)
android:
	./gradlew :composeApp:assembleDebug

## Build the fast (R8-optimized) release APK and install it on the connected phone
phone:
	./gradlew :composeApp:assembleRelease
	adb install -r composeApp/build/outputs/apk/release/composeApp-release.apk

clean:
	./gradlew clean
