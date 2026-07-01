package com.guidematch.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Supabase Storage(파일 보관소)에 파일을 업로드하는 클라이언트.
 *
 * Supabase는 REST API로 파일을 받는다. 우리는 백엔드에서 service_role 키로 인증해
 * 파일을 올리고, 공개 URL을 돌려받아 DB에 저장한다.
 *
 * RestClient = 스프링이 제공하는 HTTP 요청 도구 (다른 서버에 요청을 보낼 때 사용).
 */
@Component
public class SupabaseStorageClient {

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceKey;

    public SupabaseStorageClient(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-key}") String serviceKey
    ) {
        this.supabaseUrl = supabaseUrl;
        this.serviceKey = serviceKey;
        this.restClient = RestClient.create();
    }

    /**
     * 공개(public) 버킷에 파일을 업로드하고, 누구나 접근 가능한 URL을 반환한다.
     *
     * @param bucket      버킷 이름 (예: "credentials")
     * @param path        버킷 내 저장 경로 (예: "guide-1/uuid.pdf")
     * @param data        파일 내용(바이트)
     * @param contentType 파일 형식 (예: "application/pdf")
     * @return 공개 접근 URL
     */
    public String uploadPublic(String bucket, String path, byte[] data, String contentType) {
        restClient.post()
                .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + path)
                .header("Authorization", "Bearer " + serviceKey)
                .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
                .body(data)
                .retrieve()
                .toBodilessEntity();

        // 공개 버킷의 파일 접근 URL 형식
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
    }
}
