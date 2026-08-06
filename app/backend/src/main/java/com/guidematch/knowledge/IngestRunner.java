package com.guidematch.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * {@code ingest} 프로파일로 뜰 때만 동작하는 배치 진입점. 적재를 마치면 앱을 종료한다.
 *
 * <pre>
 *   SPRING_PROFILES_ACTIVE=ingest gradle bootRun --args='--ingest.run=/path/to/runs/&lt;run_id&gt;'
 * </pre>
 *
 * <p><b>HTTP 엔드포인트로 만들지 않은 이유</b>: 기존 JPA 엔티티·리포지토리를 그대로 재사용하면서
 * 새 인증 표면을 만들지 않고, 프로덕션 백엔드가 로컬 파일 경로에 의존하지 않게 된다.
 * 적재는 개발자 기계에서 도는 배치지 서비스 기능이 아니다.
 */
@Component
@Profile("ingest")
public class IngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final IngestService ingestService;
    private final IngestStateExporter stateExporter;
    private final ApplicationContext context;

    public IngestRunner(IngestService ingestService,
                        IngestStateExporter stateExporter,
                        ApplicationContext context) {
        this.ingestService = ingestService;
        this.stateExporter = stateExporter;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            Path runDir = Path.of(required(args, "ingest.run"));
            IngestService.Counts counts = ingestService.ingest(runDir);

            // 커서 갱신 — 이게 실패하면 다음 실행이 같은 범위를 다시 하지만,
            // record_id 멱등성 덕에 중복이 안 쌓이므로 안전하게 재시도된다
            Path stateFile = args.containsOption("ingest.state")
                    ? Path.of(args.getOptionValues("ingest.state").get(0))
                    : runDir.getParent().getParent().resolve("state").resolve("ingested-sources.jsonl");
            stateExporter.export(stateFile);

            log.info("적재 성공 {}", counts.toMap());
        } catch (Exception e) {
            log.error("적재 실패 — {}", e.toString(), e);
            exitCode = 1;
        }
        final int code = exitCode;
        System.exit(org.springframework.boot.SpringApplication.exit(context, () -> code));
    }

    private static String required(ApplicationArguments args, String name) {
        if (!args.containsOption(name) || args.getOptionValues(name).isEmpty()) {
            throw new IllegalArgumentException("--" + name + " 인자가 필요합니다");
        }
        return args.getOptionValues(name).get(0);
    }
}
