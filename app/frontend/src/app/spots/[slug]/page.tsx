"use client";

// 명소 상세 페이지 — 정적 데이터(src/lib/spots.ts) + 카카오 주변 검색(/api/places/nearby).
// 주변 장소 UI는 /explore 페이지의 카드·칩 패턴을 그대로 재사용한다.
// 사진은 카카오가 아니라 우리 것이다 — 여행자 노트 사진과 수집한 공식 사진을
// 백엔드(PlaceController.attachMedia)가 붙여 준다.

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { getSpot } from "@/lib/spots";
import GuideCard, { type GuideCardData } from "@/components/GuideCard";
import CourseCard, { type CourseCardData } from "@/components/CourseCard";
import { PinIcon, CompassIcon, SearchIcon } from "@/components/icons";
import SaveButton from "@/components/SaveButton";
import PlaceDetailModal from "@/components/PlaceDetailModal";

type Place = {
  id: string;
  name: string;
  category: string | null;
  categoryGroupCode: string | null;
  phone: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  placeUrl: string | null;
  distanceMeters: number | null;
  /** 대표 사진(여행자 사진 우선, 없으면 공식 사진). 없으면 undefined — "0장"은 존재하지 않는다. */
  coverPhotoUrl?: string | null;
  photoCount?: number | null;
  /** 공식 사진(TourAPI) — 상세 모달이 출처 배지와 함께 맨 앞에 띄운다. */
  officialPhotoUrl?: string | null;
  officialPhotoPublisher?: string | null;
};

type NearbyResponse = {
  city: string;
  category: string;
  kakaoEnabled: boolean;
  places: Place[];
};

type FunnelGuide = GuideCardData & { matchScore: number | null };
type FunnelCourse = CourseCardData;

// 라벨은 /explore와 같은 키를 쓴다(le = t.explore) — 같은 카테고리를 두 벌로 번역하지 않는다.
// 관광명소·문화시설이 앞에 있는 이유: 이 섹션은 "명소 옆에 뭐가 있나"에 답하는 자리이고,
// 우리가 사진·지식을 가진 장소도 대부분 그 두 종류다(식당·카페만 두면 화면이 늘 이모지다).
const NEARBY_CATS = [
  { key: "attraction", labelKey: "catAttraction", icon: "🏛️" },
  { key: "culture",    labelKey: "catCulture",    icon: "🎭" },
  { key: "food",       labelKey: "catFood",       icon: "🍜" },
  { key: "cafe",       labelKey: "catCafe",       icon: "☕" },
] as const;

type NearbyCat = (typeof NEARBY_CATS)[number]["key"];

