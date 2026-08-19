"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import DistrictSelect from "@/components/DistrictSelect";
import { PinIcon } from "@/components/icons";
import PlaceDetailModal from "@/components/PlaceDetailModal";
import PageHeader from "@/components/PageHeader";
import EmptyState from "@/components/EmptyState";
import SaveButton from "@/components/SaveButton";

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

type PlacesResponse = {
  city: string;
  category: string;
  kakaoEnabled: boolean;
  places: Place[];
};

const CATEGORIES = [
  { key: "attraction", labelKey: "catAttraction", icon: "🏛️" },
  { key: "food",       labelKey: "catFood",       icon: "🍜" },
  { key: "cafe",       labelKey: "catCafe",       icon: "☕" },
  { key: "culture",    labelKey: "catCulture",    icon: "🎭" },
  { key: "market",     labelKey: "catMarket",     icon: "🛍️" },
] as const;

export default function ExplorePage() {
  const { t, lang } = useLanguage();
  const le = t.explore;

  const [city, setCity]         = useState("");
  const [district, setDistrict] = useState("");
  const [category, setCategory] = useState<string>("attraction");
  const [places, setPlaces]     = useState<Place[]>([]);
  const [kakaoOn, setKakaoOn]   = useState(true);
  const [loading, setLoading]   = useState(false);
  const [selectedPlace, setSelectedPlace] = useState<Place | null>(null);

  useEffect(() => {
    if (!city) { setPlaces([]); return; }
    let cancelled = false;
    setLoading(true);
    const districtParam = district ? `&district=${encodeURIComponent(district)}` : "";
    api<PlacesResponse>(`/api/places?city=${encodeURIComponent(city)}&category=${category}${districtParam}&lang=${lang}`)
      .then((res) => {
        if (cancelled) return;
        setPlaces(res.places);
        setKakaoOn(res.kakaoEnabled);
      })
      .catch(() => { if (!cancelled) setPlaces([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [city, district, category]);

  const activeCat = CATEGORIES.find((c) => c.key === category) ?? CATEGORIES[0];

  return (
    <main className="page px-4">
      <div className="container-sm">
        {/* Header */}
        <PageHeader
          back={{ href: "/", label: t.common.back }}
          title={le.title}
          subtitle={le.subtitle}
        />

        {/* 도시 + 세부 지역(구) 선택 */}
        <div className="card mb-5 flex flex-col gap-2.5 p-5">
          <CitySelect value={city} onChange={(c) => { setCity(c); setDistrict(""); }} />
          <DistrictSelect city={city} value={district} onChange={setDistrict} />
        </div>

        {!city ? (
          <EmptyState icon="🗺️" message={le.pickCity} />
        ) : (
          <>
            {/* 카테고리 탭 */}
            <div className="mb-4 flex gap-2 overflow-x-auto pb-2">
              {CATEGORIES.map((c) => (
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
              <EmptyState icon={activeCat.icon} message={le.empty} />
            ) : (
              <div className="animate-fade-up flex flex-col gap-3">
                {places.map((p) => (
                  <div
                    key={p.id}
                    onClick={() => setSelectedPlace(p)}
                    className="card-hover relative cursor-pointer p-4"
                  >
                    <div className="flex items-start gap-3">
                      {/* 사진이 있으면 아이콘 대신 사진. 없으면 기존 아이콘 타일 그대로다 —
                          "사진 없음" 자리를 따로 만들지 않는다. 개수 배지는 사진 칸 기준이라
                          카드가 아니라 이 span이 relative를 가져야 한다. */}
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
                      <SaveButton
                        target={{ itemType: "PLACE", place: {
                          ref: `kakao:${p.id}`, name: p.name, category: p.category,
                          address: p.address, lat: p.latitude, lng: p.longitude,
                          // 찜 목록은 사진 그리드다(SavedGrid) — 여기서 안 담으면 그 화면은
                          // 우리가 사진을 가진 장소도 영원히 이모지로만 보여준다.
                          // 스냅샷이라 나중에 사진이 생겨도 소급되지 않는다(name·address와 같은 성질).
                          image: p.coverPhotoUrl ?? p.officialPhotoUrl ?? null,
                        } }}
                        className="mt-0.5 flex-shrink-0"
                      />
                      <span className="mt-1 flex-shrink-0 text-xs text-stone-300">›</span>
                    </div>
                  </div>
                ))}
              </div>
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
