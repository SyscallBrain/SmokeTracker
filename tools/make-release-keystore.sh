#!/usr/bin/env bash
# Gera a keystore de release do SmokeTracker, guarda a password e uma cópia do .jks no
# Bitwarden, e escreve as propriedades de assinatura em ~/.gradle/gradle.properties.
#
# Bitwarden (conta gratuita, sem Premium — os anexos exigem Premium):
#   «SmokeTracker — keystore release»                           login com a password
#   «SmokeTracker — keystore release (ficheiro .jks em base64)»  Nota Segura com o .jks
# Restaurar o .jks: bw get notes <id-da-nota> | base64 -d > ~/.android/smoketracker-release.jks
#
# Corre-se UMA vez, no terminal do Tiago (pede a master password do Bitwarden de forma
# interactiva). Não contém segredos; a password nunca é impressa nem passada em argumentos
# (keytool lê-a de uma variável de ambiente com -storepass:env / -keypass:env).
#
# Idempotente: recusa se a keystore já existir (voltar a gerar = nova assinatura = o Android
# obriga a desinstalar a app e perdem-se os dados).
#
# Variáveis opcionais (testes):
#   SMOKETRACKER_KEYSTORE      caminho do .jks          (omissão: ~/.android/smoketracker-release.jks)
#   SMOKETRACKER_GRADLE_PROPS  ficheiro de propriedades (omissão: ~/.gradle/gradle.properties)
#   KEYTOOL                    executável keytool       (omissão: /opt/android-studio/jbr/bin/keytool ou o do PATH)
set -euo pipefail

KEYSTORE="${SMOKETRACKER_KEYSTORE:-$HOME/.android/smoketracker-release.jks}"
GRADLE_PROPS="${SMOKETRACKER_GRADLE_PROPS:-$HOME/.gradle/gradle.properties}"
ALIAS="smoketracker"
DNAME="CN=SmokeTracker"
ITEM_NAME="SmokeTracker — keystore release"
NOTE_NAME="SmokeTracker — keystore release (ficheiro .jks em base64)"
NOTE_MAX_CHARS=10000   # limite do campo notes do Bitwarden
RESTORE_CMD="bw get notes <id> | base64 -d > ~/.android/smoketracker-release.jks"

die() { printf 'erro: %s\n' "$*" >&2; exit 1; }
info() { printf '==> %s\n' "$*" >&2; }

# 1. Recusar se já existe (antes de qualquer outra coisa).
if [[ -e "$KEYSTORE" ]]; then
    die "a keystore já existe em $KEYSTORE — não a substituo (mudar a chave obriga a desinstalar a app). Password: Bitwarden «$ITEM_NAME»; .jks: nota «$NOTE_NAME» ($RESTORE_CMD)."
fi
if [[ -f "$GRADLE_PROPS" ]] && grep -q '^SMOKETRACKER_STORE_FILE=' "$GRADLE_PROPS"; then
    die "$GRADLE_PROPS já tem SMOKETRACKER_STORE_FILE mas a keystore não existe em $KEYSTORE — resolve à mão (repor o .jks da nota «$NOTE_NAME»: $RESTORE_CMD)."
fi

# 2. Ferramentas.
if [[ -z "${KEYTOOL:-}" ]]; then
    if [[ -x /opt/android-studio/jbr/bin/keytool ]]; then
        KEYTOOL=/opt/android-studio/jbr/bin/keytool
    else
        KEYTOOL="$(command -v keytool || true)"
    fi
fi
[[ -n "$KEYTOOL" && -x "$KEYTOOL" ]] || die "keytool não encontrado (define KEYTOOL=...)"
command -v bw >/dev/null || die "bw (Bitwarden CLI) não encontrado no PATH"
command -v jq >/dev/null || die "jq não encontrado"
command -v openssl >/dev/null || die "openssl não encontrado"
command -v base64 >/dev/null || die "base64 não encontrado"
command -v sha256sum >/dev/null || die "sha256sum não encontrado"

TMPDIR_RUN="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_RUN"; }
trap cleanup EXIT
umask 077

# 3. Bitwarden: desbloquear (interactivo) e sincronizar.
info "A desbloquear o Bitwarden (pede a master password)…"
BW_SESSION="$(bw unlock --raw)" || die "bw unlock falhou"
[[ -n "$BW_SESSION" ]] || die "bw unlock não devolveu sessão"
export BW_SESSION
bw sync --quiet || die "bw sync falhou"
for name in "$ITEM_NAME" "$NOTE_NAME"; do
    if [[ "$(bw list items --search "$name" | jq --arg n "$name" '[.[] | select(.name == $n)] | length')" != "0" ]]; then
        die "já existe um item «$name» no Bitwarden — não crio outro. Repõe o .jks a partir da nota «$NOTE_NAME»: $RESTORE_CMD"
    fi
done

# 4. Password aleatória (só em variável de ambiente; nunca impressa).
SMOKETRACKER_KS_PASSWORD="$(openssl rand -base64 32)"
export SMOKETRACKER_KS_PASSWORD

# 5. Gerar a keystore (PKCS12: a password da chave é a mesma da store).
mkdir -p "$(dirname "$KEYSTORE")"
info "A gerar $KEYSTORE (RSA 4096, 10000 dias, alias $ALIAS)…"
"$KEYTOOL" -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "$DNAME" \
    -storepass:env SMOKETRACKER_KS_PASSWORD -keypass:env SMOKETRACKER_KS_PASSWORD
chmod 600 "$KEYSTORE"

