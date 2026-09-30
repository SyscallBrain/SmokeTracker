#!/usr/bin/env bash
# Generates the SmokeTracker release keystore, stores the password and a copy of the .jks in
# Bitwarden, and writes the signing properties to ~/.gradle/gradle.properties.
#
# Bitwarden (free account, no Premium needed — attachments require Premium):
#   "SmokeTracker — keystore release"                            login holding the password
#   "SmokeTracker — keystore release (ficheiro .jks em base64)"  secure note holding the .jks
# The item names are kept as-is: they identify items that already exist in the vault.
# Restore the .jks: bw get notes <note-id> | base64 -d > ~/.android/smoketracker-release.jks
#
# Run it ONCE, in an interactive terminal (it asks for the Bitwarden master password
# interactively). It contains no secrets; the password is never printed nor passed as an
# argument (keytool reads it from an environment variable with -storepass:env / -keypass:env).
#
# Idempotent: refuses to run if the keystore already exists (regenerating = new signature =
# Android forces the app to be uninstalled and its data is lost).
#
# Optional variables (testing):
#   SMOKETRACKER_KEYSTORE      path of the .jks         (default: ~/.android/smoketracker-release.jks)
#   SMOKETRACKER_GRADLE_PROPS  properties file          (default: ~/.gradle/gradle.properties)
#   KEYTOOL                    keytool executable       (default: /opt/android-studio/jbr/bin/keytool or the one on PATH)
set -euo pipefail

KEYSTORE="${SMOKETRACKER_KEYSTORE:-$HOME/.android/smoketracker-release.jks}"
GRADLE_PROPS="${SMOKETRACKER_GRADLE_PROPS:-$HOME/.gradle/gradle.properties}"
ALIAS="smoketracker"
DNAME="CN=SmokeTracker"
ITEM_NAME="SmokeTracker — keystore release"
NOTE_NAME="SmokeTracker — keystore release (ficheiro .jks em base64)"
NOTE_MAX_CHARS=10000   # limit of the Bitwarden notes field
RESTORE_CMD="bw get notes <id> | base64 -d > ~/.android/smoketracker-release.jks"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
info() { printf '==> %s\n' "$*" >&2; }

# 1. Refuse if it already exists (before anything else).
if [[ -e "$KEYSTORE" ]]; then
    die "the keystore already exists at $KEYSTORE — not replacing it (changing the key forces the app to be uninstalled). Password: Bitwarden \"$ITEM_NAME\"; .jks: note \"$NOTE_NAME\" ($RESTORE_CMD)."
fi
if [[ -f "$GRADLE_PROPS" ]] && grep -q '^SMOKETRACKER_STORE_FILE=' "$GRADLE_PROPS"; then
    die "$GRADLE_PROPS already has SMOKETRACKER_STORE_FILE but there is no keystore at $KEYSTORE — fix it by hand (restore the .jks from the note \"$NOTE_NAME\": $RESTORE_CMD)."
fi

# 2. Tools.
if [[ -z "${KEYTOOL:-}" ]]; then
    if [[ -x /opt/android-studio/jbr/bin/keytool ]]; then
        KEYTOOL=/opt/android-studio/jbr/bin/keytool
    else
        KEYTOOL="$(command -v keytool || true)"
    fi
fi
[[ -n "$KEYTOOL" && -x "$KEYTOOL" ]] || die "keytool not found (set KEYTOOL=...)"
command -v bw >/dev/null || die "bw (Bitwarden CLI) not found on PATH"
command -v jq >/dev/null || die "jq not found"
command -v openssl >/dev/null || die "openssl not found"
command -v base64 >/dev/null || die "base64 not found"
command -v sha256sum >/dev/null || die "sha256sum not found"

TMPDIR_RUN="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_RUN"; }
trap cleanup EXIT
umask 077

# 3. Bitwarden: unlock (interactive) and sync.
info "Unlocking Bitwarden (asks for the master password)…"
BW_SESSION="$(bw unlock --raw)" || die "bw unlock failed"
[[ -n "$BW_SESSION" ]] || die "bw unlock returned no session"
export BW_SESSION
bw sync --quiet || die "bw sync failed"
for name in "$ITEM_NAME" "$NOTE_NAME"; do
    if [[ "$(bw list items --search "$name" | jq --arg n "$name" '[.[] | select(.name == $n)] | length')" != "0" ]]; then
        die "an item \"$name\" already exists in Bitwarden — not creating another. Restore the .jks from the note \"$NOTE_NAME\": $RESTORE_CMD"
    fi
done

# 4. Random password (environment variable only; never printed).
SMOKETRACKER_KS_PASSWORD="$(openssl rand -base64 32)"
export SMOKETRACKER_KS_PASSWORD

# 5. Generate the keystore (PKCS12: the key password is the same as the store password).
mkdir -p "$(dirname "$KEYSTORE")"
info "Generating $KEYSTORE (RSA 4096, 10000 days, alias $ALIAS)…"
"$KEYTOOL" -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "$DNAME" \
    -storepass:env SMOKETRACKER_KS_PASSWORD -keypass:env SMOKETRACKER_KS_PASSWORD
chmod 600 "$KEYSTORE"

