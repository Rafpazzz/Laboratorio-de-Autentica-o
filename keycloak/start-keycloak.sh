#!/usr/bin/env sh

set -eu

certificate_path="/opt/keycloak/bootstrap/sp-certificate.crt"

if [ ! -f "$certificate_path" ]; then
    printf 'SAML certificate not found at %s\n' "$certificate_path" >&2
    exit 1
fi

SAML_SP_CERTIFICATE_VALUE="$(
    sed '/-----BEGIN CERTIFICATE-----/d; /-----END CERTIFICATE-----/d' \
        "$certificate_path" | tr -d '\r\n'
)"

if [ -z "$SAML_SP_CERTIFICATE_VALUE" ]; then
    printf 'SAML certificate is empty\n' >&2
    exit 1
fi

export SAML_SP_CERTIFICATE_VALUE

exec /opt/keycloak/bin/kc.sh start-dev --import-realm
