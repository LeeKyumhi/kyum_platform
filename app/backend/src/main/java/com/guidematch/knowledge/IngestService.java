package com.guidematch.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Codex가 만든 실행 디렉터리 하나를 읽어 Postgres에 적재한다.
 *
 * <p><b>트랜잭션을 배치 전체에 걸지 않는다.</b> 줄 단위로 커밋해야 한 줄이 깨져도 나머지
 * 1,829줄이 살아남는다. 배치 전체를 한 트랜잭션으로 묶으면 마지막 줄의 오류가 앞의 모든
 * 성과를 되돌린다 — 25분짜리 수집을 통째로 버리는 셈이다.
 *
 * <p>파싱은 레코드 바인딩이 아니라 트리(JsonNode)로 한다. 계약에 없는 필드가 하나 섞였다고
 * 줄 전체를 버리면 프롬프트를 조금 고칠 때마다 적재가 멈춘다. 대신 필요한 필드만 꺼내 쓰고,
 * 못 꺼내면 <b>구체적인 사유와 함께</b> {@code _rejects.jsonl}로 보낸다.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private final PlaceResolver resolver;
    private final PlaceRepository placeRepo;
    private final PlaceInsightRepository insightRepo;
    private final PlaceCandidateUnresolvedRepository unresolvedRepo;
    private final IngestRunRepository runRepo;
    private final IngestSourceRepository sourceRepo;

    /** 이 미만은 적재하지 않는다. 추측성 사실이 자산을 오염시키는 것보다 비어 있는 게 낫다. */
    private final double minConfidence;

    public IngestService(PlaceResolver resolver,
                         PlaceRepository placeRepo,
                         PlaceInsightRepository insightRepo,
                         PlaceCandidateUnresolvedRepository unresolvedRepo,
                         IngestRunRepository runRepo,
                         IngestSourceRepository sourceRepo,
                         @Value("${ingest.min-confidence:0.5}") double minConfidence) {
        this.resolver = resolver;
        this.placeRepo = placeRepo;
        this.insightRepo = insightRepo;
        this.unresolvedRepo = unresolvedRepo;
        this.runRepo = runRepo;
        this.sourceRepo = sourceRepo;
        this.minConfidence = minConfidence;
    }

    public record Counts(
            int placesResolved,
            int placesUnresolved,
            int insightsUpserted,
            int insightsSkipped,
            int rejects
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("places_resolved", placesResolved);
            m.put("places_unresolved", placesUnresolved);
            m.put("insights_upserted", insightsUpserted);
            m.put("insights_skipped", insightsSkipped);
            m.put("rejects", rejects);
            return m;
        }
    }

    public Counts ingest(Path runDir) throws IOException {
        if (!Files.isDirectory(runDir)) {
            throw new IOException("실행 디렉터리가 없습니다: " + runDir);
        }

        JsonNode manifest = readManifest(runDir);
        String runId = text(manifest.path("run_id"), runDir.getFileName().toString());
        String sourceKind = text(manifest.path("source").path("kind"), null);
        String promptVersion = text(manifest.path("extractor").path("prompt_version"), null);
        String scopeKey = scopeKeyOf(manifest);

        IngestRun run = runRepo.findByRunId(runId).orElseGet(() -> runRepo.save(
                new IngestRun(runId, sourceKind, jsonToMap(manifest.path("scope")), promptVersion)));

        Path rejects = runDir.resolve("_rejects.jsonl");
        Counter c = new Counter();

        try {
            // 장소를 먼저 적재해야 인사이트가 붙을 노드가 존재한다
            eachLine(runDir.resolve("places.jsonl"), (lineNo, raw, node) ->
                    ingestPlace(raw, node, runId, sourceKind, scopeKey, rejects, c));

            eachLine(runDir.resolve("insights.jsonl"), (lineNo, raw, node) ->
                    ingestInsight(raw, node, runId, sourceKind, promptVersion, scopeKey, rejects, c));

            Counts counts = c.snapshot();
            run.complete(counts.toMap());
            runRepo.save(run);
            log.info("적재 완료 run={} {}", runId, counts.toMap());
            return counts;
        } catch (RuntimeException e) {
            run.fail(e.toString());
            runRepo.save(run);
            throw e;
        }
    }

    // ── 장소 ────────────────────────────────────────────────────────

    private void ingestPlace(String raw, JsonNode n, String runId, String manifestSource,
                             String scopeKey, Path rejects, Counter c) {
        String nameRaw = text(n.path("name_raw"), null);
        if (nameRaw == null) {
            reject(rejects, raw, "name_raw 없음", c);
            return;
        }
        String sourceKind = text(n.path("source").path("kind"), manifestSource);

        PlaceClue clue = new PlaceClue(
                nameRaw,
                strings(n.path("aliases")),
                text(n.path("city"), null),
                text(n.path("district"), null),
                decimal(n.path("lat")),
                decimal(n.path("lng")),
                text(n.path("external_ids").path("kakao_place_id"), null),
                text(n.path("external_ids").path("tour_api_content_id"), null),
                text(n.path("category_raw"), null),
                sourceKind);

        PlaceResolver.Resolution r = resolver.resolve(clue);
        if (r.isResolved()) {
            placeRepo.save(r.place());
            c.placesResolved++;
        } else {
            // 추측해서 합치지 않는다 — 원본 그대로 보관해두면 나중에 언제든 해결할 수 있다
            unresolvedRepo.save(new PlaceCandidateUnresolved(
                    text(n.path("record_id"), null), raw, r.unresolvedReason(), runId));
            c.placesUnresolved++;
        }
        touchSource(n.path("source"), sourceKind, scopeKey, runId);
    }

    // ── 인사이트 ─────────────────────────────────────────────────────

    private void ingestInsight(String raw, JsonNode n, String runId, String manifestSource,
                               String promptVersion, String scopeKey, Path rejects, Counter c) {
        String recordId = text(n.path("record_id"), null);
        if (recordId == null) {
            reject(rejects, raw, "record_id 없음 — 멱등성을 보장할 수 없음", c);
            return;
        }

        Optional<FactKind> kind = FactKind.fromWire(text(n.path("fact_kind"), null));
        if (kind.isEmpty()) {
            // 추출기가 새 값을 발명한 경우. 열거형이 무너지면 질의가 불가능해지므로 버린다
            reject(rejects, raw, "알 수 없는 fact_kind: " + text(n.path("fact_kind"), "(없음)"), c);
            return;
        }

        double confidence = n.path("confidence").asDouble(0.0);
        if (confidence < minConfidence) {
            c.insightsSkipped++;
            return;
        }

        JsonNode ref = n.path("place_ref");
        String nameRaw = text(ref.path("name_raw"), null);
        if (nameRaw == null) {
            reject(rejects, raw, "place_ref.name_raw 없음", c);
            return;
        }

        String sourceKind = text(n.path("source_kind"), manifestSource);
        PlaceResolver.Resolution r = resolver.resolve(new PlaceClue(
                nameRaw, List.of(),
                text(ref.path("city"), null), text(ref.path("district"), null),
                decimal(ref.path("lat")), decimal(ref.path("lng")),
                text(ref.path("external_ids").path("kakao_place_id"), null),
                text(ref.path("external_ids").path("tour_api_content_id"), null),
                null, sourceKind));

        if (!r.isResolved()) {
            // 장소를 모르는 사실은 붙일 데가 없다. 버리지 말고 보관 — 나중에 장소가 확정되면 살릴 수 있다
            unresolvedRepo.save(new PlaceCandidateUnresolved(
                    recordId, raw, "insight place_ref: " + r.unresolvedReason(), runId));
            c.placesUnresolved++;
            return;
        }
        Place place = placeRepo.save(r.place());

        JsonNode evidence = n.path("evidence");
        PlaceInsight fresh = new PlaceInsight(
                recordId, place.getId(), kind.get(),
                jsonToMap(n.path("value")),
                noteI18n(n.path("note_i18n")),
                confidence,
                text(evidence.path("url"), null),
                text(evidence.path("publisher"), null),
                date(evidence.path("published_at")),
                sourceKind,
                text(n.path("extracted_by").path("prompt_version"), promptVersion),
                runId);

        // 같은 record_id면 최신 값이 이긴다 (이력은 남기지 않는다 — 설계 확정 사항)
        insightRepo.findByRecordId(recordId).ifPresentOrElse(
                existing -> {
                    existing.overwriteFrom(fresh);
                    insightRepo.save(existing);
                },
                () -> insightRepo.save(fresh));
        c.insightsUpserted++;

        touchSource(evidence, sourceKind, scopeKey, runId);
    }

    // ── 소스 커서 ────────────────────────────────────────────────────

    /** 이 URL을 봤다고 기록한다 — 다음 실행의 커서가 된다. */
    private void touchSource(JsonNode sourceNode, String sourceKind, String scopeKey, String runId) {
        String url = text(sourceNode.path("url"), null);
        if (url == null) return;
        String hash = sha256(url);
        sourceRepo.findByUrlHash(hash).ifPresentOrElse(
                s -> {
                    s.touch(runId);
                    sourceRepo.save(s);
                },
                () -> sourceRepo.save(new IngestSource(hash, url, sourceKind, scopeKey, runId)));
    }

    // ── 파일 · 파싱 도우미 ────────────────────────────────────────────

    private interface LineHandler {
        void handle(int lineNo, String raw, JsonNode node);
    }

    /**
     * 줄 단위 스트리밍. 파일 전체를 메모리에 올리지 않고, 한 줄의 실패가 다음 줄에 번지지 않는다.
     * 파일이 없으면 조용히 건너뛴다 — 장소만 있고 인사이트가 없는 실행도 정상이다.
     */
    private void eachLine(Path file, LineHandler handler) throws IOException {
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                try {
                    handler.handle(lineNo, line, mapper.readTree(line));
                } catch (Exception e) {
                    log.warn("{}:{} 줄 처리 실패 — {}", file.getFileName(), lineNo, e.toString());
                }
            }
        }
    }

    private JsonNode readManifest(Path runDir) throws IOException {
        Path f = runDir.resolve("manifest.json");
        if (!Files.exists(f)) return mapper.createObjectNode();
        return mapper.readTree(Files.readString(f, StandardCharsets.UTF_8));
    }

    /**
     * 거절된 줄은 버리지 않고 사유와 함께 남긴다 — 프롬프트가 어디서 깨지는지 알려주는
     * 유일한 단서이고, 이게 없으면 추출 품질을 개선할 방법이 없다.
     */
    private void reject(Path rejects, String raw, String reason, Counter c) {
        c.rejects++;
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("reason", reason);
            entry.put("line", raw);
            Files.writeString(rejects, mapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("_rejects.jsonl 기록 실패: {}", e.toString());
        }
    }

    private static String scopeKeyOf(JsonNode manifest) {
        String city = text(manifest.path("scope").path("city"), "");
        String district = text(manifest.path("scope").path("district"), "");
        return district.isEmpty() ? city : city + "/" + district;
    }

    private static String text(JsonNode n, String fallback) {
        if (n == null || n.isMissingNode() || n.isNull()) return fallback;
        String s = n.asText();
        return s.isBlank() ? fallback : s;
    }

    private static Double decimal(JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull() || !n.isNumber()) ? null : n.asDouble();
    }

    private static LocalDate date(JsonNode n) {
        String s = text(n, null);
        if (s == null) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> strings(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        arr.forEach(x -> {
            String s = text(x, null);
            if (s != null) out.add(s);
        });
        return out;
    }

    private Map<String, Object> jsonToMap(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull() || !n.isObject()) return null;
        return mapper.convertValue(n, LinkedHashMap.class);
    }

    private Map<String, String> noteI18n(JsonNode n) {
        if (n == null || !n.isObject()) return null;
        Map<String, String> out = new HashMap<>();
        n.fields().forEachRemaining(e -> {
            String v = text(e.getValue(), null);
            if (v != null) out.put(e.getKey(), v);
        });
        return out.isEmpty() ? null : out;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Counter {
        int placesResolved, placesUnresolved, insightsUpserted, insightsSkipped, rejects;

        Counts snapshot() {
            return new Counts(placesResolved, placesUnresolved, insightsUpserted, insightsSkipped, rejects);
        }
    }
}
