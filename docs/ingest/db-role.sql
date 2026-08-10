-- 적재 전용 DB 롤 — Supabase SQL Editor에서 실행한다.
--
-- 왜 만드는가:
--   적재 배치는 Codex(자동 실행 에이전트)가 띄운다. 지금은 그 프로세스가 `postgres` 롤로
--   붙으므로, 자격증명이 새면 users·bookings·payments까지 전부 열린다. 적재가 실제로
--   필요한 건 knowledge 테이블 7개뿐이다. 사고가 나도 피해가 거기서 멈추게 만든다.
--
-- 확인된 전제 (2026-07-31, 실DB 조회):
--   - 현재 `postgres` 롤은 superuser는 아니지만 CREATE ROLE 권한이 있다
--   - knowledge 7개 테이블의 id는 전부 IDENTITY(BY DEFAULT) 컬럼이고 public 스키마에
--     시퀀스가 없다 → **시퀀스 GRANT 불필요**. INSERT 권한만으로 id가 생성된다
--   - knowledge 패키지에 DELETE 경로가 하나도 없다 → DELETE는 주지 않는다
--   - 접속은 Supabase 풀러 경유다 → 접속 사용자명은 `peerup_ingest`가 아니라
--     **`peerup_ingest.<project-ref>`** 다 (아래 4번 참고). 이걸 틀리면 비밀번호가
--     틀린 것처럼 보이는 인증 실패가 난다

-- ── 1. 롤 생성 ───────────────────────────────────────────────────────────
-- 비밀번호는 아래 문자열을 바꿔서 쓴다. 생성 예:
--   LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 40; echo
--
-- ⚠ **영숫자만 쓸 것. 특히 `#` 금지.** 취향 문제가 아니다.
--   .env는 spring-dotenv가 읽는데, 따옴표 없는 값에서 `#` 뒤를 주석으로 잘라낸다.
--   그러면 Spring만 잘린 비밀번호로 붙어 28P01 인증 실패가 나고, 그게 500줄 스택트레이스
--   밑에 "Unable to determine Dialect"로 포장돼 나온다. 게다가 verify-db-role.sh는
--   자체 파서라 `#`를 그대로 읽어 **통과해버려서**, "검증은 되는데 적재만 안 되는"
--   추적 불가능한 상태가 된다. (2026-08-05에 실제로 밟았다)
--   ingest.sh 사전점검이 이제 막지만, 애초에 만들지 않는 게 낫다.
create role peerup_ingest with login password 'REPLACE_WITH_GENERATED_PASSWORD';

-- ── 2. 최소 권한 ─────────────────────────────────────────────────────────
grant connect on database postgres to peerup_ingest;
grant usage on schema public to peerup_ingest;

-- SELECT은 왜 필요한가: 해결 사다리가 기존 장소를 찾아야 하고(findByKakaoPlaceId 등),
-- 멱등성이 record_id로 기존 행을 조회해서 성립한다.
-- UPDATE는 재추출 시 최신 값으로 덮어쓰기 위해 필요하다.
-- DELETE는 주지 않는다 — 코드에 삭제 경로가 없다. 나중에 생기면 권한 오류로 즉시 드러나는데,
-- 그건 버그가 아니라 "삭제를 정말 할 건지" 다시 생각하라는 신호다.
grant select, insert, update on
    places,
    place_aliases,
    place_insights,
    place_candidates_unresolved,
    ingest_runs,
    ingest_sources,
    recommendation_signals
to peerup_ingest;

-- ⚠ knowledge 테이블을 새로 추가하면 이 GRANT를 반드시 다시 실행해야 한다.
--   새 테이블은 `postgres`(개발 앱)가 만들고, 소유자가 다르므로 적재 롤에 권한이
--   자동으로 붙지 않는다. ALTER DEFAULT PRIVILEGES도 도움이 안 된다 — 그건 "이 롤이
--   만드는 객체"에만 적용되는데, 테이블을 만드는 건 적재 롤이 아니기 때문이다.
--   빠뜨리면 코드 버그처럼 보이는 permission denied가 난다.

-- ── 3. 검증 — 격리가 실제로 됐는지 확인 (여기가 핵심이다) ────────────────
-- 아래 결과에 knowledge 7개 **말고 다른 테이블이 한 줄이라도 보이면** 격리가 안 된 것이다.
-- (Supabase가 PUBLIC이나 anon/authenticated에 걸어둔 권한을 상속받는 경우가 있다)
select table_name, privilege_type
from information_schema.table_privileges
where grantee = 'peerup_ingest' and table_schema = 'public'
order by table_name, privilege_type;

-- 읽기가 실제로 막혔는지 직접 확인 (0행이어야 정상):
select count(*) as 적재롤이_볼_수_있는_비knowledge_테이블
from information_schema.table_privileges
where grantee = 'peerup_ingest'
  and table_schema = 'public'
  and table_name not in ('places','place_aliases','place_insights',
                         'place_candidates_unresolved','ingest_runs',
                         'ingest_sources','recommendation_signals');

-- ── 4. 접속 사용자명 만들기 ──────────────────────────────────────────────
-- 풀러 경유라 사용자명에 project-ref 접미사가 붙는다.
-- 현재 .env의 SUPABASE_DB_USER가 `postgres.abcdefgh...` 형태이므로,
-- INGEST_DB_USER는 그 점 뒤 부분을 그대로 붙인 `peerup_ingest.abcdefgh...` 다.
-- 터미널에서 정확한 값을 만들려면:
--   grep '^SUPABASE_DB_USER=' app/backend/.env | sed 's/.*postgres\./INGEST_DB_USER=peerup_ingest./'

-- ── 되돌리기 ─────────────────────────────────────────────────────────────
-- revoke all on all tables in schema public from peerup_ingest;
-- revoke all on schema public from peerup_ingest;
-- drop role peerup_ingest;
