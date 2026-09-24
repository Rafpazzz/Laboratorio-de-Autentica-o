#!/usr/bin/env sh

set -eu

target_directory="${1:-.local/saml}"
private_key="$target_directory/sp-private.key"
certificate="$target_directory/sp-certificate.crt"

if [ -f "$private_key" ] && [ -f "$certificate" ]; then
    printf 'SAML credentials already exist in %s\n' "$target_directory"
    exit 0
fi

mkdir -p "$target_directory"
umask 077

openssl genpkey \
    -algorithm RSA \
    -pkeyopt rsa_keygen_bits:3072 \
    -out "$private_key"

openssl req \
    -new \
    -x509 \
    -sha256 \
    -key "$private_key" \
    -out "$certificate" \
    -days 3650 \
    -subj "/CN=autenticacao-lab-saml"

chmod 600 "$private_key"
chmod 644 "$certificate"

printf 'SAML credentials created in %s\n' "$target_directory"
