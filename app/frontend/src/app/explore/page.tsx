"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";

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
  const { t } = useLanguage();
  const le = t.explore;

  const [city, setCity]         = useState("");
  const [category, setCategory] = useState<string>("attraction");
  const [places, setPlaces]     = useState<Place[]>([]);
  const [kakaoOn, setKakaoOn]   = useState(true);
  const [loading, setLoading]   = useState(false);

  useEffect(() => {
    if (!city) { setPlaces([]); return; }
    let cancelled = false;
    setLoading(true);
    api<PlacesResponse>(`/api/places?city=${encodeURIComponent(city)}&category=${category}`)
      .then((res) => {
        if (cancelled) return;
        setPlaces(res.places);
        setKakaoOn(res.kakaoEnabled);
      })
      .catch(() => { if (!cancelled) setPlaces([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [city, category]);

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center gap-3 mb-2">
          <Link href="/" className="btn-ghost text-sm">{t.common.back}</Link>
          <h1 className="section-title">🗺️ {le.title}</h1>
        </div>
        <p className="text-sm text-gray-400 mb-5">{le.subtitle}</p>

        {/* 도시 선택 */}
        <div className="card p-5 mb-5">
          <CitySelect value={city} onChange={(c) => setCity(c)} />
        </div>

        {!city ? (
          <p className="text-center text-gray-400 text-sm py-10">{le.pickCity}</p>
        ) : (
          <>
            {/* 카테고리 탭 */}
            <div className="flex gap-2 overflow-x-auto pb-2 mb-4">
              {CATEGORIES.map((c) => (
                <button
                  key={c.key}
                  onClick={() => setCategory(c.key)}
                  className={`whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium border transition-all ${
                    category === c.key
                      ? "bg-indigo-600 text-white border-indigo-600 shadow-sm"
                      : "bg-white text-gray-500 border-gray-200 hover:border-indigo-300 hover:text-indigo-600"
                  }`}
                >
                  {c.icon} {le[c.labelKey]}
                </button>
              ))}
            </div>

            {!kakaoOn && (
              <p className="rounded-xl bg-amber-50 border border-amber-100 px-4 py-3 text-sm text-amber-700 mb-4">
                ⚠️ {le.kakaoDisabled}
              </p>
            )}

            {loading ? (
              <p className="text-center text-gray-400 text-sm py-10">{le.loading}</p>
            ) : places.length === 0 ? (
              <p className="text-center text-gray-400 text-sm py-10">{le.empty}</p>
            ) : (
              <div className="flex flex-col gap-3">
                {places.map((p) => (
                  <div key={p.id} className="card p-4 flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold text-gray-900 truncate">{p.name}</p>
                      {p.category && (
                        <p className="text-xs text-gray-400 mt-0.5 truncate">{p.category}</p>
                      )}
                      {p.address && (
                        <p className="text-sm text-gray-500 mt-1 truncate">📍 {p.address}</p>
                      )}
                      <div className="flex items-center gap-3 mt-1.5 text-xs text-gray-400">
                        {p.distanceMeters != null && (
                          <span>{(p.distanceMeters / 1000).toFixed(1)}km</span>
                        )}
                        {p.phone && <span>{p.phone}</span>}
                      </div>
                    </div>
                    {p.placeUrl && (
                      <a
                        href={p.placeUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="btn-secondary text-xs whitespace-nowrap px-3 py-1.5 flex-shrink-0"
                      >
                        {le.openMap}
                      </a>
                    )}
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </main>
  );
}
