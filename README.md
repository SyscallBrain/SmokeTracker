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
| Robolectric (testes do Room na JVM) | 4.17 (SDK 36) |
| androidx.test core | 1.7.0 |
| kotlinx-coroutines-test | 1.11.0 |

SDK: `compileSdk = release(37) { minorApiLevel = 2 }`, `targetSdk = 36`, `minSdk = 26`.
Telemóvel alvo: Android 16 (API 36).

## Pendente: instalar no telemóvel (T0 passo 6)

O APK debug do T0 foi enviado com `apk-to-phone` para
`/storage/emulated/0/Download/cinzeiro-0.1.0-t0-debug.apk`
(sha256 `403de81dd28854605bfc67776004b4466d8b572a6d65ccbff9a1aab13e4f122b`).
Falta o Tiago instalá-lo e confirmar que abre com fundo escuro. Não há adb neste
host, logo `adb shell getprop ro.build.version.sdk` não foi corrido (Android 16 é
o valor declarado pelo Tiago).

## Estatísticas (`domain/`)

Kotlin puro (`java.time`, sem `android.*`), testado na JVM com `./gradlew testDebugUnitTest`.

- O dia termina à meia-noite local da zona de cada registo.
- `summary(..., endInclusive = hoje)` conta hoje como **dia inteiro** no `perDay` mesmo que
  ainda esteja a decorrer (simplicidade > precisão ao décimo). O cartão «média 7 dias» do ecrã
  Hoje usa `endInclusive = ontem` para não ser puxado para baixo pelo dia parcial.
- `perDay` divide por `min(N, dias desde o primeiro registo + 1)`.
- `deltaPct` é `null` se o período anterior começar antes do primeiro registo ou tiver média 0.

Desempenho medido (teste `SummaryTest`, 2026-09-29, JVM do host = JBR 21, não telemóvel):
`summary` sobre **3 anos × 40 cigarros/dia = 43 840 eventos**, média de 10 execuções após
aquecimento: D7 4,29 ms · D30 1,92 ms · D90 2,29 ms · 1 a 2,63 ms. Gatilho do ADR é >100 ms
**no telemóvel** — ainda não medido lá.

## Dados (`data/`)

- Room, tabela `smoke_event(id, at_epoch_ms, zone_id)` com índice em `at_epoch_ms` (ADR §3).
  Esquema exportado em `app/schemas/pt.tiago.cinzeiro.data.AppDatabase/1.json` — **versionado**,
  é a base das futuras migrações.
- Intervalos das consultas (`observeBetween`, `countBetween`): `[from, to)` — início inclusivo,
  fim exclusivo.
- `SmokeRepository(dao, clock)`: `logNow()` usa `clock.instant()`/`clock.zone`; `logAt()` rejeita
  instantes no futuro (`IllegalArgumentException`); `delete(id)` apaga **esse** id.
  `changes: SharedFlow<Change>` emite após cada inserção/remoção (gancho para o widget do T8).
- Relógio em produção: `SystemZoneClock` (zona lida em cada chamada — muda com o fuso do telemóvel).
- Testes do DAO/repositório: Robolectric 4.17 com BD Room em memória
  (`app/src/test/.../data/SmokeRepositoryTest.kt`). Precisa de
  `--add-exports java.base/jdk.internal.access=ALL-UNNAMED` na JVM dos testes (já no
  `app/build.gradle.kts`); sem isso o SDK 36 do Robolectric falha em `ApplicationSharedMemory`.

### Seed de debug (só no APK debug)

**Como abrir:** no ecrã Hoje, **pressão longa na data** (ex.: «ter, 29 set») → diálogo «Debug» com
«Gerar dados de teste», «Apagar tudo» e «Sistema de design» (catálogo de cores/componentes do T1).
No release a data não reage à pressão longa.


O código vive em `app/src/debug/` (`SyntheticData`, `createDebugTools`); o `src/release/` devolve
`null`, por isso o release não contém nada disto. Gera 90 dias (8–18/dia, picos às 8 h, 13 h e
21 h, semente fixa) e **substitui todos os registos**.

## Ecrã Hoje (T4)

- Data · contagem grande (tnum, animada) · «último há …» (actualiza ao minuto) · botão +1 (128 dp,
  vibração `LongPress`) · cartões «média 7 dias» (`summary(D7, endInclusive = ontem)`, com variação)
  e «intervalo hoje» · barras por hora (Vico 3.3.1, hora actual em `primary`).
- Snackbar «Registado às HH:MM · Desfazer» durante 5 s; «Desfazer» apaga **o id inserido**.
- Navegação inferior: Hoje · Estatísticas · Histórico.
- Vico: API confirmada nas fontes `compose-android-3.3.1-sources.jar` (`columnModel`,
  `ColumnCartesianLayer.ColumnProvider`, `CartesianLayerRangeProvider.fixed`); `columnSeries` está
  deprecado na 3.3.1.

## Estatísticas (T5/T6)

- Período 7 d · 30 d · 90 d · 1 a; cartões total, média/dia, dias sem fumar, máximo; barras diárias
  (7/30 d) ou semanais (90 d/1 a) com média móvel de 7 d (Vico 3.3.1, `columnModel`).
- «Por hora do dia» (barras 0–23) e heatmap hora × dia da semana (Canvas, segunda = linha 0, `S T Q Q S S D`).

## Histórico (T7)

- Lista por dia local, páginas de 30 dias; deslizar para a esquerda apaga, snackbar «Desfazer»
  repõe o mesmo instante e zona (id novo). «Adicionar»: DatePicker + TimePicker M3 24 h; futuro
  bloqueado na UI e no repositório.

## Exportar (T10)

- Histórico → ícone de descarregar → CSV ou JSON; ficheiro escolhido pelo SAF (`CreateDocument`),
  sem permissões. Formatos em `domain/export/` (ADR §Decisão 10):
  - CSV: `id,instant_utc,zone_id,local_datetime`, UTF-8 sem BOM, `\n`.
  - JSON: `{format:"cinzeiro-export", version:1, exported_at, app_version, count, events:[…]}`.

## Widget (T8)

- Glance 2×1: contagem de hoje, «último HH:MM» (hora absoluta), botão «+1».
- Actualiza após o +1 (`updateAll` no `ActionCallback`), sempre que o repositório emite `changes`
  (em `App`, scope de aplicação) e à meia-noite local (WorkManager one-off reagendado).
- Permissões novas no APK vêm do `work-runtime` (WAKE_LOCK, ACCESS_NETWORK_STATE,
  RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE) — todas «normal», concedidas na instalação.

