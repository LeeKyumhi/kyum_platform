// 채팅 카드 인코딩 규약 — 프론트 전용.
// 메시지 content를 "PEERUP::PLACE::{json}"(장소) / "PEERUP::PLAN::{json}"(여행일정·투어코스) 형태로 보내면
// ChatRoom이 카드로 렌더한다. 백엔드는 규약을 모르고 content를 불투명 텍스트로 저장/브로드캐스트한다(전송 경로 무변경).
// 파싱 실패 시 그냥 일반 텍스트로 우아하게 degrade한다.
//
// ⚠️ 카드 소비 지점은 4곳(렌더 분기·자동번역 필터·번역 토글·인박스 프리뷰) — 전부 parseCard/cardPreview로 통일한다.

export const PLACE_PREFIX = "PEERUP::PLACE::";
export const PLAN_PREFIX = "PEERUP::PLAN::";

// ── 장소 카드 (T1/T3) ──
export type PlacePayload = {
  name: string;
  address?: string | null;
  lat: number;
  lng: number;
  url?: string | null;
  /** "place"=검색한 장소, "me"=내 위치(T3) */
  kind?: "place" | "me";
};

// ── 계획 카드 (여행일정 / 투어코스 공유) — 스냅샷 임베드(권한 문제 회피, 공유 시점 고정) ──
export type PlanItem = { name: string; startHour?: number | null; durationHours?: number | null; category?: string | null };
export type PlanDay = { day: number; date?: string | null; items: PlanItem[] };
export type PlanStop = { name: string; category?: string | null };
export type PlanPayload =
  | { kind: "itinerary"; title: string; startDate?: string | null; days: PlanDay[] }
  | { kind: "course"; title: string; date?: string | null; durationHours?: number | null; price?: number | null; currency?: string | null; stops: PlanStop[] };

export function encodePlaceCard(p: PlacePayload): string {
  return PLACE_PREFIX + JSON.stringify(p);
}
export function encodePlanCard(p: PlanPayload): string {
  return PLAN_PREFIX + JSON.stringify(p); // kind가 첫 키 → 잘린 프리뷰에서도 종류 판별 가능
}

/** content가 장소 카드면 payload 반환, 아니면 null. 방어적: 좌표/이름 유효성까지 확인. (내부용 — 외부는 parseCard) */
function parsePlaceCard(content: string | null | undefined): PlacePayload | null {
  if (!content || !content.startsWith(PLACE_PREFIX)) return null;
  try {
    const obj = JSON.parse(content.slice(PLACE_PREFIX.length));
    if (obj && typeof obj.name === "string" && typeof obj.lat === "number" && typeof obj.lng === "number") {
      return obj as PlacePayload;
    }
    return null;
  } catch {
    return null;
  }
}

/** content가 계획 카드면 payload 반환, 아니면 null. (내부용 — 외부는 parseCard) */
function parsePlanCard(content: string | null | undefined): PlanPayload | null {
  if (!content || !content.startsWith(PLAN_PREFIX)) return null;
  try {
    const obj = JSON.parse(content.slice(PLAN_PREFIX.length));
    if (obj && typeof obj.title === "string" && (obj.kind === "itinerary" || obj.kind === "course")) {
      return obj as PlanPayload;
    }
    return null;
  } catch {
    return null;
  }
}

// ── 통합 카드 파서 — 모든 소비 지점이 이걸 쓴다 ──
export type ParsedCard =
  | { kind: "place"; place: PlacePayload }
  | { kind: "plan"; plan: PlanPayload };

export function parseCard(content: string | null | undefined): ParsedCard | null {
  const place = parsePlaceCard(content);
  if (place) return { kind: "place", place };
  const plan = parsePlanCard(content);
  if (plan) return { kind: "plan", plan };
  return null;
}

/**
 * 인박스 미리보기 라벨. DM 미리보기는 80자로 잘려 저장되지만 prefix(+kind)는 맨 앞이라 살아남으므로
 * JSON 파싱 없이 prefix만 확인해 친화적 라벨을 돌려준다. 카드가 아니면 null.
 */
export function cardPreview(
  content: string | null | undefined,
  labels: { place: string; itinerary: string; course: string },
): string | null {
  if (!content) return null;
  if (content.startsWith(PLACE_PREFIX)) return labels.place;
  if (content.startsWith(PLAN_PREFIX)) {
    return content.includes('"kind":"course"') ? labels.course : labels.itinerary;
  }
  return null;
}
