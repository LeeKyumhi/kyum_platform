#!/usr/bin/env bash
# Codex 수집 작업 공간을 만들고 계약 파일을 동기화한다.
#
#   ./scripts/ingest/setup-workspace.sh
#
# 언제 실행하나:
#   - 처음 한 번
#   - docs/ingest/ 아래 계약(CONTRACT.md·sources.yml·targets.yml·schema)을 고친 뒤
#
# 작업 공간을 앱 리포 바깥에 두는 이유:
#   스케줄로 자동 실행되는 에이전트에게 앱 소스 쓰기 권한을 주면, 아무도 안 보는 새벽에
#   "도움이 되려고" 코드를 고칠 수 있다. 물리적으로 불가능하게 만든다.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORKSPACE="${PEERUP_INGEST_HOME:-$HOME/peerup-ingest}"

echo "▌작업 공간: $WORKSPACE"
# lib = 적재 실행 jar, tmp = Tomcat 임시 디렉터리(샌드박스 쓰기 허용 범위 안에 못박는다)
mkdir -p "$WORKSPACE"/{contract/schema,runs,staging,state,logs,bin,lib,tmp}

echo "▌계약 파일 동기화 (리포 → 작업 공간)"
# 아래에서 읽기 전용으로 잠그므로, 재실행 시 덮어쓰려면 먼저 풀어야 한다.
chmod -R u+w "$WORKSPACE/contract" 2>/dev/null || true
cp "$REPO_ROOT/docs/ingest/CONTRACT.md"   "$WORKSPACE/contract/"
cp "$REPO_ROOT/docs/ingest/sources.yml"   "$WORKSPACE/contract/"
cp "$REPO_ROOT/docs/ingest/targets.yml"   "$WORKSPACE/contract/"
cp "$REPO_ROOT"/docs/ingest/schema/*.json "$WORKSPACE/contract/schema/"
# Codex가 실수로도 못 고치게 읽기 전용으로
chmod -w "$WORKSPACE/contract/"*.md "$WORKSPACE/contract/"*.yml "$WORKSPACE/contract/schema/"*.json 2>/dev/null || true

echo "▌적재 스크립트 설치"
sed "s|@@REPO_ROOT@@|$REPO_ROOT|g; s|@@WORKSPACE@@|$WORKSPACE|g" \
    "$REPO_ROOT/scripts/ingest/ingest.sh.template" > "$WORKSPACE/bin/ingest.sh"
chmod +x "$WORKSPACE/bin/ingest.sh"

echo "▌적재 jar 빌드·설치"
"$REPO_ROOT/scripts/ingest/build-jar.sh"

# 수집용 API 키는 작업 공간 안에 둔다.
#
# 리포의 .env에 두지 않는 이유: 그 파일에는 SUPABASE_SERVICE_KEY·JWT_SECRET처럼
# 수집과 무관하고 훨씬 위험한 값이 함께 있다. Codex가 그 파일을 열 이유를 만들지 않는다.
# 여기 있는 건 공공데이터 조회용 읽기 전용 키라 노출 시 피해가 그 API에서 멈춘다.
#
# 리포 밖이라 커밋될 수 없다 — .gitignore에 기대지 않는 구조적 보장이다.
mkdir -p "$WORKSPACE/secrets"
chmod 700 "$WORKSPACE/secrets"
if [ ! -f "$WORKSPACE/secrets/tour-api.key" ]; then
  cat > "$WORKSPACE/secrets/tour-api.key" <<'KEYEOF'
# 여기에 TourAPI 서비스키 한 줄만 남기고 이 주석들은 지운다.
#
# 발급: https://www.data.go.kr → "한국관광공사_국문 관광정보 서비스_GW" 활용신청 (보통 자동승인)
#
# ⚠ **디코딩된(Decoding) 키**를 넣을 것. 포털은 인코딩/디코딩 두 가지를 주는데,
#   인코딩 키를 쿼리스트링에 그대로 넣으면 이중 인코딩되어 401이 난다.
#   (%2F가 %252F가 되는 식 — 비밀번호가 틀린 것처럼 보이지만 아니다)
#
# ⚠ **꺾쇠 < > 를 같이 붙여넣지 말 것.** 안내문의 <디코딩키> 표기를 통째로 복사하면
#   인증이 아니라 파라미터 오류(400/코드10)로 나와서 키를 의심조차 안 하게 된다.
#
# 올바른 모양: base64 88자, A-Za-z0-9+/ 와 끝의 == 뿐. % 도 < > 도 없다.
#   점검: head -1 이_파일 | grep -qE '^[A-Za-z0-9+/]+={0,2}$' && echo OK
KEYEOF
  chmod 600 "$WORKSPACE/secrets/tour-api.key"
  echo "▌TourAPI 키 자리 생성: $WORKSPACE/secrets/tour-api.key (아직 비어 있음)"
fi

# 두 커서 파일 다 빈 채로라도 있어야 한다.
# scope-progress.jsonl은 적재가 한 번 성공해야 생기는데, 프롬프트는 이 파일로 계획한다.
# 없으면 첫 실행에서 Codex가 "커서를 못 읽었다"와 "아직 아무것도 안 했다"를 구분 못 한다.
for f in ingested-sources.jsonl scope-progress.jsonl; do
  if [ ! -f "$WORKSPACE/state/$f" ]; then
    : > "$WORKSPACE/state/$f"
    echo "▌빈 커서 파일 생성: state/$f"
  fi
done

cat <<EOF

✅ 준비 완료

  작업 공간   : $WORKSPACE
  적재 jar    : $WORKSPACE/lib/ingest.jar
  적재 명령   : $WORKSPACE/bin/ingest.sh <run_id>
  프롬프트    : $REPO_ROOT/docs/ingest/codex-ingest-prompt.md

⚠ app/backend/src/main 을 고치면 scripts/ingest/build-jar.sh 를 다시 돌릴 것.
  잊으면 ingest.sh가 최신성 가드에 걸려 멈춘다(조용히 옛 코드로 적재하지 않는다).

⚠ --cd $WORKSPACE 를 반드시 붙일 것.
  workspace-write의 쓰기 허용 루트는 cwd다. 리포 안에서 --cd 없이 돌리면
  쓰기 루트가 앱 리포가 되어, 작업 공간을 분리한 의미가 통째로 사라진다.

다음 할 일
  1) 예약 등록 전에 손으로 한 번 돌려볼 것:
       codex exec --cd $WORKSPACE --skip-git-repo-check \\
         --sandbox workspace-write \\
         -c sandbox_workspace_write.network_access=true \\
         < $REPO_ROOT/docs/ingest/codex-ingest-prompt.md
     프롬프트는 인자 대신 stdin으로 넣는다 — 따옴표·한글이 낄 자리를 없앤다.
     --skip-git-repo-check가 필요한 이유: 작업 공간은 git 리포가 아니다(그게 정상이다).
     ✅ 2026-07-31 확인: 위 -c 플래그로 네트워크가 열린다 (Kakao 40건 수집 성공)
  2) 통과하면 codex-ingest-prompt.md 본문을 Codex 앱 [예약된 작업]에 등록
     ⚠ 앱에서도 작업 디렉터리가 $WORKSPACE 인지 확인할 것. 같은 이유다.
EOF
