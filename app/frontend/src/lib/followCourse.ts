// "이 코스 따라하기" — 코스를 코스 블록 1개짜리 새 일정으로 복사한다.
// TimetableBuilder placeModuleAt의 course 분기와 동일한 아이템 형태
// (placeName=코스 제목, 좌표=첫 waypoint, sourceCourseId로 상세 모달 연결).

import { api } from "@/lib/api";
import type { PlanPayload } from "@/lib/placeCard";

type FollowableCourse = {
  id: number;
  title: string;
  city: string | null;
  durationHours: number;
  waypoints?: { latitude: number | null; longitude: number | null }[];
};

export async function followCourse(c: FollowableCourse): Promise<number> {
  const created = await api<{ id: number }>("/api/itineraries/me", {
    method: "POST", auth: true,
    body: { title: c.title, city: c.city },
  });
  const first = c.waypoints?.[0];
  try {
    await api<{ id: number }>(`/api/itineraries/me/${created.id}`, {
      method: "PUT", auth: true,
      body: {
        title: c.title, city: c.city, startDate: null, endDate: null,
        items: [{
          dayIndex: 1, sortOrder: 0,
          placeId: null, placeName: c.title, category: "tour", address: null,
          latitude: first?.latitude ?? null, longitude: first?.longitude ?? null,
          memo: null,
          startHour: 10,
          durationHours: Math.max(1, Math.min(c.durationHours ?? 2, 12)),
          laneIndex: 0, laneSpan: 1,
          sourceCourseId: c.id,
        }],
      },
    });
  } catch (err) {
    // 아이템 추가 실패 시 방금 만든 빈 일정을 정리(고아 방지) — 정리 실패는 무시하고 원래 에러를 올림
    await api(`/api/itineraries/me/${created.id}`, { method: "DELETE", auth: true }).catch(() => {});
    throw err;
  }
  return created.id;
}

// PEERUP::PLAN 스냅샷(여행 일정 or 투어 코스)을 새 Itinerary로 복사한다("따라하기").
// 스냅샷엔 좌표가 없으므로 위경도는 null — 따라한 일정엔 지도 핀이 없다(v1 한계).
export async function followPlan(plan: PlanPayload): Promise<number> {
  const created = await api<{ id: number }>("/api/itineraries/me", {
    method: "POST", auth: true,
    body: { title: plan.title, city: null },
  });
  type NewItem = {
    dayIndex: number; sortOrder: number; placeId: null; placeName: string;
    category: string | null; address: null; latitude: null; longitude: null;
    memo: null; startHour: number; durationHours: number;
    laneIndex: number; laneSpan: number; sourceCourseId: null;
  };
  const items: NewItem[] = [];
  if (plan.kind === "itinerary") {
    for (const d of plan.days) {
      d.items.forEach((it, idx) => items.push({
        dayIndex: d.day, sortOrder: idx, placeId: null, placeName: it.name,
        category: it.category ?? "tour", address: null, latitude: null, longitude: null,
        memo: null, startHour: it.startHour ?? 10,
        durationHours: Math.max(1, Math.min(it.durationHours ?? 2, 12)),
        laneIndex: 0, laneSpan: 1, sourceCourseId: null,
      }));
    }
  } else {
    plan.stops.forEach((s, idx) => items.push({
      dayIndex: 1, sortOrder: idx, placeId: null, placeName: s.name,
      category: s.category ?? "tour", address: null, latitude: null, longitude: null,
      memo: null, startHour: 10 + idx, durationHours: 1,
      laneIndex: 0, laneSpan: 1, sourceCourseId: null,
    }));
  }
  try {
    await api(`/api/itineraries/me/${created.id}`, {
      method: "PUT", auth: true,
      body: { title: plan.title, city: null, startDate: null, endDate: null, items },
    });
  } catch (err) {
    await api(`/api/itineraries/me/${created.id}`, { method: "DELETE", auth: true }).catch(() => {});
    throw err;
  }
  return created.id;
}
