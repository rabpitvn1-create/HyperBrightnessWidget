# Local AI Relay — MVP 0.1.0

Android local-first relay. Source implementation of an offline workflow engine, Mock Connector, and manual web handoff for ChatGPT, Gemini and Claude. See [target architecture](docs/ARCHITECTURE.md) for the larger design, not all of which is implemented.

## Model selection
Every step stores a Provider + model ID independently. Enter the exact model name available in your account; previously used IDs become suggestions for that provider. Select the real model in the provider website and confirm it before accepting a response. The app cannot verify the actual web model, does not scrape accounts, and does not auto-switch accounts or circumvent limits. Mock has three explicit offline models; it echoes prompts for testing and is not an AI.

## How to use
Select four providers/models and instructions, enter a question, and start review mode. Review the generated prompt, copy it, open the provider website in your normal browser, select the model, submit manually, then paste the complete response. Repeat per step. An all-Mock workflow can also run automatically. The current workflow and exact approved request are stored privately in an atomic checkpoint; starting a new workflow overwrites the old one. No API keys, browser cookies, or credentials are stored. The app does not have Internet permission. Clipboard data is shared with Android and should not contain secrets.

## Build and test
Requires JDK 17, Gradle 8.10.2 and Android SDK platform 35. No wrapper is bundled.
- gradle :core:relayCheck
- gradle :app:assembleDebug
- adb install app/build/outputs/apk/debug/app-debug.apk

Alternative offline JVM check:
kotlinc core/src/main/kotlin/relay/core/Relay.kt core/src/test/kotlin/relay/core/RelayTest.kt -include-runtime -d relay-tests.jar
java -jar relay-tests.jar

Observed locally: Kotlin/JVM 1.9.0 and JDK 21 compiled and ran four groups of checks: four-step model identity/context; serialized checkpoint recovery, quota pause, duplicate and mismatched reply rejection, manual reroute; cancellation; deterministic Mock crash recovery. Android SDK, Gradle, emulator, device, and live provider credentials were unavailable. No Android APK or live provider test is claimed.

## Limitations vs target architecture
MVP uses native Android Views and a versioned private AtomicFile checkpoint with Java serialization (not Room/Compose). Single current workflow only; no long-term history, encrypted storage, schema migration, live connector model discovery, real API integration, automatic web transfer, streaming, background runner, full reconciliation/error taxonomy or automatic quota sensing. The only automatic connector is Mock. The connector interface is a reduced synchronous prototype, not the full v0.1 target contract. Future changes to checkpoint schema require migration. No CI/release workflows were changed or added.
