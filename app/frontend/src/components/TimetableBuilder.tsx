"use client";

// 드래그 타임테이블 빌더 — 여행자 일정(/trips/[id])과 가이드 코스(/guide/courses) 공용.
// 팔레트(우측)에서 장소/코스/추천을 끌어다 시간표(좌측)에 놓는다.
// items는 부모가 소유(controlled): 부모가 로드/저장, 여기선 배치/이동/삭제만 onItemsChange로 통지.
//   - mode="trip"   : 멀티데이(일차 탭) + 팔레트 3탭 (장소·투어 코스·추천 동선)
//   - mode="course" : 단일 시간표(일차 탭 없음, singleDay) + 팔레트 2탭 (장소·코스 추천)
//   추천 팔레트는 두 모드가 같은 UI를 쓴다 — 여행자용 화면을 따로 만들지 않는다.
// 드래그 수학(포인터 좌표 → hour/lane 매핑)은 Wave 6에서 검증된 로직 그대로 (절대 재구현 금지, 여기 한 곳에만).

import { useEffect, useMemo, useRef, useState } from "react";
import {
  DndContext, DragOverlay, PointerSensor, KeyboardSensor,
  useSensor, useSensors, useDraggable,
  type DragStartEvent, type DragEndEvent,
} from "@dnd-kit/core";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import type { Translations } from "@/lib/i18n";
import { useModalDismiss } from "@/lib/useModalDismiss";
import DistrictSelect from "@/components/DistrictSelect";
import TripMap from "@/components/TripMap";
import { PinIcon } from "@/components/icons";
import PlaceDetailModal, { type ModalPlace, type PlaceInsightView } from "@/components/PlaceDetailModal";

// ── 타임테이블 상수 ──
const DAY_START = 6;   // 06:00 부터
const DAY_END = 24;    // 24:00 까지 (마지막 블록은 23시 시작 가능)
const ROW_PX = 56;     // 1시간 행 높이(px)
const MIN_LANES = 2;   // 같은 시간대에 최소 2칸

export type BuilderItem = {
  _k: string;
  dayIndex: number;
  sortOrder: number;
  placeId: string | null;
  placeName: string;
  category: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  memo: string | null;
  startHour: number | null;
  durationHours: number | null;
  laneIndex: number | null;
  laneSpan: number | null;
  sourceCourseId: number | null;
};

type Place = {
  id: string; name: string; category: string | null; address: string | null;
  latitude: number | null; longitude: number | null;
  phone?: string | null; placeUrl?: string | null; distanceMeters?: number | null;
  /** 추천에서 온 장소 — 담을 때 ADDED 신호를 남기기 위한 표식. 일반 장소 팔레트는 undefined. */
  rec?: { placeId: number | null; kakaoPlaceId: string | null };
  /** 추천 정차지의 수집된 지식. 상세 모달에서만 쓴다. */
  insights?: PlaceInsightView[];
  /** 추천순으로 앞에 세운 이유 (/api/places가 채운다). 없으면 빈 배열. */
  reasons?: RecReason[];
  /** 대표 사진(여행자 사진 우선, 없으면 공식 사진). 없으면 undefined — "0장"은 존재하지 않는다. */
  coverPhotoUrl?: string | null;
  photoCount?: number | null;
  /** 공식 사진(TourAPI) — 상세 모달이 출처 배지와 함께 맨 앞에 띄운다. */
  officialPhotoUrl?: string | null;
  officialPhotoPublisher?: string | null;
};
type PlacesResponse = { kakaoEnabled: boolean; places: Place[] };

type Waypoint = {
  id: number; placeName: string; category: string | null; address: string | null;
  latitude: number | null; longitude: number | null;
};
type Course = {
  id: number; title: string; city: string | null; durationHours: number | null;
  price: number | null; currency: string | null; guideName: string | null;
  waypoints: Waypoint[];
};

// ── 코스 추천 ──
/** 추천 근거. 백엔드는 구조만 내려보내고 문장은 여기서 만든다(ko/en/zh). */
export type RecReason = {
  kind: "guide_course" | "traveler_saved" | "official" | "walk";
  count: number | null; source: string | null; factKind: string | null; walkMinutes: number | null;
};
type RecStop = {
  order: number; name: string; category: string; address: string | null;
  latitude: number | null; longitude: number | null; placeUrl: string | null;
  distanceFromPrevMeters: number | null;
  /** ★ 일정에 담을 때 저장되는 값. 없으면 예전처럼 합성 id가 저장돼 지도 링크가 깨진다. */
  placeId: number | null; kakaoPlaceId: string | null;
  reasons: RecReason[];
  /** 상세 모달용 원본. reasons(앞면 요약)가 이걸 대체하지 않는다. */
  insights: PlaceInsightView[];
  /** 대표 사진(여행자 우선, 없으면 공식) + 공식 사진·발행처. 백엔드가 규칙을 정한다. */
  coverPhotoUrl: string | null;
  photoCount: number | null;
  officialPhotoUrl: string | null;
  officialPhotoPublisher: string | null;
};
export type RecResponse = {
  city: string; district: string | null; theme: string; kakaoEnabled: boolean;
  stops: RecStop[]; totalDistanceMeters: number; suggestedDurationHours: number;
  /** SHOWN↔ADDED 신호를 짝짓는 키. */
  courseRef: string | null;
};
/**
 * 추천 동선은 종류를 고르게 하지 않는다 — 고르는 일은 장소 탭이 맡는다.
 * 서버는 여전히 theme 파라미터를 받으므로(테마별 슬롯 구성이 살아 있다) 필요해지면 되살릴 수 있다.
 */
const REC_THEME = "mixed";

const PLACE_CATS = [
  { key: "attraction", labelKey: "catAttraction", icon: "🏛️" },
  { key: "food",       labelKey: "catFood",       icon: "🍽️" },
  { key: "cafe",       labelKey: "catCafe",       icon: "☕" },
  { key: "culture",    labelKey: "catCulture",    icon: "🎭" },
  { key: "market",     labelKey: "catMarket",     icon: "🛍️" },
] as const;

