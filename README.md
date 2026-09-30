# SmokeTracker

App Android pessoal (nome visível «SmokeTracker»; `applicationId`, pacotes Kotlin, directório
do projecto e o id de formato `cinzeiro-export` mantêm `cinzeiro` — invisíveis, mudá-los
perderia os registos instalados) para registar cigarros com um toque e ver estatísticas.
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
  sem permissões; nome sugerido `smoketracker-AAAA-MM-DD.csv|json`. Formatos em `domain/export/` (ADR §Decisão 10):
  - CSV: `id,instant_utc,zone_id,local_datetime`, UTF-8 sem BOM, `\n`.
  - JSON: `{format:"cinzeiro-export", version:1, exported_at, app_version, count, events:[…]}`.

## Importar (R6)

- Histórico → ícone de descarregar → «Importar JSON» (sempre activo, mesmo com a BD vazia).
  Seletor SAF `OpenDocument` com `application/json`, `text/plain`, `application/octet-stream`
  (alguns gestores de ficheiros não marcam `.json`). Sem permissões.
- `domain/export/JsonImporter` (Kotlin puro) valida **o ficheiro inteiro** antes de escrever:
  `format = cinzeiro-export`, `version = 1`, `count = n.º de eventos`, e cada evento com
  `instant_utc` ISO-8601 não futuro e `zone_id` IANA conhecido. Qualquer erro → nada é importado;
  a mensagem indica o primeiro erro e o registo («registo n.º 3: instante no futuro …»).
- Dedupe por `(at_epoch_ms, zone_id)` ao milissegundo; os `id` do ficheiro são ignorados (a BD
  atribui novos). Importar o mesmo ficheiro duas vezes não duplica nada.
- Diálogo «Importar N registos (M já existem e serão ignorados)?»; se N = 0 só aparece a mensagem.
  A inserção é uma transacção Room (`SmokeEventDao.insertMissing`); `repository.changes` emite
  **uma** vez no fim → widget actualiza.

## Release (T9)

A release é assinada com uma keystore própria, **diferente** da debug: o Android não instala a
release por cima da debug (nem o contrário). Passar de uma para a outra = **desinstalar = perder
os dados**. Antes: Histórico → Exportar → JSON; depois de instalar: Histórico → Importar JSON.

Versões: debug actual **0.7.0 / 8**. A primeira release será **1.0.0 / versionCode 100** (a mudar
em `app/build.gradle.kts` no passo 3 do plano R6, não antes).

### 1. Criar a keystore (uma vez, pelo Tiago, no terminal)

```bash
tools/make-release-keystore.sh
```

- Pede a master password do Bitwarden (`bw unlock`, interactivo). Não imprime a password gerada.
- Gera `~/.android/smoketracker-release.jks` (PKCS12, alias `smoketracker`, RSA 4096,
  10000 dias, `CN=SmokeTracker`), `chmod 600`. Password aleatória (`openssl rand -base64 32`),
  igual para a store e para a chave.
- Bitwarden: item **«SmokeTracker — keystore release»** (tipo login, utilizador `smoketracker`,
  password = a da keystore) com o `.jks` em **anexo** (cópia de segurança).
- Acrescenta a `~/.gradle/gradle.properties` (fora do repo, `chmod 600`):
  `SMOKETRACKER_STORE_FILE`, `SMOKETRACKER_STORE_PASSWORD`, `SMOKETRACKER_KEY_ALIAS`,
  `SMOKETRACKER_KEY_PASSWORD`.
- Recusa correr se o `.jks` já existir, se as propriedades já lá estiverem ou se já houver um item
  com esse nome no Bitwarden. **Nunca** gerar outra keystore: perdê-la = não poder actualizar a app
  sem desinstalar. Para repor noutra máquina: descarregar o anexo do item Bitwarden para
  `~/.android/` e copiar as 4 propriedades.

### 2. Compilar a release

```bash
export JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/opt/android-sdk
./gradlew assembleRelease --console=plain > build.log 2>&1; echo EXIT=$?
/opt/android-sdk/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

O certificado tem de ser `CN=SmokeTracker` (não `CN=Android Debug`). Sem as 4 propriedades a
tarefa `checkReleaseSigning` falha com «Assinatura release não configurada: faltam as
propriedades Gradle …» — **nunca** se cai para a chave debug. `assembleDebug` e os testes não
dependem destas propriedades.

## Widget (T8)

- Glance 2×1: contagem de hoje, «último HH:MM» (hora absoluta), botão «+1».
- Os dados são lidos **dentro** da composição (`widgetStateFlow`: Flows do Room
  `observeCountBetween` + `observeLast`, com `collectAsState`), porque o Glance mantém a sessão viva
  e `update()/updateAll()` recompõe sem voltar a chamar `provideGlance` (bug R2.2). O dia é
  recalculado a cada meia-noite local no próprio Flow; `updateAll` continua após o +1, em `changes`
  (em `App`) e à meia-noite (WorkManager) para acordar/recriar a sessão.
- +1 do widget: vibração curta (`EFFECT_CLICK`, `USAGE_HARDWARE_FEEDBACK` — o uso `TOUCH` é
  ignorado com a app em segundo plano) e Toast «Registado às HH:MM · N hoje». Permissão `VIBRATE`.
- Permissões novas no APK vêm do `work-runtime` (WAKE_LOCK, ACCESS_NETWORK_STATE,
  RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE) — todas «normal», concedidas na instalação.