# 6. Cópia do .jks em base64: cabe numa Nota Segura? Verificado antes de tocar no Bitwarden;
#    se não couber, a keystore acabada de gerar ainda não é usada por nada e é apagada.
KS_B64="$(base64 -w 76 "$KEYSTORE")"
if (( ${#KS_B64} > NOTE_MAX_CHARS )); then
    rm -f "$KEYSTORE"
    die "o .jks em base64 tem ${#KS_B64} caracteres (> $NOTE_MAX_CHARS, limite das notas do Bitwarden). Apaguei $KEYSTORE (ainda não usado); nada foi criado no Bitwarden."
fi
info "O .jks em base64 tem ${#KS_B64} caracteres (limite $NOTE_MAX_CHARS)."

# 7. Item no Bitwarden: login com a password.
info "A criar o item «$ITEM_NAME» no Bitwarden…"
ITEM_ID="$(
    bw get template item | jq \
        --arg name "$ITEM_NAME" \
        --arg alias "$ALIAS" \
        --arg path "$KEYSTORE" \
        --arg note "$NOTE_NAME" \
        '.type = 1 | .name = $name | .folderId = null
         | .login = {"username": $alias, "password": env.SMOKETRACKER_KS_PASSWORD, "uris": [], "totp": null}
         | .notes = "Keystore de release da app SmokeTracker (pt.tiago.cinzeiro). Alias: \($alias). Ficheiro: \($path). Password da store = password da chave. O .jks está em base64 na Nota Segura «\($note)». Perder isto = não poder actualizar a app sem desinstalar."
         | .fields = [{"name": "alias", "value": $alias, "type": 0}]' \
    | bw encode | bw create item | jq -r '.id'
)" || ITEM_ID=""
[[ -n "$ITEM_ID" && "$ITEM_ID" != "null" ]] || die "bw create item falhou (a keystore ficou em $KEYSTORE; guarda-a à mão)"

# 8. Propriedades Gradle (fora do repo). Escritas já aqui: a password está no Bitwarden, por isso
#    a keystore fica utilizável mesmo que a nota falhe a seguir.
info "A escrever as propriedades em $GRADLE_PROPS…"
mkdir -p "$(dirname "$GRADLE_PROPS")"
touch "$GRADLE_PROPS"
chmod 600 "$GRADLE_PROPS"
{
    printf '\n# SmokeTracker — assinatura release (tools/make-release-keystore.sh). Password: Bitwarden «%s»; .jks: «%s»\n' "$ITEM_NAME" "$NOTE_NAME"
    printf 'SMOKETRACKER_STORE_FILE=%s\n' "$KEYSTORE"
    printf 'SMOKETRACKER_STORE_PASSWORD=%s\n' "$SMOKETRACKER_KS_PASSWORD"
    printf 'SMOKETRACKER_KEY_ALIAS=%s\n' "$ALIAS"
    printf 'SMOKETRACKER_KEY_PASSWORD=%s\n' "$SMOKETRACKER_KS_PASSWORD"
} >> "$GRADLE_PROPS"

info "Certificado:"
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass:env SMOKETRACKER_KS_PASSWORD 2>/dev/null | grep -E 'Owner|SHA256:' >&2 || true
unset SMOKETRACKER_KS_PASSWORD

# 9. Nota Segura com o .jks em base64 (não exige Premium) + verificação sha256 lendo-a de volta.
NOTE_FAIL="o item de password «$ITEM_NAME» (id $ITEM_ID) existe e as propriedades estão em $GRADLE_PROPS, mas falta a cópia do .jks no Bitwarden. Guarda-a à mão: corre «base64 -w 76 $KEYSTORE» e cola o texto numa Nota Segura «$NOTE_NAME» (restaurar: $RESTORE_CMD)"
info "A guardar o .jks em base64 na Nota Segura «$NOTE_NAME»…"
NOTE_ID="$(
    bw get template item | jq \
        --arg name "$NOTE_NAME" \
        --arg b64 "$KS_B64" \
        --arg restore "$RESTORE_CMD" \
        '.type = 2 | .secureNote = {"type": 0} | .name = $name | .folderId = null | .login = null
         | .notes = $b64
         | .fields = [{"name": "restaurar", "value": $restore, "type": 0}]' \
    | bw encode | bw create item | jq -r '.id'
)" || NOTE_ID=""
[[ -n "$NOTE_ID" && "$NOTE_ID" != "null" ]] || die "bw create item (nota) falhou — $NOTE_FAIL"

info "A verificar a cópia (sha256)…"
SHA_LOCAL="$(sha256sum "$KEYSTORE" | cut -d' ' -f1)"
SHA_NOTE="$(bw get notes "$NOTE_ID" | base64 -d | sha256sum | cut -d' ' -f1)" || SHA_NOTE=""
[[ "$SHA_NOTE" == "$SHA_LOCAL" ]] \
    || die "a nota $NOTE_ID não coincide com o .jks (sha256 ${SHA_NOTE:-?} ≠ $SHA_LOCAL) — $NOTE_FAIL"
info "Cópia verificada (sha256 $SHA_LOCAL)."

bw lock >/dev/null || true
info "Feito. Keystore: $KEYSTORE (600). Propriedades: $GRADLE_PROPS (600). Bitwarden: «$ITEM_NAME» (id $ITEM_ID) + Nota Segura «$NOTE_NAME» (id $NOTE_ID)."
info "Restaurar o .jks noutra máquina: bw get notes $NOTE_ID | base64 -d > ~/.android/smoketracker-release.jks"
