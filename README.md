# cinzeiro

App Android pessoal para registar cigarros com um toque e ver estatísticas.
Dados só no telemóvel (sem permissão `INTERNET`).

Plano e ADR vivem fora deste repositório (workspace do architect).

## Compilar

```bash
export JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug --console=plain > build.log 2>&1; echo EXIT=$?
./gradlew testDebugUnitTest --console=plain > build.log 2>&1; echo EXIT=$?
./gradlew --stop
```

`local.properties` (fora do git): `sdk.dir=/opt/android-sdk`.

## Toolchain (versões finais, verificadas no T0 em 2026-09-29)

| Peça | Versão |
|---|---|
| JDK do Gradle | JetBrains Runtime 21.0.10 (`/opt/android-studio/jbr`) — o OpenJDK 25-ea do sistema não é usado |
| Gradle wrapper | 9.8.0 (AGP 9.4 exige ≥ 9.6.0) |
| AGP | 9.4.1 (Kotlin integrado: **não** se aplica `org.jetbrains.kotlin.android`) |
| Kotlin (plugins compose/serialization) | 2.4.20 |
| KSP | 2.3.12 |
| kotlinx-serialization-json | 1.11.0 |
| Compose BOM | 2026.09.00 (exige compileSdk ≥ 37) |
| Room | 2.8.5 |
| Glance appwidget | 1.2.0 |
| Vico compose-m3 | 3.3.1 |
| WorkManager | 2.12.0 |
| Navigation Compose | 2.10.2 |
| Lifecycle | 2.11.0 |
| activity-compose | 1.13.0 |
| core-ktx | 1.19.1 |
| JUnit | 4.13.2 |

SDK: `compileSdk = release(37) { minorApiLevel = 2 }`, `targetSdk = 36`, `minSdk = 26`.
Telemóvel alvo: Android 16 (API 36).

## Pendente: instalar no telemóvel (T0 passo 6)

O APK debug do T0 foi enviado com `apk-to-phone` para
`/storage/emulated/0/Download/cinzeiro-0.1.0-t0-debug.apk`
(sha256 `403de81dd28854605bfc67776004b4466d8b572a6d65ccbff9a1aab13e4f122b`).
Falta o Tiago instalá-lo e confirmar que abre com fundo escuro. Não há adb neste
host, logo `adb shell getprop ro.build.version.sdk` não foi corrido (Android 16 é
o valor declarado pelo Tiago).