/** 장소 이름·카테고리로 대표 아이콘을 고른다 (식당🍽️·카페☕·궁궐🏯 등). ko/en/zh 카테고리 문자열 모두 대응. */
export function categoryIcon(name: string, cat: string | null, isTour: boolean): string {
  if (isTour) return "🎫";
  const s = `${name} ${cat ?? ""}`.toLowerCase();
  if (/궁|궁궐|palace|宫|故宫/.test(s)) return "🏯";
  if (/사찰|사원|암자|절\b|temple|寺/.test(s)) return "⛩️";
  if (/카페|커피|coffee|cafe|café|咖啡/.test(s)) return "☕";
  if (/음식|식당|맛집|한식|중식|일식|양식|분식|고기|국밥|치킨|restaurant|food|먹거리|餐|美食/.test(s)) return "🍽️";
  if (/시장|market|市场|市場/.test(s)) return "🛍️";
  if (/박물관|미술|갤러리|공연|극장|문화|museum|art|gallery|theat|文化|博物|艺术/.test(s)) return "🎭";
  if (/공원|park|公园|정원|garden/.test(s)) return "🌳";
  if (/타워|전망|tower|观景|观光|명소|attraction|景点/.test(s)) return "🗼";
  return "📍";
}

/** 카카오맵 링크 — placeId 있으면 장소 상세, 없으면 좌표/검색으로. */
export function kakaoMapUrl(item: { placeId: string | null; placeName: string; latitude: number | null; longitude: number | null }): string {
  if (item.placeId) return `https://place.map.kakao.com/${item.placeId}`;
  if (item.latitude != null && item.longitude != null)
    return `https://map.kakao.com/link/map/${encodeURIComponent(item.placeName)},${item.latitude},${item.longitude}`;
  return `https://map.kakao.com/?q=${encodeURIComponent(item.placeName)}`;
}

/**
 * 드래그 미리보기를 커서에 붙인다.
 *
 * <b>왜 필요한가</b>: dnd-kit의 오버레이는 커서가 아니라 <b>끌기 시작한 노드의 rect</b>에 앵커된다
 * (core의 PositionedOverlay가 top/left/width/height를 원본 rect로 고정하고 드래그 델타만 transform으로 얹는다).
 * 그래서 넓은 팔레트 카드의 오른쪽을 잡으면 미리보기가 커서에서 100px 넘게 떨어진 곳에 뜬다.
 *
 * 더 나쁜 건 <b>드롭 판정은 커서 좌표를 쓴다</b>는 것이다(onDragEnd의 activatorEvent + delta).
 * 즉 미리보기가 실제로 놓일 자리를 거짓말한다. 이 modifier가 그 둘을 일치시킨다.
 *
 * @dnd-kit/modifiers의 snapCenterToCursor와 같은 계산이다. 그 패키지를 설치하지 않는 이유는
 * 이 6줄을 위해 의존성을 늘리지 않기 위해서다(프론트 번들 최소 유지 원칙).
 */
export function snapCenterToCursor({ activatorEvent, draggingNodeRect, transform }: {
  activatorEvent: Event | null;
  draggingNodeRect: { left: number; top: number; width: number; height: number } | null;
  transform: { x: number; y: number; scaleX: number; scaleY: number };
}) {
  const p = activatorEvent as PointerEvent | null;
  // 측정 전에는 rect가 없다 — 여기서 NaN이 새면 첫 프레임에 미리보기가 화면 밖으로 튄다
  if (!draggingNodeRect || !p || typeof p.clientX !== "number") return transform;
  const offsetX = p.clientX - draggingNodeRect.left;
  const offsetY = p.clientY - draggingNodeRect.top;
  return {
    ...transform,
    x: transform.x + offsetX - draggingNodeRect.width / 2,
    y: transform.y + offsetY - draggingNodeRect.height / 2,
  };
}

let keySeq = 0;
export const newItemKey = () => `it_${keySeq++}_${Date.now()}`;

const hourLabel = (h: number) => `${String(h).padStart(2, "0")}:00`;

type Props = {
  items: BuilderItem[];
  onItemsChange: (items: BuilderItem[]) => void;
  city: string;
  /** trip=멀티데이+코스 팔레트, course=단일+추천 팔레트 */
  mode: "trip" | "course";
  /** 코스 모드: 일차 탭/추가 숨김, 단일 시간표 (모든 아이템 dayIndex=1) */
  singleDay?: boolean;
  /** 여행 날짜 범위로 계산한 최소 일수 (trip 모드에서만 의미) */
  minDayCount?: number;
  /** 코스 모드: "폼 채우기" 시 부모(코스 폼)에 추천 결과 전달 */
  onFillFormFromRec?: (rec: RecResponse) => void;
  /** trip 모드: 일차 탭 줄 우측 끝에 렌더할 CTA (예: 동행 파트너 찾기 링크). singleDay(코스 모드)에선 일차 탭 자체가 숨겨지므로 자연히 렌더되지 않음. */
  dayCta?: React.ReactNode;
};

