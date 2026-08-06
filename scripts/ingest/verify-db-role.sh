#!/usr/bin/env bash
# 적재 전용 DB 롤이 제대로 좁혀졌는지 확인한다.
#
#   ./scripts/ingest/verify-db-role.sh
#
# 언제: docs/ingest/db-role.sql을 실행하고 .env에 INGEST_DB_* 를 채운 직후.
# 접속만 보는 게 아니라 **앱 테이블이 막혔는지**까지 확인한다 — 그게 이 작업의 목적이다.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$REPO_ROOT/app/backend/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ .env가 없습니다: $ENV_FILE" >&2
  exit 2
fi

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

# postgresql 드라이버는 gradle 캐시에 이미 있다 — 별도 다운로드를 만들지 않는다.
DRIVER="$(find "$HOME/.gradle/caches" -name 'postgresql-42*.jar' 2>/dev/null | head -1)"
if [ -z "$DRIVER" ]; then
  echo "❌ postgresql 드라이버를 찾지 못했습니다. 먼저 백엔드를 한 번 빌드하세요:" >&2
  echo "   $REPO_ROOT/scripts/ingest/build-jar.sh" >&2
  exit 2
fi

exec "$JAVA_HOME/bin/java" -cp "$DRIVER" \
    "$REPO_ROOT/scripts/ingest/VerifyDbRole.java" "$ENV_FILE"
