#!/usr/bin/env bash
#
# 레지스트리 기반 코스 추천 스모크 — Phase A 완료 판정.
#
# ★ 합격 기준은 "정차지가 나온다"가 아니라 source="registry" 정차지 ≥ 1이다.
#   Kakao 폴백이 언제나 채워주므로, 백필을 통째로 빠뜨려도 응답은 정상이고
#   결과는 예전과 완전히 동일하다. 그 조용한 실패를 잡는 것이 이 스크립트의 목적이다.
#
# 사용법:  bash scripts/smoke/registry-course-smoke.sh <email> <password>
#   - 백엔드가 :8080에 떠 있어야 한다.
#   - 계정은 **이메일 인증이 끝난** 로컬 계정이어야 한다(미인증이면 로그인 자체가 막힌다).
#
# ⚠ 파이썬을 python3 -c '...' 로 인라인하지 말 것. f-string 안의 따옴표가 셸 이스케이프와
#   충돌해 SyntaxError가 나는데, 그게 "테스트 실패"처럼 보인다(실제로 한 번 밟았다).
#   아래처럼 따옴표를 막은 heredoc(<<'PY')을 쓴다.

set -uo pipefail   # ⚠ -e 를 켜지 않는다: grep/curl 실패에 스크립트가 죽으면 진단이 안 나온다

BASE="${BASE:-http://localhost:8080}"
EMAIL="${1:?사용법: registry-course-smoke.sh <email> <password>}"
PASSWORD="${2:?사용법: registry-course-smoke.sh <email> <password>}"
DISTRICT_ENC="%EC%A4%91%EA%B5%AC"   # 중구
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "▶ 토큰 준비 ($EMAIL)"
# signup은 user 객체를 반환한다(토큰 아님) → 이어서 login으로 accessToken을 받아야 한다.
curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" > "$TMP/login.json"

TOKEN=$(python3 - "$TMP/login.json" <<'PY'
import sys, json
try:
    print(json.load(open(sys.argv[1])).get("accessToken", ""))
except Exception:
    print("")
PY
)

if [ -z "$TOKEN" ]; then
  echo "  ❌ 로그인 실패 — 응답: $(head -c 200 "$TMP/login.json")"
  echo "     이메일 인증이 끝난 계정인지 확인할 것 (미인증 로컬 계정은 로그인이 막힌다)"
  exit 1
fi
ok "accessToken 획득"

rec() { curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/courses/recommend?$1"; }

echo
echo "▶ 1. 구 지정 — source=\"registry\" 정차지가 나오는가 (완료조건 1)"
rec "city=Seoul&district=$DISTRICT_ENC&theme=culture&lang=ko" > "$TMP/r1.json"
if python3 - "$TMP/r1.json" <<'PY'
import sys, json
d = json.load(open(sys.argv[1]))
stops = d.get("stops", [])
reg = [s for s in stops if s.get("source") == "registry"]
print("     정차지 %d곳 · registry %d곳 · kakaoEnabled=%s"
      % (len(stops), len(reg), d.get("kakaoEnabled")))
for s in stops:
    print("       [%s] %s / %s / 주소=%s / 인사이트 %d"
          % (s.get("source"), s.get("name"), s.get("category"),
             s.get("address"), len(s.get("insights") or [])))
sys.exit(0 if reg else 1)
PY
then ok "registry 정차지 ≥ 1"
else bad "registry 정차지 0 — 백필 누락 또는 findCandidates 0건 (place_kind IS NULL 개수 확인)"
fi

echo
echo "▶ 2. 구 미지정 — district=null 경로가 500이 아닌가"
rec "city=Seoul&theme=mixed&lang=ko" > "$TMP/r2.json"
if python3 - "$TMP/r2.json" <<'PY'
import sys, json
d = json.load(open(sys.argv[1]))
assert isinstance(d.get("stops"), list), d
srcs = [s.get("source") for s in d["stops"]]
print("     정차지 %d곳 · district=%s · 출처=%s" % (len(d["stops"]), d.get("district"), srcs))
PY
then ok "구 없는 조회 정상 (JPQL null 파라미터 함정 회피 확인)"
else bad "구 없는 조회 실패 — 백엔드 로그의 SQL 오류 확인"
fi

echo
echo "▶ 3. 축제가 정차지로 나오지 않는가 (15회)"
FESTIVAL=0
for _ in $(seq 1 15); do
  if rec "city=Seoul&district=$DISTRICT_ENC&theme=culture&lang=ko" \
       | grep -qE '명동으로|청계천의 빛|게임문화축제'; then
    FESTIVAL=$((FESTIVAL+1))
  fi
done
if [ "$FESTIVAL" -eq 0 ]; then ok "축제 0회 노출 (place_kind=EVENT 격리 동작)"
else bad "축제가 ${FESTIVAL}/15회 정차지로 나왔다 — EVENT 분류 확인"; fi

echo
echo "▶ 4. 인사이트가 붙은 정차지가 나오는가 (15회 · 완료조건 2 약식)"
# 인사이트 보유 장소는 실측 3곳뿐이고 후보 풀에 섞여 있어, 무작위 선택 때문에 매번은 안 나온다.
HIT=""
for _ in $(seq 1 15); do
  rec "city=Seoul&district=$DISTRICT_ENC&theme=culture&lang=ko" > "$TMP/r4.json"
  OUT=$(python3 - "$TMP/r4.json" <<'PY'
import sys, json
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    sys.exit(0)
for s in d.get("stops", []):
    ins = s.get("insights") or []
    if ins:
        print("%s | %s | %s | %s" % (s.get("source"), s.get("name"),
                                     ins[0].get("kind"), ins[0].get("note")))
PY
)
  if [ -n "$OUT" ]; then HIT="$OUT"; break; fi
done
if [ -n "$HIT" ]; then
  echo "     $HIT"
  ok "인사이트 부착 확인 — 지식이 코스에 도달하는 경로가 실증됨"
else
  bad "15회 모두 인사이트 0 — byPlaceIds 조인과 place_insights.place_id 확인"
fi

echo
echo "──────────────────────────────────────────"
echo "통과 $pass · 실패 $fail"
[ "$fail" -eq 0 ]
