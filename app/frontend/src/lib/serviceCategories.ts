// 서비스 카테고리 — 백엔드 ServiceCategory enum과 키가 정확히 일치해야 한다.
// requiresLicense=true(관광)는 관광통역안내사 인증(VERIFIED) 가이드만 선택/제공/예약할 수 있다.

export type ServiceCategoryKey =
  | "TOUR_GUIDE"
  | "MEDICAL_INTERPRETER"
  | "DINING_COMPANION"
  | "CAFE_COMPANION"
  | "SHOPPING_INTERPRETER"
  | "LANGUAGE_EXCHANGE";

export type ServiceCategory = { key: ServiceCategoryKey; requiresLicense: boolean };

export const SERVICE_CATEGORIES: ServiceCategory[] = [
  { key: "TOUR_GUIDE",          requiresLicense: true  },
  { key: "MEDICAL_INTERPRETER", requiresLicense: false },
  { key: "DINING_COMPANION",    requiresLicense: false },
  { key: "CAFE_COMPANION",      requiresLicense: false },
  { key: "SHOPPING_INTERPRETER",requiresLicense: false },
  { key: "LANGUAGE_EXCHANGE",   requiresLicense: false },
];

export const TOUR_CATEGORY_KEYS = SERVICE_CATEGORIES.filter((c) => c.requiresLicense).map((c) => c.key);
export const NON_TOUR_CATEGORY_KEYS = SERVICE_CATEGORIES.filter((c) => !c.requiresLicense).map((c) => c.key);

export function categoryRequiresLicense(key: string): boolean {
  return SERVICE_CATEGORIES.find((c) => c.key === key)?.requiresLicense ?? false;
}