export default function TimetableBuilder({
  items, onItemsChange, city, mode, singleDay = false, minDayCount = 0, onFillFormFromRec, dayCta,
}: Props) {
  const { t, lang } = useLanguage();
  const li = t.itinerary;
  const le = t.explore;
  const tt = t.timetable;
  const lc = t.courses;

  const setItems = (updater: (prev: BuilderItem[]) => BuilderItem[]) => onItemsChange(updater(items));

  const [activeDay, setActiveDay] = useState(1);
  const [extraDays, setExtraDays] = useState(1);
  const [extraLanes, setExtraLanes] = useState<Record<number, number>>({});

  // 팔레트
  // trip 모드는 3탭(장소·투어코스·추천 동선), course 모드는 2탭(장소·코스 추천).
  // "rec"이 두 모드에서 같은 컴포넌트를 쓴다 — 여행자에게만 다른 화면을 만들지 않는다.
  const [paletteTab, setPaletteTab] = useState<"places" | "courses" | "rec">("places");
  const [pickerCat, setPickerCat]   = useState("attraction");
  const [pickerDistrict, setPickerDistrict] = useState("");
  const [places, setPlaces]         = useState<Place[]>([]);
  const [placesLoading, setPlacesLoading] = useState(false);
  const [kakaoOn, setKakaoOn]       = useState(true);
  const [manualName, setManualName] = useState("");
  const [courses, setCourses]       = useState<Course[]>([]);
  const [coursesById, setCoursesById] = useState<Record<number, Course>>({});

  // 코스 추천 (course 모드)
  const [recDistrict, setRecDistrict] = useState("");
  const [recLoading, setRecLoading] = useState(false);
  const [rec, setRec] = useState<RecResponse | null>(null);

  // 모달
  const [detailPlace, setDetailPlace] = useState<ModalPlace | null>(null);
  const [detailCourse, setDetailCourse] = useState<Course | null>(null);

  // 드래그
  const [activeLabel, setActiveLabel] = useState<string | null>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const trayRef = useRef<HTMLDivElement>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor),
  );

  const dayLabel = (n: number) =>
    lang === "ko" ? `${n}일차` : lang === "zh" ? `第${n}天` : `Day ${n}`;

  const dayCount = useMemo(() => {
    if (singleDay) return 1;
    const maxItemDay = items.reduce((m, i) => Math.max(m, i.dayIndex), 1);
    return Math.max(extraDays, minDayCount, maxItemDay, 1);
  }, [singleDay, items, extraDays, minDayCount]);

  const days = useMemo(() => Array.from({ length: dayCount }, (_, i) => i + 1), [dayCount]);
  const hours = useMemo(() => Array.from({ length: DAY_END - DAY_START }, (_, i) => DAY_START + i), []);

  const dayItems = useMemo(() => items.filter((i) => i.dayIndex === activeDay), [items, activeDay]);
  const placed = useMemo(() => dayItems.filter((i) => i.startHour != null), [dayItems]);
  const unplaced = useMemo(() => dayItems.filter((i) => i.startHour == null), [dayItems]);

  const usedLanes = useMemo(
    () => placed.reduce((m, i) => Math.max(m, (i.laneIndex ?? 0) + (i.laneSpan ?? 1)), 0),
    [placed]);
  const laneCount = useMemo(
    () => Math.max(MIN_LANES, usedLanes, extraLanes[activeDay] ?? 0),
    [usedLanes, extraLanes, activeDay]);
  const canRemoveLane = laneCount > Math.max(MIN_LANES, usedLanes);
  function removeLane() {
    if (!canRemoveLane) return;
    setExtraLanes((p) => ({ ...p, [activeDay]: laneCount - 1 }));
  }

  function laneIsFree(hour: number, lane: number, ignoreKey?: string) {
    return !placed.some((i) => {
      if (i._k === ignoreKey) return false;
      const sh = i.startHour ?? 0;
      const eh = sh + (i.durationHours ?? 1);
      const sl = i.laneIndex ?? 0;
      const el = sl + (i.laneSpan ?? 1);
      return hour >= sh && hour < eh && lane >= sl && lane < el;
    });
  }
  function resolveLane(hour: number, preferred: number, ignoreKey?: string) {
    if (laneIsFree(hour, preferred, ignoreKey)) return preferred;
    for (let l = 0; l < laneCount; l++) if (laneIsFree(hour, l, ignoreKey)) return l;
    return laneCount;
  }

  function placeModuleAt(mod: PaletteModule, hour: number, lane: number) {
    const resolvedLane = resolveLane(hour, lane);
    if (resolvedLane >= laneCount) setExtraLanes((p) => ({ ...p, [activeDay]: resolvedLane + 1 }));
    const base = {
      _k: newItemKey(), dayIndex: activeDay, sortOrder: 0, memo: null,
      startHour: hour, laneIndex: resolvedLane, laneSpan: 1,
    };
    if (mod.kind === "course") {
      const c = mod.course;
      setCoursesById((p) => ({ ...p, [c.id]: c }));
      const first = c.waypoints[0];
      setItems((prev) => [...prev, {
        ...base,
        placeId: null, placeName: c.title, category: "tour",
        address: null,
        latitude: first?.latitude ?? null, longitude: first?.longitude ?? null,
        durationHours: Math.max(1, Math.min(c.durationHours ?? 2, DAY_END - hour)),
        sourceCourseId: c.id,
      }]);
    } else {
      const p = mod.place;
      setItems((prev) => [...prev, {
        ...base,
        // 빈 문자열이 저장되면 카카오맵 링크가 깨진다 — 모르면 null이어야 한다
        placeId: p.id || null, placeName: p.name, category: p.category,
        address: p.address, latitude: p.latitude, longitude: p.longitude,
        durationHours: 1, sourceCourseId: null,
      }]);
      if (p.rec) recordAdded(p.rec);
    }
  }

  /**
   * "담았다" 신호. 저장(PUT)까지 기다리면 어떤 항목이 추천에서 왔는지 알 방법이 없어서
   * 담는 순간에만 따로 알린다. 실패해도 사용자 동작을 막지 않는다 — 부가 기록이다.
   */
  function recordAdded(ref: { placeId: number | null; kakaoPlaceId: string | null }) {
    if (!rec?.courseRef) return;
    api("/api/courses/recommend/signals", {
      method: "POST", auth: true,
      body: { placeId: ref.placeId, kakaoPlaceId: ref.kakaoPlaceId, courseRef: rec.courseRef },
    }).catch(() => {});
  }

  function moveBlock(k: string, hour: number, lane: number) {
    const resolvedLane = resolveLane(hour, lane, k);
    if (resolvedLane >= laneCount) setExtraLanes((p) => ({ ...p, [activeDay]: resolvedLane + 1 }));
    setItems((prev) => prev.map((i) => {
      if (i._k !== k) return i;
      const dur = i.durationHours ?? 1;
      const clampedHour = Math.min(hour, DAY_END - 1);
      return { ...i, startHour: clampedHour, laneIndex: resolvedLane,
        durationHours: Math.max(1, Math.min(dur, DAY_END - clampedHour)) };
    }));
  }

  function unplaceBlock(k: string) {
    setItems((prev) => prev.map((i) => (i._k === k ? { ...i, startHour: null, laneIndex: null } : i)));
  }
  function removeItem(k: string) { setItems((prev) => prev.filter((i) => i._k !== k)); }

  function adjustDuration(k: string, delta: number) {
    setItems((prev) => prev.map((i) => {
      if (i._k !== k) return i;
      const sh = i.startHour ?? DAY_START;
      const next = Math.max(1, Math.min((i.durationHours ?? 1) + delta, DAY_END - sh));
      return { ...i, durationHours: next };
    }));
  }
  function adjustSpan(k: string, delta: number) {
    setItems((prev) => prev.map((i) => {
      if (i._k !== k) return i;
      const li0 = i.laneIndex ?? 0;
      const next = Math.max(1, Math.min((i.laneSpan ?? 1) + delta, laneCount - li0));
      return { ...i, laneSpan: next };
    }));
  }

  function addManual() {
    if (!manualName.trim()) return;
    setItems((prev) => [...prev, {
      _k: newItemKey(), dayIndex: activeDay, sortOrder: 0,
      placeId: null, placeName: manualName.trim(), category: null, address: null,
      latitude: null, longitude: null, memo: null,
      startHour: null, durationHours: 1, laneIndex: null, laneSpan: 1, sourceCourseId: null,
    }]);
    setManualName("");
  }

  // 장소 검색
  useEffect(() => {
    if (paletteTab !== "places" || !city) { setPlaces([]); return; }
    let cancelled = false;
    setPlacesLoading(true);
    const districtParam = pickerDistrict ? `&district=${encodeURIComponent(pickerDistrict)}` : "";
    api<PlacesResponse>(`/api/places?city=${encodeURIComponent(city)}&category=${pickerCat}${districtParam}&lang=${lang}`)
      .then((res) => { if (!cancelled) { setPlaces(res.places); setKakaoOn(res.kakaoEnabled); } })
      .catch(() => { if (!cancelled) setPlaces([]); })
      .finally(() => { if (!cancelled) setPlacesLoading(false); });
    return () => { cancelled = true; };
  }, [paletteTab, city, pickerCat, pickerDistrict, lang]);

  // 투어코스 목록 (trip 모드 팔레트) — 도시 기준
  useEffect(() => {
    if (mode !== "trip" || !city) { setCourses([]); return; }
    let cancelled = false;
    api<Course[]>(`/api/courses?city=${encodeURIComponent(city)}`)
      .then((res) => {
        if (cancelled) return;
        setCourses(res);
        setCoursesById((p) => { const n = { ...p }; res.forEach((c) => (n[c.id] = c)); return n; });
      })
      .catch(() => { if (!cancelled) setCourses([]); });
    return () => { cancelled = true; };
  }, [mode, city]);

  // 코스 추천 (course 모드)
  async function fetchRecommend() {
    if (!city) return;
    setRecLoading(true);
    try {
      const params = new URLSearchParams({ city, theme: REC_THEME, lang });
      if (recDistrict) params.set("district", recDistrict);
      const res = await api<RecResponse>(`/api/courses/recommend?${params}`, { auth: true });
      setRec(res);
    } catch { setRec(null); }
    finally { setRecLoading(false); }
  }

  // 추천 정차지 → 팔레트 드래그용 Place 형태.
  // id는 반드시 Kakao 장소 id여야 한다 — 이 값이 그대로 일정/코스에 저장되기 때문이다.
  // 예전에는 `rec-3-경복궁` 같은 값을 합성해 넣었고, 그게 카카오맵 링크를 깨뜨리고
  // tour_course_waypoints.place_id를 조인 불가능한 값으로 오염시켰다.
  const recPlaces: Place[] = useMemo(() =>
    (rec?.stops ?? []).map((s) => ({
      id: s.kakaoPlaceId ?? "", name: s.name, category: s.category,
      address: s.address, latitude: s.latitude, longitude: s.longitude, placeUrl: s.placeUrl,
      rec: { placeId: s.placeId, kakaoPlaceId: s.kakaoPlaceId },
      insights: s.insights ?? [],
      coverPhotoUrl: s.coverPhotoUrl,
      photoCount: s.photoCount,
      officialPhotoUrl: s.officialPhotoUrl,
      officialPhotoPublisher: s.officialPhotoPublisher,
    })), [rec]);

  function onDragStart(e: DragStartEvent) {
    const d = e.active.data.current as ActiveData | undefined;
    if (!d) return;
    setActiveLabel(d.kind === "block" ? d.name : d.kind === "place" ? d.place.name : d.course.title);
  }
  function onDragEnd(e: DragEndEvent) {
    setActiveLabel(null);
    const a = e.active.data.current as ActiveData | undefined;
    if (!a) return;
    const act = e.activatorEvent as PointerEvent;
    const px = (act?.clientX ?? 0) + e.delta.x;
    const py = (act?.clientY ?? 0) + e.delta.y;

    const grid = gridRef.current?.getBoundingClientRect();
    if (grid && px >= grid.left && px <= grid.right && py >= grid.top && py <= grid.bottom) {
      const hour = Math.min(DAY_END - 1, Math.max(DAY_START, DAY_START + Math.floor((py - grid.top) / ROW_PX)));
      const lane = Math.min(laneCount - 1, Math.max(0, Math.floor((px - grid.left) / (grid.width / laneCount))));
      if (a.kind === "block") moveBlock(a._k, hour, lane);
      else if (a.kind === "place") placeModuleAt({ kind: "place", place: a.place }, hour, lane);
      else if (a.kind === "course") placeModuleAt({ kind: "course", course: a.course }, hour, lane);
      return;
    }
    const tray = trayRef.current?.getBoundingClientRect();
    if (tray && px >= tray.left && px <= tray.right && py >= tray.top && py <= tray.bottom) {
      if (a.kind === "block") unplaceBlock(a._k);
    }
  }

  const routePoints = useMemo(() =>
    [...placed]
      .sort((a, b) => (a.startHour! - b.startHour!) || ((a.laneIndex ?? 0) - (b.laneIndex ?? 0)))
      .map((it) => ({ key: it._k, name: it.placeName, latitude: it.latitude, longitude: it.longitude })),
  [placed]);

  /**
   * 팔레트 장소 → 상세 모달 입력.
   *
   * 모달은 노트를 두 식별자로 조회하는데, 레지스트리 id(`rec.placeId`)는 추천 정차지에만
   * 있고 kakao id는 반대로 없을 수도 있다(레지스트리 전용 장소는 `id`가 빈 문자열).
   * 여기서 옮겨 담지 않으면 그런 정차지는 조회 파라미터가 하나도 없어 조용히 빈 목록이 된다.
   */
  function toModalPlace(p: Place): ModalPlace {
    return { ...p, placeId: p.rec?.placeId ?? null };
  }

  function openDetail(it: BuilderItem) {
    if (it.sourceCourseId != null) {
      setDetailCourse(coursesById[it.sourceCourseId] ?? {
        id: it.sourceCourseId, title: it.placeName, city, durationHours: it.durationHours,
        price: null, currency: null, guideName: null, waypoints: [],
      });
    } else {
      setDetailPlace({
        id: it.placeId ?? it._k, name: it.placeName, category: it.category,
        address: it.address, latitude: it.latitude, longitude: it.longitude,
        placeUrl: kakaoMapUrl(it),
      });
    }
  }

  return (
    <>
      {/* 일차 탭 (trip 모드만) */}
      {!singleDay && (
        <div className="mb-4 flex items-center gap-2 overflow-x-auto pb-1">
          <div className="seg-wrap flex-shrink-0">
            {days.map((d) => (
              <button key={d} onClick={() => setActiveDay(d)}
                className={`whitespace-nowrap ${activeDay === d ? "seg-active" : "seg"}`}>
                {dayLabel(d)}
              </button>
            ))}
          </div>
          <button onClick={() => setExtraDays(Math.max(extraDays, dayCount) + 1)}
            className="flex-shrink-0 whitespace-nowrap rounded-full border border-dashed border-stone-300 px-4 py-2 text-sm font-medium text-stone-400 transition-colors hover:border-sky-300 hover:text-sky-500">
            {li.addDay}
          </button>
          {dayCta && <div className="ml-auto flex-shrink-0">{dayCta}</div>}
        </div>
      )}

      <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd}>
        <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start lg:gap-4 xl:grid-cols-[minmax(0,1fr)_24rem]">
        {/* ── 좌측: 미배치 트레이 + 시간표 + 지도 ── */}
        <div className="min-w-0">
        <UnplacedTray innerRef={trayRef} count={unplaced.length} label={tt.unplaced} hint={tt.unplacedHint}>
          {unplaced.map((it) => (
            <PaletteChip key={it._k} id={`block:${it._k}`}
              data={{ kind: "block", _k: it._k, name: it.placeName }}
              label={it.placeName} isTour={it.category === "tour"} isCompanion={it.category === "companion"}
              onInfo={() => openDetail(it)} onRemove={() => removeItem(it._k)} />
          ))}
        </UnplacedTray>

        {/* 타임테이블 */}
        <div className="card mb-4 overflow-hidden p-0">
          <div className="flex items-center justify-between border-b border-stone-100 px-4 py-3">
            <h3 className="flex items-center gap-2 text-sm font-bold text-stone-900">
              🗓️ {tt.timetableTitle}{singleDay ? "" : ` · ${dayLabel(activeDay)}`}
            </h3>
            <div className="flex items-center gap-1.5">
              <button onClick={removeLane} disabled={!canRemoveLane}
                className="rounded-full border border-dashed border-stone-300 px-3 py-1 text-xs font-medium text-stone-400 transition-colors hover:border-stone-400 hover:text-stone-600 disabled:cursor-not-allowed disabled:opacity-40">
                {tt.removeLane}
              </button>
              <button onClick={() => setExtraLanes((p) => ({ ...p, [activeDay]: laneCount + 1 }))}
                className="rounded-full border border-dashed border-stone-300 px-3 py-1 text-xs font-medium text-stone-400 transition-colors hover:border-sky-300 hover:text-sky-500">
                {tt.addLane}
              </button>
            </div>
          </div>
          <div className="flex">
            <div className="w-12 flex-shrink-0 select-none border-r border-stone-100">
              {hours.map((h) => (
                <div key={h} className="relative text-right" style={{ height: ROW_PX }}>
                  <span className="absolute -top-2 right-2 text-[10px] font-medium text-stone-400">{hourLabel(h)}</span>
                </div>
              ))}
            </div>
            <div ref={gridRef} className="relative flex-1" style={{ height: hours.length * ROW_PX }}>
              {hours.map((h, hi) => (
                <div key={h} className="absolute inset-x-0 flex border-b border-stone-100"
                  style={{ top: hi * ROW_PX, height: ROW_PX }}>
                  {Array.from({ length: laneCount }, (_, lane) => (
                    <TimeCell key={lane} hour={h} lane={lane} laneCount={laneCount} />
                  ))}
                </div>
              ))}
              {placed.map((it) => (
                <PlacedBlock key={it._k} item={it} laneCount={laneCount}
                  icon={categoryIcon(it.placeName, it.category, it.category === "tour")}
                  onIcon={() => {
                    if (it.sourceCourseId != null) openDetail(it);
                    else window.open(kakaoMapUrl(it), "_blank", "noopener");
                  }}
                  onInfo={() => openDetail(it)} onRemove={() => removeItem(it._k)}
                  onDur={(d) => adjustDuration(it._k, d)} onSpan={(d) => adjustSpan(it._k, d)}
                  tourBadge={li.tourBadge} mapLabel={tt.openMap} />
              ))}
              {placed.length === 0 && (
                <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                  <p className="text-xs text-stone-300">{tt.dropHint}</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 지도 동선 */}
        <div className="card mb-4 p-5">
          <h3 className="mb-3 flex items-center gap-2 font-bold text-stone-900">
            <PinIcon className="h-5 w-5 text-sky-500" /> {t.tripMap.routeTitle}{singleDay ? "" : ` · ${dayLabel(activeDay)}`}
          </h3>
          <TripMap points={routePoints} className="rounded-2xl" />
        </div>
        </div>{/* ── /좌측 ── */}

        {/* ── 우측: 팔레트 ── */}
        <div className="lg:sticky lg:top-4">
        <div className="card mb-6 p-4 lg:max-h-[calc(100vh-2rem)] lg:overflow-y-auto">
          <div className="mb-3 flex items-center gap-2">
            <button onClick={() => setPaletteTab("places")}
              className={`${paletteTab === "places" ? "chip-active" : "chip"} px-3 py-1.5 text-xs`}>
              📍 {tt.palettePlaces}
            </button>
            {mode === "trip" && (
              <button onClick={() => setPaletteTab("courses")}
                className={`${paletteTab === "courses" ? "chip-active" : "chip"} px-3 py-1.5 text-xs`}>
                🎫 {tt.paletteCourses}
              </button>
            )}
            <button onClick={() => setPaletteTab("rec")}
              className={`${paletteTab === "rec" ? "chip-active" : "chip"} px-3 py-1.5 text-xs`}>
              ✨ {mode === "trip" ? lc.recTravelerTitle : lc.recTitle}
            </button>
            <span className="ml-auto text-[11px] text-stone-400">{tt.dragHint}</span>
          </div>

          {paletteTab === "places" ? (
            !city ? (
              <p className="py-4 text-center text-xs text-stone-400">{li.pickCityForPlaces}</p>
            ) : (
              <>
                <div className="mb-3 flex gap-2">
                  <input value={manualName} onChange={(e) => setManualName(e.target.value)}
                    onKeyDown={(e) => { if (e.key === "Enter") addManual(); }}
                    placeholder={li.manualAddPh} className="input flex-1 text-sm" />
                  <button onClick={addManual} disabled={!manualName.trim()}
                    className="btn-secondary px-4 text-sm disabled:opacity-50">{li.add}</button>
                </div>
                <DistrictSelect city={city} value={pickerDistrict} onChange={setPickerDistrict} className="mb-2 text-sm" />
                <div className="mb-2 flex gap-1.5 overflow-x-auto pb-2">
                  {PLACE_CATS.map((c) => (
                    <button key={c.key} onClick={() => setPickerCat(c.key)}
                      className={`${pickerCat === c.key ? "chip-active" : "chip"} px-3 py-1.5 text-xs`}>
                      {c.icon} {le[c.labelKey]}
                    </button>
                  ))}
                </div>
                {!kakaoOn && (
                  <p className="mb-2 rounded-xl border border-amber-100 bg-amber-50 px-3 py-2 text-xs text-amber-700">
                    ⚠️ {le.kakaoDisabled}
                  </p>
                )}
                {placesLoading ? (
                  <p className="py-4 text-center text-xs text-stone-400">{le.loading}</p>
                ) : places.length === 0 ? (
                  <p className="py-4 text-center text-xs text-stone-400">{le.empty}</p>
                ) : (
                  <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-1">
                    {places.map((p) => (
                      // 목록은 추천순이다 — 앞에 세운 이유를 카드에 함께 보여줘야
                      // "왜 이게 위에 있지?"에 답이 된다. 근거가 없으면 아무것도 렌더하지 않는다.
                      <div key={p.id} className="flex flex-col gap-1">
                        <PaletteCard id={`place:${p.id}`}
                          data={{ kind: "place", place: p }}
                          icon={categoryIcon(p.name, p.category, false)}
                          label={p.name} sub={p.address} detailLabel={li.detailsBtn}
                          photoUrl={p.coverPhotoUrl} photoCount={p.photoCount}
                          onInfo={() => setDetailPlace(toModalPlace(p))} />
                        <ReasonChips reasons={p.reasons ?? []} lc={lc} />
                      </div>
                    ))}
                  </div>
                )}
              </>
            )
          ) : paletteTab === "courses" ? (
            // 투어코스 팔레트 (trip 모드 전용 — 가이드가 파는 상품)
            !city ? (
              <p className="py-4 text-center text-xs text-stone-400">{li.pickCityForPlaces}</p>
            ) : courses.length === 0 ? (
              <p className="py-4 text-center text-xs text-stone-400">{tt.noCourses}</p>
            ) : (
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-1">
                {courses.map((c) => (
                  <PaletteCard key={c.id} id={`course:${c.id}`}
                    data={{ kind: "course", course: c }} icon="🎫"
                    label={c.title} sub={c.guideName ?? undefined} isTour detailLabel={li.detailsBtn}
                    onInfo={() => setDetailCourse(c)} />
                ))}
              </div>
            )
          ) : (
            // 추천 팔레트 — 가이드(코스 추천)와 여행자(추천 동선)가 같은 UI를 쓴다
            !city ? (
              <p className="py-4 text-center text-xs text-stone-400">{lc.recPickCity}</p>
            ) : (
              <div className="flex flex-col gap-2.5">
                <p className="text-xs text-stone-500">{lc.recDragHint}</p>
                <DistrictSelect city={city} value={recDistrict} onChange={setRecDistrict} className="text-sm" />
                <button type="button" onClick={fetchRecommend} disabled={recLoading}
                  className="btn-primary py-2 text-sm disabled:opacity-50">
                  {recLoading ? lc.recLoading : (rec ? `🔄 ${lc.recAgain}` : `✨ ${lc.recBtn}`)}
                </button>

                {rec && !recLoading && (rec.stops.length === 0 ? (
                  <p className="rounded-xl border border-amber-100 bg-amber-50 px-3 py-2 text-xs text-amber-700">{lc.recEmpty}</p>
                ) : (
                  <>
                    <p className="text-[11px] text-stone-500">
                      {lc.recTotalWalk} <b>{(rec.totalDistanceMeters / 1000).toFixed(1)}km</b>
                      <span className="mx-1 text-stone-300">·</span>
                      ~{rec.suggestedDurationHours}{lc.hoursUnit}
                    </p>
                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-1">
                      {recPlaces.map((p, i) => (
                        // 근거가 없는 정차지에는 아무것도 렌더하지 않는다 — 빈 자리를 남기면
                        // 첫 정차지(이전 거리가 없어 구조적으로 📍를 못 받는다)가 고장난 것처럼 보인다
                        <div key={`${i}-${p.id}`} className="flex flex-col gap-1">
                          <PaletteCard id={`place:${i}:${p.id}`}
                            data={{ kind: "place", place: p }}
                            icon={categoryIcon(p.name, p.category, false)}
                            label={`${i + 1}. ${p.name}`} sub={p.address} detailLabel={li.detailsBtn}
                            photoUrl={p.coverPhotoUrl} photoCount={p.photoCount}
                            onInfo={() => setDetailPlace(toModalPlace(p))} />
                          <ReasonChips reasons={rec.stops[i]?.reasons ?? []} lc={lc} />
                          <VibeLine insights={rec.stops[i]?.insights ?? []} />
                        </div>
                      ))}
                    </div>
                    {onFillFormFromRec && (
                      <button type="button" onClick={() => onFillFormFromRec(rec)}
                        className="btn-secondary py-2 text-sm">⬇️ {lc.recUse}</button>
                    )}
                  </>
                ))}
              </div>
            )
          )}
        </div>
        </div>{/* ── /우측 ── */}
        </div>{/* ── /좌우 그리드 ── */}

        {/*
          오버레이 박스는 원본 카드 크기 그대로다(dnd-kit이 그렇게 만든다).
          modifier가 그 박스의 "중심"을 커서에 맞추므로, 칩도 박스 중앙에 놓아야
          칩 자체가 커서에 붙는다. 좌상단에 두면 modifier를 넣고도 여전히 어긋난다.
        */}
        <DragOverlay dropAnimation={null} modifiers={[snapCenterToCursor]}>
          {activeLabel ? (
            <div className="flex h-full w-full items-center justify-center">
              <span className="whitespace-nowrap rounded-xl bg-sky-500 px-3 py-2 text-xs font-bold text-white shadow-lg">
                {activeLabel}
              </span>
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>

      {detailPlace && (
        <PlaceDetailModal place={detailPlace} onClose={() => setDetailPlace(null)} />
      )}
      {detailCourse && (
        <CoursePreviewModal course={detailCourse} onClose={() => setDetailCourse(null)}
          waypointsLabel={tt.coursePreview} />
      )}
    </>
  );
}

// ── 드래그 데이터 타입 ──
type PaletteModule = { kind: "place"; place: Place } | { kind: "course"; course: Course };
type ActiveData =
  | { kind: "block"; _k: string; name: string }
  | { kind: "place"; place: Place }
  | { kind: "course"; course: Course };

function TimeCell({ hour, lane, laneCount }: { hour: number; lane: number; laneCount: number }) {
  return (
    <div data-testid={`cell-${hour}-${lane}`} style={{ width: `${100 / laneCount}%` }}
      className="h-full border-r border-stone-50" />
  );
}

function PlacedBlock({ item, laneCount, icon, onIcon, onInfo, onRemove, onDur, onSpan, tourBadge, mapLabel }: {
  item: BuilderItem; laneCount: number; icon: string; onIcon: () => void; onInfo: () => void; onRemove: () => void;
  onDur: (d: number) => void; onSpan: (d: number) => void; tourBadge: string; mapLabel: string;
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `block:${item._k}`, data: { kind: "block", _k: item._k, name: item.placeName },
  });
  const isTour = item.category === "tour";
  const isCompanion = item.category === "companion";
  const sh = item.startHour ?? DAY_START;
  const dur = item.durationHours ?? 1;
  const laneIdx = item.laneIndex ?? 0;
  const span = item.laneSpan ?? 1;
  return (
    <div ref={setNodeRef} data-testid="placed-block"
      style={{
        position: "absolute",
        top: (sh - DAY_START) * ROW_PX + 2,
        height: dur * ROW_PX - 4,
        left: `calc(${(laneIdx / laneCount) * 100}% + 2px)`,
        width: `calc(${(span / laneCount) * 100}% - 4px)`,
        opacity: isDragging ? 0.4 : 1,
      }}
      className={`group flex flex-col overflow-hidden rounded-xl border px-1.5 py-1 text-left shadow-sm ${
        isTour ? "border-amber-200 bg-amber-50" : "border-sky-200 bg-sky-50"
      }`}>
      <div className="flex min-h-0 flex-1 items-start gap-1.5">
        <button onClick={onIcon} title={isTour ? tourBadge : mapLabel}
          className={`mt-0.5 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg text-base shadow-sm transition-transform hover:scale-110 ${
            isTour ? "bg-amber-100" : isCompanion ? "bg-sky-100" : "bg-white"
          }`}>
          {icon}
        </button>
        <button {...listeners} {...attributes} onClick={onInfo}
          className="min-h-0 min-w-0 flex-1 cursor-grab touch-none self-stretch text-left active:cursor-grabbing">
          <p className="truncate text-xs font-bold text-stone-900">{item.placeName}</p>
          {isTour ? (
            <p className="truncate text-[10px] font-semibold text-amber-600">🎫 {tourBadge}</p>
          ) : !isCompanion && item.category && (
            <p className="truncate text-[10px] text-stone-400">{item.category}</p>
          )}
        </button>
        <button onClick={onRemove}
          className="flex h-4 w-4 flex-shrink-0 items-center justify-center rounded text-stone-300 hover:bg-red-50 hover:text-red-500">✕</button>
      </div>
      <div className="mt-0.5 flex items-center gap-1 text-stone-400">
        <span className="text-[9px]">⇕</span>
        <StepBtn onClick={() => onDur(-1)}>−</StepBtn>
        <span className="w-4 text-center text-[9px] font-bold text-stone-500">{dur}</span>
        <StepBtn onClick={() => onDur(1)}>＋</StepBtn>
        <span className="ml-1 text-[9px]">⇔</span>
        <StepBtn onClick={() => onSpan(-1)}>−</StepBtn>
        <span className="w-4 text-center text-[9px] font-bold text-stone-500">{span}</span>
        <StepBtn onClick={() => onSpan(1)}>＋</StepBtn>
      </div>
    </div>
  );
}

