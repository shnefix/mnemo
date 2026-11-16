.PHONY: dev test lint android clean

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

clean:
	./gradlew clean
