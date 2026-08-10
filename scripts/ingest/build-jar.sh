#!/usr/bin/env bash
# 적재 배치용 실행 jar을 빌드해 Codex 작업 공간에 설치한다.
#
#   ./scripts/ingest/build-jar.sh
#
# 언제 실행하나: app/backend/src/main 아래를 고친 뒤. 잊으면 ingest.sh가
# 최신성 가드에 걸려 멈춘다(조용히 옛 코드로 적재하는 것보다 낫다).
#
# 왜 Codex가 이걸 직접 못 부르게 하나:
#   빌드는 사람이 하는 행위다. 새벽에 자동 실행되는 에이전트에게 빌드를 맡기면
#   앱 소스 접근이 필요해지고, 작업 공간 격리가 무너진다. Codex가 실행 가능한 명령은
#   여전히 bin/ingest.sh 하나뿐이다.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORKSPACE="${PEERUP_INGEST_HOME:-$HOME/peerup-ingest}"
BACKEND="$REPO_ROOT/app/backend"

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

echo "▌빌드: gradle bootJar"
(cd "$BACKEND" && gradle bootJar --quiet)

BUILT="$(ls -t "$BACKEND"/build/libs/*.jar | grep -v -- '-plain\.jar$' | head -1)"
if [ -z "$BUILT" ]; then
  echo "❌ 빌드 산출물을 찾지 못했습니다: $BACKEND/build/libs" >&2
  exit 1
fi

# 적재 코드가 실제로 들어갔는지 확인한다. jar만 있고 알맹이가 없으면
# 적재는 "0건 성공"으로 조용히 끝나고, 그건 실패보다 나쁘다.
#
# 목록을 변수에 한 번만 담는 이유: `unzip -l | grep -q`는 grep이 첫 매치에서 끝나며
# unzip에 SIGPIPE를 보내고, pipefail 때문에 파이프라인이 실패로 잡혀 오탐이 난다.
LISTING="$(unzip -l "$BUILT")"
for entry in \
    'com/guidematch/knowledge/IngestRunner.class' \
    'BOOT-INF/classes/application-ingest.yml'; do
  case "$LISTING" in
    *"$entry"*) ;;
    *) echo "❌ jar에 $entry 가 없습니다 — 빌드가 잘못됐습니다" >&2; exit 1 ;;
  esac
done

mkdir -p "$WORKSPACE/lib"
# mtime을 일부러 "지금"으로 새로 찍는다(-p를 쓰지 않는다).
#
# ingest.sh의 최신성 가드가 보는 것은 "이 jar이 소스와 대조된 시각"이다. Gradle은 내용
# 해시로 최신 여부를 판단하므로, UP-TO-DATE라 재빌드를 안 했더라도 jar은 지금 소스와
# 같은 내용임이 방금 확인된 것이다. 이때 mtime을 보존하면(-p) 가드를 풀 방법이 없어진다:
#   git checkout·stash·IDE 저장은 내용을 안 바꾸고도 소스 mtime을 미래로 옮긴다
#   → 소스가 jar보다 새로워짐 → Gradle은 재빌드 안 함 → 가드가 영원히 걸린 채로 남는다
# 실제로 이 함정을 밟고 나서 고쳤다.
cp "$BUILT" "$WORKSPACE/lib/ingest.jar"
touch "$WORKSPACE/lib/ingest.jar"

echo "✅ 설치 완료: $WORKSPACE/lib/ingest.jar"
ls -lh "$WORKSPACE/lib/ingest.jar"
