// "이 코스 따라하기" — 코스를 코스 블록 1개짜리 새 일정으로 복사한다.
// TimetableBuilder placeModuleAt의 course 분기와 동일한 아이템 형태
// (placeName=코스 제목, 좌표=첫 waypoint, sourceCourseId로 상세 모달 연결).

import { api } from "@/lib/api";

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
  await api(`/api/itineraries/me/${created.id}`, {
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
  return created.id;
}
