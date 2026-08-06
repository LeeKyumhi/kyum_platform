import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * 적재 전용 DB 롤이 <b>제대로 좁혀졌는지</b> 확인한다.
 *
 * <p>단순히 "접속되네"로 끝내면 안 된다. 이 작업의 목적은 접속이 아니라 <b>격리</b>이므로,
 * 할 수 있어야 하는 것과 <b>할 수 없어야 하는 것</b>을 둘 다 확인한다. 후자가 통과하지 않으면
 * 롤을 만든 의미가 없는데, 그 상태는 밖에서 전혀 티가 안 난다.
 *
 * <p>비밀값은 출력하지 않는다.
 */
public class VerifyDbRole {

    static final String[] KNOWLEDGE = {
            "places", "place_aliases", "place_insights", "place_candidates_unresolved",
            "ingest_runs", "ingest_sources", "recommendation_signals"};

    /** 적재 롤이 절대 읽으면 안 되는 것들. 여기 하나라도 뚫리면 롤 분리가 실패한 것이다. */
    static final String[] FORBIDDEN = {"users", "bookings", "guide_profiles"};

    public static void main(String[] args) throws Exception {
        Map<String, String> env = readEnv(Path.of(args[0]));
        String url = env.get("SUPABASE_DB_URL");
        String user = require(env, "INGEST_DB_USER");
        String pw = require(env, "INGEST_DB_PASSWORD");

        int failures = 0;

        System.out.println("=== 1. 접속 ===");
        try (Connection c = DriverManager.getConnection(url, user, pw)) {
            try (ResultSet rs = c.createStatement().executeQuery("select current_user")) {
                rs.next();
                System.out.println("  ✅ 접속 성공, current_user=" + rs.getString(1));
            }

            System.out.println("\n=== 2. knowledge 테이블 읽기 (되어야 정상) ===");
            for (String t : KNOWLEDGE) {
                try (ResultSet rs = c.createStatement().executeQuery("select count(*) from " + t)) {
                    rs.next();
                    System.out.printf("  ✅ %-30s %d행%n", t, rs.getInt(1));
                } catch (SQLException e) {
                    System.out.printf("  ❌ %-30s %s%n", t, firstLine(e));
                    failures++;
                }
            }

            System.out.println("\n=== 3. 쓰기 (INSERT/UPDATE — 롤백하므로 데이터 안 남음) ===");
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "insert into ingest_runs (run_id, source_kind, status, started_at) values (?,?,?, now())")) {
                    ps.setString(1, "__rolecheck__");
                    ps.setString(2, "check");
                    // status는 IngestRun.Status enum(STARTED/COMPLETED/FAILED)에 CHECK 제약이 걸려 있다.
                    // (Hibernate 6이 enum 컬럼에 자동 생성) 임의 값을 넣으면 권한이 아니라 제약에서 막힌다.
                    ps.setString(3, "STARTED");
                    ps.executeUpdate();
                }
                c.createStatement().executeUpdate(
                        "update ingest_runs set status='FAILED' where run_id='__rolecheck__'");
                System.out.println("  ✅ INSERT · UPDATE 가능");
            } catch (SQLException e) {
                System.out.println("  ❌ 쓰기 실패: " + firstLine(e));
                failures++;
            } finally {
                c.rollback();          // 검증 흔적을 남기지 않는다
                c.setAutoCommit(true);
            }

            System.out.println("\n=== 4. 앱 테이블 읽기 (막혀야 정상) ===");
            for (String t : FORBIDDEN) {
                try (ResultSet rs = c.createStatement().executeQuery("select count(*) from " + t)) {
                    rs.next();
                    System.out.printf("  ❌ %-20s 읽힘! (%d행) — 격리 실패%n", t, rs.getInt(1));
                    failures++;
                } catch (SQLException e) {
                    System.out.printf("  ✅ %-20s 차단됨%n", t);
                }
            }

            System.out.println("\n=== 5. 이 롤에 부여된 권한 전부 ===");
            try (PreparedStatement ps = c.prepareStatement(
                    "select table_name, string_agg(privilege_type, ',' order by privilege_type) " +
                    "from information_schema.table_privileges " +
                    "where grantee = current_user and table_schema='public' group by 1 order by 1")) {
                ResultSet rs = ps.executeQuery();
                Set<String> known = new HashSet<>(Arrays.asList(KNOWLEDGE));
                while (rs.next()) {
                    String t = rs.getString(1);
                    boolean expected = known.contains(t);
                    System.out.printf("  %s %-30s %s%n", expected ? "✅" : "❌", t, rs.getString(2));
                    if (!expected) failures++;   // knowledge 밖 권한은 전부 초과 권한이다
                }
            }
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("✅ 통과 — 적재 롤이 knowledge 7개에만 접근한다");
        } else {
            System.out.println("❌ 실패 " + failures + "건 — docs/ingest/db-role.sql을 다시 확인할 것");
            System.exit(1);
        }
    }

    static String require(Map<String, String> env, String key) {
        String v = env.get(key);
        if (v == null || v.isBlank()) {
            System.err.println("❌ .env에 " + key + " 가 없습니다. docs/ingest/db-role.sql 참고");
            System.exit(2);
        }
        return v;
    }

    static String firstLine(SQLException e) {
        String m = e.getMessage();
        int i = m.indexOf('\n');
        return i < 0 ? m : m.substring(0, i);
    }

    static Map<String, String> readEnv(Path p) throws Exception {
        Map<String, String> env = new HashMap<>();
        for (String line : Files.readAllLines(p)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
            int i = line.indexOf('=');
            String v = line.substring(i + 1).trim();
            if (v.length() > 1 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))))
                v = v.substring(1, v.length() - 1);
            env.put(line.substring(0, i).trim(), v);
        }
        return env;
    }
}
