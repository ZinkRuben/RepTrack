#!/bin/sh
# RepTrack konténer indítása: a HTTPS-tanúsítvány előkészítése, majd az alkalmazás.
#
# Ha nincs tanúsítvány a /certs kötetben, első induláskor önaláírt tanúsítványt készít (keytool, PKCS12) egy
# véletlen jelszóval, amelyet mellé ment. A kötet miatt újraindításkor ugyanaz a tanúsítvány marad, így a böngészőben
# egyszer elfogadott kivétel érvényes marad. Saját tanúsítvány: tedd a kötetbe reptrack.p12 néven (alias: reptrack),
# és add meg a jelszavát a SERVER_SSL_KEY_STORE_PASSWORD környezeti változóban.
set -eu

CERT_DIR="${REPTRACK_CERT_DIR:-/certs}"
KEYSTORE="$CERT_DIR/reptrack.p12"
PASSFILE="$CERT_DIR/keystore.pass"
HOST="${REPTRACK_CERT_HOST:-localhost}"

if [ -z "${SERVER_SSL_KEY_STORE_PASSWORD:-}" ]; then
    if [ ! -f "$KEYSTORE" ]; then
        echo "Önaláírt tanúsítvány készítése: $KEYSTORE (CN=$HOST)"
        SAN="dns:localhost,ip:127.0.0.1"
        if [ "$HOST" != "localhost" ]; then
            SAN="dns:$HOST,$SAN"
        fi
        umask 077
        head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32 > "$PASSFILE"
        keytool -genkeypair -alias reptrack -keyalg RSA -keysize 2048 -validity 825 \
            -storetype PKCS12 -keystore "$KEYSTORE" -storepass "$(cat "$PASSFILE")" \
            -dname "CN=$HOST, O=RepTrack" -ext "SAN=$SAN" 2>&1
    fi
    if [ ! -f "$PASSFILE" ]; then
        echo "Hiba: a $KEYSTORE tanúsítvány jelszava nem ismert. Add meg a SERVER_SSL_KEY_STORE_PASSWORD változóban." >&2
        exit 1
    fi
    SERVER_SSL_KEY_STORE_PASSWORD="$(cat "$PASSFILE")"
    export SERVER_SSL_KEY_STORE_PASSWORD
fi

exec java -jar /app/application.jar "$@"
