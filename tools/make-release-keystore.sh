#!/usr/bin/env bash
# Gera a keystore de release do SmokeTracker, guarda a password e uma cópia do .jks no
# Bitwarden, e escreve as propriedades de assinatura em ~/.gradle/gradle.properties.
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

die() { printf 'erro: %s\n' "$*" >&2; exit 1; }
info() { printf '==> %s\n' "$*" >&2; }

# 1. Recusar se já existe (antes de qualquer outra coisa).
if [[ -e "$KEYSTORE" ]]; then
    die "a keystore já existe em $KEYSTORE — não a substituo (mudar a chave obriga a desinstalar a app). A cópia está no Bitwarden («$ITEM_NAME»)."
fi
if [[ -f "$GRADLE_PROPS" ]] && grep -q '^SMOKETRACKER_STORE_FILE=' "$GRADLE_PROPS"; then
    die "$GRADLE_PROPS já tem SMOKETRACKER_STORE_FILE mas a keystore não existe em $KEYSTORE — resolve à mão (repor o .jks do Bitwarden)."
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
if [[ "$(bw list items --search "$ITEM_NAME" | jq --arg n "$ITEM_NAME" '[.[] | select(.name == $n)] | length')" != "0" ]]; then
    die "já existe um item «$ITEM_NAME» no Bitwarden — não crio outro. Repõe o .jks a partir do anexo."
fi

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

# 6. Item no Bitwarden (login com a password) + o .jks como anexo.
info "A criar o item «$ITEM_NAME» no Bitwarden…"
ITEM_ID="$(
    bw get template item | jq \
        --arg name "$ITEM_NAME" \
        --arg alias "$ALIAS" \
        --arg path "$KEYSTORE" \
        '.type = 1 | .name = $name | .folderId = null
         | .login = {"username": $alias, "password": env.SMOKETRACKER_KS_PASSWORD, "uris": [], "totp": null}
         | .notes = "Keystore de release da app SmokeTracker (pt.tiago.cinzeiro). Alias: \($alias). Ficheiro: \($path). Password da store = password da chave. O .jks está em anexo. Perder isto = não poder actualizar a app sem desinstalar."
         | .fields = [{"name": "alias", "value": $alias, "type": 0}]' \
    | bw encode | bw create item | jq -r '.id'
)"
[[ -n "$ITEM_ID" && "$ITEM_ID" != "null" ]] || die "bw create item falhou (a keystore ficou em $KEYSTORE; guarda-a à mão)"
bw create attachment --file "$KEYSTORE" --itemid "$ITEM_ID" >/dev/null \
    || die "bw create attachment falhou (item $ITEM_ID criado sem anexo; anexa $KEYSTORE à mão)"

# 7. Propriedades Gradle (fora do repo).
info "A escrever as propriedades em $GRADLE_PROPS…"
mkdir -p "$(dirname "$GRADLE_PROPS")"
touch "$GRADLE_PROPS"
chmod 600 "$GRADLE_PROPS"
{
    printf '\n# SmokeTracker — assinatura release (tools/make-release-keystore.sh). Cópia no Bitwarden: %s\n' "$ITEM_NAME"
    printf 'SMOKETRACKER_STORE_FILE=%s\n' "$KEYSTORE"
    printf 'SMOKETRACKER_STORE_PASSWORD=%s\n' "$SMOKETRACKER_KS_PASSWORD"
    printf 'SMOKETRACKER_KEY_ALIAS=%s\n' "$ALIAS"
    printf 'SMOKETRACKER_KEY_PASSWORD=%s\n' "$SMOKETRACKER_KS_PASSWORD"
} >> "$GRADLE_PROPS"

info "Certificado:"
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass:env SMOKETRACKER_KS_PASSWORD 2>/dev/null | grep -E 'Owner|SHA256:' >&2 || true

unset SMOKETRACKER_KS_PASSWORD
bw lock >/dev/null || true
info "Feito. Keystore: $KEYSTORE (600). Propriedades: $GRADLE_PROPS (600). Bitwarden: «$ITEM_NAME» (id $ITEM_ID) com o .jks em anexo."
