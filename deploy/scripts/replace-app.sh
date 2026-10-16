#!/usr/bin/env bash
set -Eeuo pipefail

uploaded_jar="${1:?uploaded jar is required}"
expected_sha="${2:?sha256 is required}"
[[ "$uploaded_jar" =~ ^/tmp/deveopsj-[a-f0-9]{32}\.jar$ ]] || exit 2
[[ "$expected_sha" =~ ^[a-f0-9]{64}$ ]] || exit 2
[[ "$(sha256sum "$uploaded_jar" | cut -d ' ' -f 1)" == "$expected_sha" ]] || {
    echo 'Uploaded JAR checksum mismatch' >&2
    exit 1
}
exec 9>/opt/deveopsj/.deploy.lock
flock -n 9 || { echo 'Another deployment is running' >&2; exit 1; }
test -f /etc/deveopsj/deveopsj.env
test -f /opt/deveopsj/app.jar
command -v curl >/dev/null
systemctl cat deveopsj >/dev/null
backup="/opt/deveopsj/app.jar.previous"
changed=0

cleanup() {
    rm -f -- "$uploaded_jar"
    if [[ "$0" =~ ^/tmp/deveopsj-[a-f0-9]{32}\.sh$ ]]; then
        rm -f -- "$0"
    fi
}
rollback() {
    code=$?
    trap - ERR
    if [[ "$changed" == 1 ]]; then
        echo 'Deployment failed; restoring the previous JAR' >&2
        if ! install -o deveopsj -g deveopsj -m 640 "$backup" /opt/deveopsj/app.jar ||
           ! systemctl restart deveopsj; then
            echo 'ROLLBACK FAILED: operator action is required' >&2
        elif ! healthy; then
            echo 'Previous JAR restored, but health is still failing' >&2
        else
            echo 'Previous JAR restored and health passed' >&2
        fi
    fi
    exit "$code"
}
healthy() {
    for attempt in $(seq 1 30); do
        if systemctl is-active --quiet deveopsj &&
           curl --silent --fail --max-time 3 http://127.0.0.1:8080/actuator/health |
               grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
            return 0
        fi
        sleep 2
    done
    return 1
}
trap cleanup EXIT
trap rollback ERR
install -o deveopsj -g deveopsj -m 640 /opt/deveopsj/app.jar "$backup"
install -o deveopsj -g deveopsj -m 640 "$uploaded_jar" /opt/deveopsj/app.jar.next
mv -f /opt/deveopsj/app.jar.next /opt/deveopsj/app.jar
changed=1
systemctl restart deveopsj
healthy
echo 'Application deployment healthy. Database/env/SQL were not changed.'
