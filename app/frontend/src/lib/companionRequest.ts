// 동행 예약 카테고리별 요청 필드 규약 (프론트 전용 — 백엔드는 불투명 저장).
// 필드 키는 i18n `companionBooking.*` 라벨 키와 1:1로 일치해야 한다.
export const COMPANION_FIELDS: Record<string, readonly string[]> = {
  MEDICAL_INTERPRETER:  ["hospitalName", "hospitalPurpose"],
  SHOPPING_INTERPRETER: ["shoppingArea", "shoppingItems"],
  DINING_COMPANION:     ["diningFood", "diningRecommend"],
  CAFE_COMPANION:       ["cafeNote"],
  LANGUAGE_EXCHANGE:    ["languageTarget", "languageLevel"],
};

/** 체크박스형 필드 — 값은 "1"(체크) 또는 미포함. */
export const CHECKBOX_FIELDS: ReadonlySet<string> = new Set(["diningRecommend"]);

/** 비어있지 않은 필드만 JSON으로. 전부 비었으면 null(전송 생략). */
export function buildRequestDetails(category: string, fields: Record<string, string>): string | null {
  const keys = COMPANION_FIELDS[category];
  if (!keys) return null;
  const out: Record<string, string> = {};
  keys.forEach((k) => { const v = (fields[k] ?? "").trim(); if (v) out[k] = v; });
  return Object.keys(out).length ? JSON.stringify(out) : null;
}

/** 파싱 실패/비정형이면 null — 호출부는 raw 텍스트로 폴백 표시. */
export function parseRequestDetails(raw: string | null | undefined): Record<string, string> | null {
  if (!raw) return null;
  try {
    const o: unknown = JSON.parse(raw);
    if (o && typeof o === "object" && !Array.isArray(o)
        && Object.values(o).every((v) => typeof v === "string")) {
      return o as Record<string, string>;
    }
  } catch { /* fall through */ }
  return null;
}