# 6. Base64 copy of the .jks: does it fit in a secure note? Checked before touching Bitwarden;
#    if it does not fit, the freshly generated keystore is not used by anything yet and is deleted.
KS_B64="$(base64 -w 76 "$KEYSTORE")"
if (( ${#KS_B64} > NOTE_MAX_CHARS )); then
    rm -f "$KEYSTORE"
    die "the base64 .jks has ${#KS_B64} characters (> $NOTE_MAX_CHARS, the Bitwarden notes limit). Deleted $KEYSTORE (not used yet); nothing was created in Bitwarden."
fi
info "The base64 .jks has ${#KS_B64} characters (limit $NOTE_MAX_CHARS)."

# 7. Bitwarden item: login holding the password.
info "Creating the item \"$ITEM_NAME\" in Bitwarden…"
ITEM_ID="$(
    bw get template item | jq \
        --arg name "$ITEM_NAME" \
        --arg alias "$ALIAS" \
        --arg path "$KEYSTORE" \
        --arg note "$NOTE_NAME" \
        '.type = 1 | .name = $name | .folderId = null
         | .login = {"username": $alias, "password": env.SMOKETRACKER_KS_PASSWORD, "uris": [], "totp": null}
         | .notes = "Release keystore of the SmokeTracker app (pt.tiago.cinzeiro). Alias: \($alias). File: \($path). Store password = key password. The .jks is stored as base64 in the secure note \"\($note)\". Losing this = the app cannot be updated without uninstalling."
         | .fields = [{"name": "alias", "value": $alias, "type": 0}]' \
    | bw encode | bw create item | jq -r '.id'
)" || ITEM_ID=""
[[ -n "$ITEM_ID" && "$ITEM_ID" != "null" ]] || die "bw create item failed (the keystore is at $KEYSTORE; back it up by hand)"

# 8. Gradle properties (outside the repo). Written at this point: the password is already in
#    Bitwarden, so the keystore stays usable even if the note fails afterwards.
info "Writing the properties to $GRADLE_PROPS…"
mkdir -p "$(dirname "$GRADLE_PROPS")"
touch "$GRADLE_PROPS"
chmod 600 "$GRADLE_PROPS"
{
    printf '\n# SmokeTracker — release signing (tools/make-release-keystore.sh). Password: Bitwarden "%s"; .jks: "%s"\n' "$ITEM_NAME" "$NOTE_NAME"
    printf 'SMOKETRACKER_STORE_FILE=%s\n' "$KEYSTORE"
    printf 'SMOKETRACKER_STORE_PASSWORD=%s\n' "$SMOKETRACKER_KS_PASSWORD"
    printf 'SMOKETRACKER_KEY_ALIAS=%s\n' "$ALIAS"
    printf 'SMOKETRACKER_KEY_PASSWORD=%s\n' "$SMOKETRACKER_KS_PASSWORD"
} >> "$GRADLE_PROPS"

info "Certificate:"
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass:env SMOKETRACKER_KS_PASSWORD 2>/dev/null | grep -E 'Owner|SHA256:' >&2 || true
unset SMOKETRACKER_KS_PASSWORD

# 9. Secure note with the base64 .jks (no Premium needed) + sha256 check by reading it back.
NOTE_FAIL="the password item \"$ITEM_NAME\" (id $ITEM_ID) exists and the properties are in $GRADLE_PROPS, but the .jks copy is missing from Bitwarden. Store it by hand: run \"base64 -w 76 $KEYSTORE\" and paste the text into a secure note \"$NOTE_NAME\" (restore: $RESTORE_CMD)"
info "Storing the base64 .jks in the secure note \"$NOTE_NAME\"…"
NOTE_ID="$(
    bw get template item | jq \
        --arg name "$NOTE_NAME" \
        --arg b64 "$KS_B64" \
        --arg restore "$RESTORE_CMD" \
        '.type = 2 | .secureNote = {"type": 0} | .name = $name | .folderId = null | .login = null
         | .notes = $b64
         | .fields = [{"name": "restore", "value": $restore, "type": 0}]' \
    | bw encode | bw create item | jq -r '.id'
)" || NOTE_ID=""
[[ -n "$NOTE_ID" && "$NOTE_ID" != "null" ]] || die "bw create item (note) failed — $NOTE_FAIL"

info "Verifying the copy (sha256)…"
SHA_LOCAL="$(sha256sum "$KEYSTORE" | cut -d' ' -f1)"
SHA_NOTE="$(bw get notes "$NOTE_ID" | base64 -d | sha256sum | cut -d' ' -f1)" || SHA_NOTE=""
[[ "$SHA_NOTE" == "$SHA_LOCAL" ]] \
    || die "note $NOTE_ID does not match the .jks (sha256 ${SHA_NOTE:-?} ≠ $SHA_LOCAL) — $NOTE_FAIL"
info "Copy verified (sha256 $SHA_LOCAL)."

bw lock >/dev/null || true
info "Done. Keystore: $KEYSTORE (600). Properties: $GRADLE_PROPS (600). Bitwarden: \"$ITEM_NAME\" (id $ITEM_ID) + secure note \"$NOTE_NAME\" (id $NOTE_ID)."
info "Restore the .jks on another machine: bw get notes $NOTE_ID | base64 -d > ~/.android/smoketracker-release.jks"