export default function SpotDetailPage() {
  const params = useParams<{ slug: string }>();
  const slug = typeof params?.slug === "string" ? params.slug : "";
  const spot = getSpot(slug);

  const { t, lang } = useLanguage();
  const ls = t.spotDetail;
  const le = t.explore;

  const [category, setCategory] = useState<NearbyCat>("attraction");
  const [places, setPlaces] = useState<Place[]>([]);
  const [kakaoOn, setKakaoOn] = useState(true);
  const [loading, setLoading] = useState(false);
  const [selectedPlace, setSelectedPlace] = useState<Place | null>(null);

  // 이 지역 가이드/코스 — spot.city.en이 KoreanCity의 key와 동일한 표기라 best-effort로 그대로 쓴다
  // (매칭 안 되면 빈 목록 → 섹션 자체를 숨겨 우아하게 저하시킨다)
  const [funnelGuides, setFunnelGuides] = useState<FunnelGuide[]>([]);
  const [funnelCourses, setFunnelCourses] = useState<FunnelCourse[]>([]);
  const [funnelLoaded, setFunnelLoaded] = useState(false);

  useEffect(() => {
    if (!spot) return;
    let cancelled = false;
    setLoading(true);
    api<NearbyResponse>(
      `/api/places/nearby?lat=${spot.latitude}&lng=${spot.longitude}&category=${category}&lang=${lang}`
    )
      .then((res) => {
        if (cancelled) return;
        setPlaces(res.places);
        setKakaoOn(res.kakaoEnabled);
      })
      .catch(() => { if (!cancelled) setPlaces([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [spot, category, lang]);

  useEffect(() => {
    if (!spot) return;
    let cancelled = false;
    setFunnelLoaded(false);
    const cityKey = spot.city.en;
    Promise.allSettled([
      api<FunnelGuide[]>(`/api/guides?city=${encodeURIComponent(cityKey)}&lang=${lang}`, { auth: true }),
      api<FunnelCourse[]>(`/api/courses?city=${encodeURIComponent(cityKey)}`),
    ]).then(([guidesRes, coursesRes]) => {
      if (cancelled) return;
      setFunnelGuides(guidesRes.status === "fulfilled" ? guidesRes.value.slice(0, 4) : []);
      setFunnelCourses(coursesRes.status === "fulfilled" ? coursesRes.value.slice(0, 4) : []);
      setFunnelLoaded(true);
    });
    return () => { cancelled = true; };
  }, [spot, lang]);

  /* slug가 목록에 없으면 not-found 상태 */
  if (!spot) {
    return (
      <main className="page px-4">
        <div className="container-sm">
          <div className="card p-8 py-16 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl shadow-md">
              🧭
            </div>
            <p className="mb-6 text-sm text-stone-500">{ls.notFound}</p>
            <Link href="/explore" className="btn-primary">
              <CompassIcon className="h-4 w-4" /> {ls.goExplore}
            </Link>
          </div>
        </div>
      </main>
    );
  }

  const name = spot.name[lang];
  const cityName = spot.city[lang];
  const activeCat = NEARBY_CATS.find((c) => c.key === category) ?? NEARBY_CATS[0];

  return (
    <main className="page px-4">
      <div className="container-md">
        {/* Back link */}
        <div className="mb-4">
          <Link href="/" className="btn-ghost text-sm">{ls.back}</Link>
        </div>

        {/* ── Hero photo ── */}
        <div className="group relative mb-8 overflow-hidden rounded-[2rem] shadow-xl">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={spot.img}
            alt={name}
            className="h-72 w-full object-cover transition-transform duration-700 group-hover:scale-105 md:h-96"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-black/20 to-transparent" />
          <SaveButton
            target={{ itemType: "PLACE", place: {
              ref: spot.slug, name: spot.name.ko, category: null,
              address: null, lat: spot.latitude, lng: spot.longitude, image: spot.img,
            } }}
            className="absolute right-4 top-4 z-10"
          />
          <div className="absolute bottom-0 left-0 right-0 p-6 text-white md:p-8">
            <p className="flex items-center gap-1.5 text-sm font-medium text-white/85">
              <PinIcon className="h-4 w-4" /> {cityName}
            </p>
            <h1 className="mt-1 text-3xl font-extrabold tracking-tight drop-shadow md:text-4xl">{name}</h1>
          </div>
        </div>

        {/* ── Description ── */}
        <section className="mb-10">
          <h2 className="section-title">{ls.about}</h2>
          <p className="mt-3 max-w-3xl leading-relaxed text-stone-600">
            {spot.description[lang]}
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <Link href={`/guides`} className="btn-secondary text-sm">
              <SearchIcon className="h-4 w-4" /> {ls.findGuide}
            </Link>
            <Link href="/explore" className="btn-ghost text-sm">
              <CompassIcon className="h-4 w-4" /> {ls.goExplore}
            </Link>
          </div>
        </section>

        <div className="divider mb-8" />

        {/* ── Nearby food & cafés ── */}
        <section>
          <h2 className="section-title">{ls.nearbyTitle}</h2>
          <p className="section-subtitle">{ls.nearbySub}</p>

          {/* 카테고리 칩 (explore 패턴 재사용) */}
          <div className="mb-4 mt-4 flex gap-2 overflow-x-auto pb-2">
            {NEARBY_CATS.map((c) => (
              <button
                key={c.key}
                onClick={() => setCategory(c.key)}
                className={category === c.key ? "chip-active" : "chip"}
              >
                {c.icon} {le[c.labelKey]}
              </button>
            ))}
          </div>

          {!kakaoOn && (
            <p className="mb-4 rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700">
              ⚠️ {le.kakaoDisabled}
            </p>
          )}

          {loading ? (
            <div className="flex flex-col gap-3">
              {[1, 2, 3, 4].map((i) => <div key={i} className="card h-24 animate-pulse p-4" />)}
            </div>
          ) : places.length === 0 ? (
            <div className="card p-8 py-16 text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl shadow-md">
                {activeCat.icon}
              </div>
              <p className="text-sm text-stone-500">{le.empty}</p>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {places.map((p) => (
                <div
                  key={p.id}
                  onClick={() => setSelectedPlace(p)}
                  className="card-hover flex cursor-pointer items-start gap-3 p-4"
                >
                  {/* 사진이 있으면 아이콘 대신 사진. 없으면 기존 아이콘 타일 그대로다 —
                      "사진 없음" 자리를 따로 만들지 않는다 (/explore와 같은 마크업). */}
                  {p.coverPhotoUrl ? (
                    <span className="relative mt-0.5 block h-11 w-11 flex-shrink-0 overflow-hidden rounded-xl shadow-sm">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={p.coverPhotoUrl} alt="" className="h-full w-full object-cover" />
                      {p.photoCount != null && p.photoCount > 1 && (
                        <span className="absolute bottom-0 right-0 rounded-tl-lg bg-black/60 px-1 text-[10px] font-semibold text-white">
                          {p.photoCount}
                        </span>
                      )}
                    </span>
                  ) : (
                    <span className="mt-0.5 flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-sky-400 to-cyan-400 text-lg shadow-sm">
                      {activeCat.icon}
                    </span>
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-bold text-stone-900">{p.name}</p>
                    {p.category && (
                      <p className="mt-0.5 truncate text-xs text-stone-400">{p.category}</p>
                    )}
                    {p.address && (
                      <p className="mt-1 flex items-center gap-1 text-sm text-stone-500">
                        <PinIcon className="h-3.5 w-3.5 flex-shrink-0 text-sky-400" />
                        <span className="truncate">{p.address}</span>
                      </p>
                    )}
                    <div className="mt-1.5 flex items-center gap-3 text-xs text-stone-400">
                      {p.distanceMeters != null && (
                        <span className="font-semibold text-sky-500">{(p.distanceMeters / 1000).toFixed(1)}km</span>
                      )}
                      {p.phone && <span>{p.phone}</span>}
                    </div>
                  </div>
                  {/* 카카오맵 링크는 모달 하단 보조 버튼으로 흡수됐다 (Wave 4가 /explore에서 내린 결정).
                      카드가 밖으로 내보내면 여행자 사진·한줄팁에 닿을 길이 사라진다. */}
                  <span className="mt-1 flex-shrink-0 text-xs text-stone-300">›</span>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* ── 이 지역 가이드/코스 (탐색→전환 퍼널) ── */}
        {funnelLoaded && (funnelGuides.length > 0 || funnelCourses.length > 0) && (
          <>
            <div className="divider my-8" />

            {funnelGuides.length > 0 && (
              <section className="mb-10">
                <div className="mb-4 flex items-end justify-between">
                  <div>
                    <h2 className="section-title">{ls.localGuidesTitle}</h2>
                    <p className="section-subtitle">{ls.localGuidesSub}</p>
                  </div>
                  <Link href={`/guides?city=${encodeURIComponent(spot.city.en)}`} className="flex-shrink-0 text-sm font-semibold text-sky-500 hover:underline">
                    {ls.seeAllGuides}
                  </Link>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  {funnelGuides.map((g) => (
                    <GuideCard key={g.id} guide={g} />
                  ))}
                </div>
              </section>
            )}

            {funnelCourses.length > 0 && (
              <section>
                <div className="mb-4 flex items-end justify-between">
                  <div>
                    <h2 className="section-title">{ls.localCoursesTitle}</h2>
                    <p className="section-subtitle">{ls.localCoursesSub}</p>
                  </div>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  {funnelCourses.map((c) => (
                    <CourseCard key={c.id} course={c} />
                  ))}
                </div>
              </section>
            )}
          </>
        )}
      </div>

      {selectedPlace && (
        <PlaceDetailModal place={selectedPlace} onClose={() => setSelectedPlace(null)} />
      )}
    </main>
  );
}
