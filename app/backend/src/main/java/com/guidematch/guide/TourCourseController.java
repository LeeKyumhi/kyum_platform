package com.guidematch.guide;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidematch.guide.dto.TourCourseResponse;
import com.guidematch.guide.dto.TourCourseWaypointRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class TourCourseController {

    private final TourCourseService courseService;
    private final ObjectMapper objectMapper;

    public TourCourseController(TourCourseService courseService, ObjectMapper objectMapper) {
        this.courseService = courseService;
        this.objectMapper = objectMapper;
    }

    /** 전체 활성 코스 목록 (공개, 탐색용) — ?city= 필터 가능 */
    @GetMapping("/api/courses")
    public List<TourCourseResponse> listAll(@RequestParam(required = false) String city) {
        return courseService.listAll(city);
    }

    /** 특정 가이드의 활성 코스 목록 (공개, 가이드 상세용) */
    @GetMapping("/api/guides/{guideProfileId}/courses")
    public List<TourCourseResponse> listByGuide(@PathVariable Long guideProfileId) {
        return courseService.listByGuide(guideProfileId);
    }

    /** 내 코스 목록 (가이드) */
    @GetMapping("/api/guide-profiles/me/courses")
    public List<TourCourseResponse> mine(@AuthenticationPrincipal Long userId) {
        return courseService.listMine(userId);
    }

    /** 코스 등록 (가이드, 이미지 선택). waypoints는 JSON 배열 문자열로 전달 (선택). */
    @PostMapping(value = "/api/guide-profiles/me/courses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TourCourseResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam("durationHours") Integer durationHours,
            @RequestParam("price") Integer price,
            @RequestParam("maxPeople") Integer maxPeople,
            @RequestParam("serviceCategory") String serviceCategory,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "waypoints", required = false) String waypointsJson
    ) {
        TourCourseResponse res = courseService.create(userId, title, description, city, durationHours, price,
                maxPeople, serviceCategory, image, parseWaypoints(waypointsJson));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /** 코스 수정 (본인 것만). 메타 + 동선(waypoints) 통째로 교체 — 동선 생략 시 빈 목록으로 비워짐. */
    @PutMapping(value = "/api/guide-profiles/me/courses/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TourCourseResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long courseId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam("durationHours") Integer durationHours,
            @RequestParam("price") Integer price,
            @RequestParam("maxPeople") Integer maxPeople,
            @RequestParam("serviceCategory") String serviceCategory,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "waypoints", required = false) String waypointsJson
    ) {
        return courseService.update(userId, courseId, title, description, city, durationHours, price,
                maxPeople, serviceCategory, image, parseWaypoints(waypointsJson));
    }

    /** 코스 삭제 (본인 것만) */
    @DeleteMapping("/api/guide-profiles/me/courses/{courseId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long courseId
    ) {
        courseService.delete(userId, courseId);
        return ResponseEntity.noContent().build();
    }

    private List<TourCourseWaypointRequest> parseWaypoints(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<TourCourseWaypointRequest>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("동선 데이터 형식이 올바르지 않습니다.");
        }
    }
}
