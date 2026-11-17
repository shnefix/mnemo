.PHONY: dev test lint android phone phone-test clean

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

APK := composeApp/build/outputs/apk/release/composeApp-release.apk

## Build the fast (R8-optimized) release APK and install it on the connected phone.
## `make phone SIGN=debug` signs with the debug key instead of the release key, to update a
## copy that was installed with the debug key (switching keys means uninstalling, which
## wipes the app's data: export a backup from Settings first).
phone:
	./gradlew :composeApp:assembleRelease $(if $(SIGN),-Pmnemo.signing=$(SIGN))
	@out=$$(adb install -r $(APK) 2>&1); echo "$$out"; case "$$out" in \
	  *Success*) ;; \
	  *UPDATE_INCOMPATIBLE*) echo; echo "The phone's copy is signed with a different key."; \
	    echo "Run 'make phone SIGN=debug' to keep it, or back up, uninstall, reinstall and restore."; exit 1 ;; \
	  *) exit 1 ;; \
	esac

## Run the on-device tests on the phone without uninstalling the app (and its data) afterwards.
## They install the slow debug build; run `make phone` after to put the fast build back.
phone-test:
	./gradlew :composeApp:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
	-adb uninstall app.mnemo.test

clean:
	./gradlew clean