function StepBtn({ children, onClick }: { children: React.ReactNode; onClick: () => void }) {
  return (
    <button onClick={onClick}
      className="flex h-4 w-4 items-center justify-center rounded bg-white/70 text-[10px] font-bold text-stone-500 shadow-sm hover:bg-white hover:text-sky-600">
      {children}
    </button>
  );
}

function PaletteChip({ id, data, label, sub, isTour, isCompanion, onInfo, onRemove }: {
  id: string; data: ActiveData; label: string; sub?: string | null; isTour?: boolean; isCompanion?: boolean;
  onInfo?: () => void; onRemove?: () => void;
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id, data });
  return (
    <div ref={setNodeRef} style={{ opacity: isDragging ? 0.4 : 1 }}
      className={`flex max-w-full items-center gap-1 rounded-full border px-1 py-1 shadow-sm ${
        isTour ? "border-amber-200 bg-amber-50" : isCompanion ? "border-sky-200 bg-sky-50" : "border-stone-200 bg-white"
      }`}>
      <button {...listeners} {...attributes} data-testid="palette-chip"
        className="flex min-w-0 cursor-grab touch-none items-center gap-1 pl-2 active:cursor-grabbing">
        <span className="truncate text-xs font-semibold text-stone-700">{label}</span>
        {sub && <span className="hidden truncate text-[10px] text-stone-400 sm:inline">· {sub}</span>}
      </button>
      {onInfo && (
        <button onClick={onInfo} className="flex-shrink-0 px-1 text-xs text-stone-300 hover:text-sky-500">ⓘ</button>
      )}
      {onRemove && (
        <button onClick={onRemove} className="flex-shrink-0 px-1 text-xs text-stone-300 hover:text-red-500">✕</button>
      )}
    </div>
  );
}

