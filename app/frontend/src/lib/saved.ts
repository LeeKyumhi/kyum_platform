// 찜(위시리스트) API + 카드 ♡ 상태 공유 캐시.
// CitySelect의 loadCities() 캐시 공유 패턴 — 페이지에서 SaveButton이 몇 개든 /api/saved/ids는 1회만.

import { api, getToken } from "@/lib/api";

export type SavedPlaceSnapshot = {
  ref: string;               // SPOTS slug 또는 "kakao:{placeId}"
  name: string;
  category: string | null;
  address: string | null;
  lat: number | null;
  lng: number | null;
  image: string | null;
};

export type SavedIds = { guideIds: number[]; courseIds: number[]; placeRefs: string[] };

/** 저장/해제 후 모든 SaveButton이 다시 동기화하도록 쏘는 이벤트. */
export const SAVED_CHANGED_EVENT = "peerup-saved-changed";

const EMPTY: SavedIds = { guideIds: [], courseIds: [], placeRefs: [] };

let idsPromise: Promise<SavedIds> | null = null;

export function loadSavedIds(): Promise<SavedIds> {
  if (!getToken()) return Promise.resolve(EMPTY);
  if (!idsPromise) {
    idsPromise = api<SavedIds>("/api/saved/ids", { auth: true }).catch(() => EMPTY);
  }
  return idsPromise;
}

function invalidate() {
  idsPromise = null;
  window.dispatchEvent(new Event(SAVED_CHANGED_EVENT));
}

export type SaveTarget =
  | { itemType: "GUIDE" | "COURSE"; refId: number }
  | { itemType: "PLACE"; place: SavedPlaceSnapshot };

export async function saveItem(target: SaveTarget): Promise<void> {
  await api("/api/saved", { method: "POST", auth: true, body: target });
  invalidate();
}

export async function unsaveItem(target: SaveTarget): Promise<void> {
  const q =
    target.itemType === "PLACE"
      ? `itemType=PLACE&placeRef=${encodeURIComponent(target.place.ref)}`
      : `itemType=${target.itemType}&refId=${target.refId}`;
  await api(`/api/saved?${q}`, { method: "DELETE", auth: true });
  invalidate();
}

/** 서버 counts ids 상한(SavedItemController.MAX_COUNT_IDS)과 동기 — 넘기면 400. */
const COUNTS_CHUNK = 100;

/** 저장수 배치 (공개) — 저장 0건 대상은 응답에 없으므로 `?? 0` 처리. 100개 초과는 청크로 나눠 병합. */
export async function fetchSaveCounts(
  type: "GUIDE" | "COURSE",
  ids: number[]
): Promise<Record<string, number>> {
  if (ids.length === 0) return {};
  const chunks: number[][] = [];
  for (let i = 0; i < ids.length; i += COUNTS_CHUNK) chunks.push(ids.slice(i, i + COUNTS_CHUNK));
  try {
    const results = await Promise.all(
      chunks.map((chunk) =>
        api<Record<string, number>>(`/api/saved/counts?type=${type}&ids=${chunk.join(",")}`)
      )
    );
    return Object.assign({}, ...results);
  } catch {
    return {};
  }
}
