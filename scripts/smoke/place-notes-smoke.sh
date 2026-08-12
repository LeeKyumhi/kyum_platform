#!/usr/bin/env bash
#
# 장소 노트(사진·한줄팁) 실 DB 스모크.
#
# ★ 단위 테스트가 구조적으로 못 잡는 것만 본다:
#   - ddl-auto가 만든 `place_notes` 테이블과 리포지토리 JPQL의 실제 실행
#     (이 리포에는 @SpringBootTest가 0개라 JPQL은 백엔드가 뜰 때만 파싱된다)
#   - Supabase Storage 실제 업로드 + **공개 URL이 서명 없이 브라우저에서 열리는지**
#   - 위장 파일 거절이 디코딩 단계에서 실제로 일어나는지
#
# 사용법:  bash scripts/smoke/place-notes-smoke.sh <email> <password>
#   - 백엔드가 :8080에 떠 있어야 한다.
#   - 계정은 **이메일 인증이 끝난** 계정이어야 한다(미인증이면 로그인 자체가 403이다).
#
# ⚠ 파이썬을 python3 -c '...' 로 인라인하지 말 것 — 따옴표가 셸 이스케이프와 충돌해
#   SyntaxError가 나는데 그게 "테스트 실패"처럼 보인다. 따옴표를 막은 heredoc(<<'PY')을 쓴다.

set -uo pipefail   # ⚠ -e 를 켜지 않는다: grep/curl 실패에 스크립트가 죽으면 진단이 안 나온다

BASE="${BASE:-http://localhost:8080}"
EMAIL="${1:?사용법: place-notes-smoke.sh <email> <password>}"
PASSWORD="${2:?사용법: place-notes-smoke.sh <email> <password>}"
KAKAO_ID="smoke-$(date +%s)"        # 실행마다 새 장소 — 3개 상한이 이전 실행에 걸리지 않게
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "▶ 토큰 준비 ($EMAIL)"
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
if [ -n "$TOKEN" ]; then ok "로그인"; else
  bad "로그인 — 응답: $(cat "$TMP/login.json")"
  echo; echo "PASS=$pass FAIL=$fail"; exit 1
fi

# 진짜 이미지를 만든다. JPEG 바이트를 손으로 적으면 디코더가 거절해도 "업로드 실패"로만
# 보여 원인을 못 찾는다 — PNG는 zlib만으로 규격대로 만들 수 있어 그런 모호함이 없다.
python3 - "$TMP/t.png" <<'PY'
import sys, struct, zlib

def chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))

w = h = 8
raw = b"".join(b"\x00" + bytes([40, 120, 200] * w) for _ in range(h))   # 8x8 RGB
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw))
       + chunk(b"IEND", b""))
open(sys.argv[1], "wb").write(png)
PY

echo "▶ 노트 등록·조회 (장소 키: $KAKAO_ID)"

# ── 1. 팁만 등록 ─────────────────────────────────────────────
R=$(curl -s -X POST "$BASE/api/places/notes" -H "Authorization: Bearer $TOKEN" \
  -F "kakaoPlaceId=$KAKAO_ID" -F "placeName=스모크카페" -F "tip=스모크 팁")
echo "$R" | grep -q '"id"' && ok "팁 등록" || bad "팁 등록: $R"
NOTE_ID=$(python3 - <<PY
import json
try: print(json.loads('''$R''').get("id", ""))
except Exception: print("")
PY
)

# ── 2. 비로그인 조회에 나온다 ────────────────────────────────
#    (인증 헤더 없이 부른다 — SecurityConfig permitAll이 실제로 걸려 있는지가 이 어서션의 핵심)
curl -s "$BASE/api/places/notes?kakaoPlaceId=$KAKAO_ID" | grep -q '스모크 팁' \
  && ok "비로그인 조회에 노출" || bad "비로그인 조회에 노출 안 됨"

# ── 3. 사진 업로드 (Storage 실제 왕복 + 2크기 생성) ──────────
R=$(curl -s -X POST "$BASE/api/places/notes" -H "Authorization: Bearer $TOKEN" \
  -F "kakaoPlaceId=$KAKAO_ID" -F "placeName=스모크카페" -F "photo=@$TMP/t.png;type=image/png")
echo "$R" | grep -q 'photoThumbUrl' && ok "사진 업로드 + 썸네일 생성" || bad "사진 업로드: $R"

# ── 4. 그 URL이 서명 없이 실제로 열린다 ──────────────────────
#    버킷이 private이면 여기서만 드러난다. 등록 응답만 보면 URL은 멀쩡해 보인다.
PHOTO_URL=$(python3 - <<PY
import json
try: print(json.loads('''$R''').get("photoThumbUrl") or "")
except Exception: print("")
PY
)
if [ -n "$PHOTO_URL" ]; then
  CT=$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$PHOTO_URL")
  case "$CT" in
    "200 image/"*) ok "공개 URL 로드 ($CT)" ;;
    *)             bad "공개 URL이 $CT — 버킷이 비공개거나 경로가 틀렸다" ;;
  esac
else
  bad "photoThumbUrl 없음 — URL 로드 확인 불가"
fi

# ── 5. 위장 파일은 400 ───────────────────────────────────────
echo "MZ not an image" > "$TMP/fake.png"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/places/notes" \
  -H "Authorization: Bearer $TOKEN" -F "kakaoPlaceId=$KAKAO_ID-fake" -F "placeName=위장" \
  -F "photo=@$TMP/fake.png;type=image/png")
[ "$CODE" = "400" ] && ok "위장 파일 400" || bad "위장 파일이 $CODE"

# ── 6. 4번째는 상한으로 막힌다 ───────────────────────────────
curl -s -X POST "$BASE/api/places/notes" -H "Authorization: Bearer $TOKEN" \
  -F "kakaoPlaceId=$KAKAO_ID" -F "placeName=스모크카페" -F "tip=세번째" > /dev/null
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/places/notes" \
  -H "Authorization: Bearer $TOKEN" -F "kakaoPlaceId=$KAKAO_ID" \
  -F "placeName=스모크카페" -F "tip=네번째")
[ "$CODE" = "400" ] && ok "장소별 3개 상한" || bad "4번째가 $CODE (상한 미작동)"

# ── 7. 삭제하면 사라진다 ─────────────────────────────────────
curl -s -X DELETE "$BASE/api/places/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" > /dev/null
curl -s "$BASE/api/places/notes?kakaoPlaceId=$KAKAO_ID" | grep -q '스모크 팁' \
  && bad "삭제 후에도 남아 있다" || ok "삭제 반영"

# ── ⓘ 시드는 이 스모크의 범위가 아니다 ──────────────────────
echo "  ⓘ 시드(places.image_url)는 검증하지 않는다 — v5 재수집 실행 후 아래 질의로 별도 확인:"
echo "      select count(*) from places where image_url is not null;   -- 0보다 커야 한다"

echo
echo "▶ 정리 SQL (실 dev DB에 행이 남는다 — Supabase 콘솔에서 실행)"
echo "    delete from place_notes where kakao_place_id like '$KAKAO_ID%';"
echo "    -- Storage: credentials/place-notes/<userId>/ 아래 이번 실행분 객체도 함께 지울 것"
echo
echo "PASS=$pass FAIL=$fail"
[ "$fail" -eq 0 ]