function PaletteCard({ id, data, icon, label, sub, isTour, onInfo, detailLabel, photoUrl, photoCount }: {
  id: string; data: ActiveData; icon: string; label: string; sub?: string | null; isTour?: boolean;
  onInfo?: () => void; detailLabel: string;
  /** 여행자가 올린 대표 사진. 있으면 아이콘 자리를 대신한다. */
  photoUrl?: string | null; photoCount?: number | null;
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id, data });
  return (
    <div ref={setNodeRef} style={{ opacity: isDragging ? 0.4 : 1 }}
      className={`flex flex-col gap-1.5 rounded-2xl border p-2 shadow-sm transition-colors ${
        isTour ? "border-amber-200 bg-amber-50 hover:border-amber-300" : "border-stone-200 bg-white hover:border-sky-300"
      }`}>
      <button {...listeners} {...attributes} data-testid="palette-chip"
        className="flex min-w-0 cursor-grab touch-none items-center gap-2 text-left active:cursor-grabbing">
        {photoUrl ? (
          <span className="relative block h-9 w-9 flex-shrink-0 overflow-hidden rounded-xl shadow-sm">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={photoUrl} alt="" className="h-full w-full object-cover" />
            {photoCount != null && photoCount > 1 && (
              <span className="absolute bottom-0 right-0 rounded-tl-md bg-black/60 px-0.5 text-[9px] font-semibold text-white">
                {photoCount}
              </span>
            )}
          </span>
        ) : (
          <span className={`flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl text-lg shadow-sm ${
            isTour ? "bg-amber-100" : "bg-sky-50"
          }`}>{icon}</span>
        )}
        <span className="min-w-0 flex-1">
          <span className="block truncate text-xs font-bold text-stone-800">{label}</span>
          {sub && <span className="block truncate text-[10px] text-stone-400">{sub}</span>}
        </span>
      </button>
      {onInfo && (
        <button onClick={onInfo}
          className="flex w-full items-center justify-center gap-1 rounded-lg bg-stone-50 py-2 text-xs font-semibold text-stone-600 transition-colors hover:bg-sky-50 hover:text-sky-600">
          ⓘ {detailLabel}
        </button>
      )}
    </div>
  );
}

