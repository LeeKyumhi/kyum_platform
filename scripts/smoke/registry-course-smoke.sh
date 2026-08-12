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
echo "▶ 5. 정차지가 식별자를 싣고 있는가 (버그 픽스 회귀)"
# 식별자가 하나도 없으면 프론트가 "rec-3-경복궁" 같은 값을 합성해 일정·코스에 저장하고,
# 카카오맵 링크가 깨지며 tour_course_waypoints.place_id가 조인 불가능해진다.
#
# ⚠ "모든 정차지에 kakaoPlaceId가 있다"는 불변식이 아니다 — places.kakao_place_id는 nullable이라
#   레지스트리 장소도 비어 있을 수 있다. 합격 기준은 "둘 중 하나는 있다"이다.
rec "city=Seoul&district=$DISTRICT_ENC&theme=culture&lang=ko" > "$TMP/r5.json"
if python3 - "$TMP/r5.json" <<'PY'
import sys, json
d = json.load(open(sys.argv[1]))
stops = d.get("stops", [])
if not stops:
    print("     정차지 0곳 — 판정 불가"); sys.exit(1)
for s in stops:
    print("     %s / kakaoPlaceId=%s / placeId=%s" % (s.get("name"), s.get("kakaoPlaceId"), s.get("placeId")))
anonymous = [s["name"] for s in stops if not s.get("kakaoPlaceId") and not s.get("placeId")]
# 결함은 아니지만 놓치면 안 되는 값: kakao id가 없는 레지스트리 장소는 코스 waypoint
# (카카오 id 스냅샷)와 조인이 안 돼, 가이드가 코스에 넣어도 🎫가 영영 세지 못한다.
gap = [s["name"] for s in stops if not s.get("kakaoPlaceId") and s.get("placeId")]
if gap:
    print("     ⓘ kakao id 없는 레지스트리 장소 %d곳 — 🎫 집계에서 제외됨(수집 빈칸): %s"
          % (len(gap), ", ".join(gap)))
sys.exit(1 if anonymous else 0)
PY
then ok "모든 정차지가 식별자를 가짐 (합성 id 여지 없음)"
else bad "식별자가 전혀 없는 정차지 있음 — 합성 id가 저장된다"
fi

echo
echo "▶ 6. courseRef가 응답에 실리는가 (ADDED 신호 짝짓기)"
if python3 - "$TMP/r5.json" <<'PY'
import sys, json
d = json.load(open(sys.argv[1]))
print("     courseRef=%s" % d.get("courseRef"))
sys.exit(0 if d.get("courseRef") else 1)
PY
then ok "courseRef 존재 — 프론트가 ADDED를 SHOWN과 같은 키로 남길 수 있다"
else bad "courseRef 없음 — ADDED 신호가 어떤 추천에서 왔는지 영영 못 짝짓는다"
fi

echo
echo "▶ 7. 추천 근거(reasons)가 나오는가 (15회)"
# 🎫는 인증 가이드가 담은 코스가 있어야 뜨므로 지금은 대개 0이다(정상).
# 🏛는 인사이트에 발행처가 있어야, 📍는 첫 정차지가 아니어야 뜬다.
KINDS=""
for _ in $(seq 1 15); do
  rec "city=Seoul&district=$DISTRICT_ENC&theme=culture&lang=ko" > "$TMP/r7.json"
  OUT=$(python3 - "$TMP/r7.json" <<'PY'
import sys, json
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    sys.exit(0)
for s in d.get("stops", []):
    for r in (s.get("reasons") or []):
        print("%s | %s | count=%s source=%s walk=%s"
              % (s.get("name"), r.get("kind"), r.get("count"), r.get("source"), r.get("walkMinutes")))
PY
)
  if [ -n "$OUT" ]; then KINDS="$OUT"; break; fi
done
if [ -n "$KINDS" ]; then
  echo "$KINDS" | sed 's/^/     /'
  ok "근거 부착 확인"
else
  # ⚠ 근거 0이 곧 결함은 아니다. 🎫는 인증 가이드 코스가 있어야, 🏛는 발행처가 있어야,
  #    📍는 구간이 1km 이내여야 뜬다. 셋 다 못 뜨는 정상 상태와 조립 버그를 구분하려면
  #    아래 진단이 필요하다 — 이것 없이는 "0"이 무슨 뜻인지 알 수 없다.
  echo "     진단 —"
  python3 - "$TMP/r7.json" <<'PY' | sed 's/^/     /'
import sys, json
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print("응답 파싱 실패"); raise SystemExit
for s in d.get("stops", []):
    ins = s.get("insights") or []
    pubs = [i.get("publisher") for i in ins]
    print("%-16s 구간=%sm · 인사이트 %d · 발행처=%s"
          % (s.get("name"), s.get("distanceFromPrevMeters"), len(ins), pubs or "없음"))
print("해석: 구간이 전부 1km 초과 → 📍 없음(정상) / 발행처가 전부 None → 🏛 없음(수집 문제)")
print("      둘 다 아닌데 0이면 CourseReasons 조립 버그")
PY
  bad "15회 모두 근거 0 — 위 진단으로 원인 구분 필요 (0 자체는 결함이 아닐 수 있음)"
fi

echo
echo "▶ 8. 🏛 근거에 출처가 반드시 붙는가 (TourAPI attribution 의무)"
if python3 - "$TMP/r7.json" <<'PY'
import sys, json
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    sys.exit(1)
bad = []
for s in d.get("stops", []):
    for r in (s.get("reasons") or []):
        if r.get("kind") == "official" and not r.get("source"):
            bad.append(s.get("name"))
        if r.get("kind") == "guide_course" and not r.get("count"):
            bad.append("%s(0명 표시!)" % s.get("name"))
print("     출처 없는 🏛 / 0명 🎫: %s" % (bad or "없음"))
sys.exit(1 if bad else 0)
PY
then ok "출처 없는 근거·0 표시 없음 (규칙2·규칙3)"
else bad "출처 없는 근거 또는 0 표시가 나왔다 — 규칙 위반"
fi

echo
echo "▶ 9. 담기 신호(ADDED) 기록이 200인가"
FIRST=$(python3 - "$TMP/r5.json" <<'PY'
import sys, json
d = json.load(open(sys.argv[1]))
s = (d.get("stops") or [{}])[0]
print(json.dumps({"placeId": s.get("placeId"), "kakaoPlaceId": s.get("kakaoPlaceId"),
                  "courseRef": d.get("courseRef")}))
PY
)
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/courses/recommend/signals" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$FIRST")
if [ "$CODE" = "200" ] || [ "$CODE" = "204" ]; then ok "ADDED 기록 $CODE"
else bad "ADDED 기록 실패 (HTTP $CODE) — 2사이클 🧳의 원천이 비게 된다"; fi

echo
echo "──────────────────────────────────────────"
echo "통과 $pass · 실패 $fail"
[ "$fail" -eq 0 ]