/**
 * 추천 근거 — "왜 여기인가"를 한 줄로.
 *
 * 근거가 없으면 <b>아무것도 렌더하지 않는다</b>. 백엔드가 0인 근거를 아예 안 내려보내므로
 * 여기서 "0명이 담았어요" 같은 문구가 만들어질 여지 자체가 없다.
 * 문구를 백엔드가 아니라 여기서 만드는 이유는 ko/en/zh 세 언어가 필요하기 때문이다.
 */
function ReasonChips({ reasons, lc }: { reasons: RecReason[]; lc: Translations["courses"] }) {
  if (reasons.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1 px-0.5">
      {reasons.map((r, i) => {
        const [icon, text] = reasonText(r, lc);
        if (!text) return null;
        return (
          <span key={i}
            className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${
              r.kind === "guide_course" ? "bg-amber-50 text-amber-700"
                : r.kind === "official" ? "bg-emerald-50 text-emerald-700"
                : "bg-stone-100 text-stone-500"
            }`}>
            <span aria-hidden>{icon}</span>{text}
          </span>
        );
      })}
    </div>
  );
}

/**
 * 분위기 한 줄 — 근거가 아니라 묘사다. 그래서 배지가 아니라 회색 한 줄로 둔다.
 * 출처 없는 사실은 띄우지 않는 규칙이 여기에도 그대로 적용된다.
 */
function VibeLine({ insights }: { insights: PlaceInsightView[] }) {
  const vibe = insights.find((i) => i.kind === "vibe" && i.note && i.publisher);
  if (!vibe) return null;
  return <p className="px-1 text-[10px] italic leading-snug text-stone-400">“{vibe.note}”</p>;
}

/** 근거 하나 → [아이콘, 문장]. 모르는 종류는 빈 문자열 — 억지로 문구를 만들지 않는다. */
function reasonText(r: RecReason, lc: Translations["courses"]): [string, string] {
  if (r.kind === "guide_course") {
    // 1명일 때 "1명"은 근거로서 약하게 읽힌다. 사실을 바꾸지 않으면서 숫자만 뺀다.
    const n = r.count ?? 0;
    return ["🎫", n <= 1 ? lc.reasonGuideOne : lc.reasonGuideMany.replace("{n}", String(n))];
  }
  if (r.kind === "traveler_saved") {
    return ["🧳", lc.reasonTravelers.replace("{n}", String(r.count ?? 0))];
  }
  if (r.kind === "official") {
    if (!r.source) return ["", ""];   // 출처 없는 자료는 배지가 될 수 없다
    const fact = r.factKind ? (lc.reasonFacts[r.factKind as keyof typeof lc.reasonFacts] ?? "") : "";
    return ["🏛", lc.reasonOfficialFmt.replace("{source}", r.source).replace("{fact}", fact).replace(/ · $/, "")];
  }
  if (r.kind === "walk" && r.walkMinutes != null) {
    return ["📍", lc.reasonWalk.replace("{n}", String(r.walkMinutes))];
  }
  return ["", ""];
}

function UnplacedTray({ innerRef, count, label, hint, children }: {
  innerRef: React.Ref<HTMLDivElement>; count: number; label: string; hint: string; children: React.ReactNode;
}) {
  return (
    <div ref={innerRef} className="card mb-4 p-3">
      <div className="mb-2 flex items-center gap-2">
        <span className="text-xs font-bold text-stone-500">📥 {label}</span>
        <span className="text-[11px] text-stone-400">{hint}</span>
      </div>
      {count === 0 ? (
        <p className="py-1 text-center text-[11px] text-stone-300">—</p>
      ) : (
        <div className="flex flex-wrap gap-2">{children}</div>
      )}
    </div>
  );
}

function CoursePreviewModal({ course, onClose, waypointsLabel }: {
  course: Course; onClose: () => void; waypointsLabel: string;
}) {
  useModalDismiss(onClose);
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="w-full max-w-md rounded-t-2xl bg-white p-5 shadow-xl sm:rounded-2xl">
        <div className="mb-3 flex items-start justify-between gap-2">
          <div>
            <h2 className="text-base font-bold text-stone-900">🎫 {course.title}</h2>
            {course.guideName && <p className="text-xs text-stone-400">{course.guideName}</p>}
          </div>
          <button onClick={onClose} className="text-stone-400 hover:text-stone-600">✕</button>
        </div>
        <p className="mb-2 text-xs font-semibold text-stone-500">{waypointsLabel}</p>
        {course.waypoints.length === 0 ? (
          <p className="py-2 text-sm text-stone-400">—</p>
        ) : (
          <ol className="space-y-1.5">
            {course.waypoints.map((w, i) => (
              <li key={w.id} className="flex items-center gap-2 text-sm text-stone-700">
                <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-amber-100 text-[10px] font-bold text-amber-700">{i + 1}</span>
                <span className="truncate">{w.placeName}</span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}
