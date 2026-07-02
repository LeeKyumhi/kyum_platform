"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export type City = {
  key: string;
  nameKo: string;
  nameEn: string;
  nameZh: string;
  lat: number;
  lng: number;
};

type ReverseResp = { nearestCity: City | null; kakaoRegion: string | null; kakaoEnabled: boolean };

// 도시 목록은 자주 안 바뀌므로 모듈 캐시로 재요청 방지
let cachedCities: City[] | null = null;

export function cityLabel(c: City, lang: string) {
  return lang === "ko" ? c.nameKo : lang === "zh" ? c.nameZh : c.nameEn;
}

/**
 * 도시 선택 드롭다운 + "내 위치"(GPS) 버튼.
 * onChange(cityKey, lat, lng) — 좌표는 GPS/목록에서 채워지며 없으면 null.
 */
export default function CitySelect({
  value,
  onChange,
  className = "",
}: {
  value: string;
  onChange: (city: string, lat: number | null, lng: number | null) => void;
  className?: string;
}) {
  const { t, lang } = useLanguage();
  const l = t.location;
  const [cities, setCities] = useState<City[]>(cachedCities ?? []);
  const [detecting, setDetecting] = useState(false);
  const [geoError, setGeoError] = useState("");

  useEffect(() => {
    if (cachedCities) return;
    api<City[]>("/api/cities")
      .then((data) => { cachedCities = data; setCities(data); })
      .catch(() => {});
  }, []);

  function onSelect(key: string) {
    const c = cities.find((x) => x.key === key);
    if (c) onChange(c.key, c.lat, c.lng);
    else onChange(key, null, null);
  }

  function detect() {
    setGeoError("");
    if (typeof navigator === "undefined" || !("geolocation" in navigator)) {
      setGeoError(l.geoUnsupported);
      return;
    }
    setDetecting(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try {
          const { latitude, longitude } = pos.coords;
          const res = await api<ReverseResp>(`/api/geo/reverse?lat=${latitude}&lng=${longitude}`);
          if (res.nearestCity) onChange(res.nearestCity.key, res.nearestCity.lat, res.nearestCity.lng);
          else setGeoError(l.geoError);
        } catch {
          setGeoError(l.geoError);
        } finally {
          setDetecting(false);
        }
      },
      () => { setGeoError(l.geoDenied); setDetecting(false); },
      { timeout: 10000, enableHighAccuracy: false }
    );
  }

  return (
    <div className={className}>
      <div className="flex gap-2">
        <select value={value} onChange={(e) => onSelect(e.target.value)} className="input flex-1">
          <option value="">{l.selectCity}</option>
          {cities.map((c) => (
            <option key={c.key} value={c.key}>{cityLabel(c, lang)}</option>
          ))}
        </select>
        <button
          type="button"
          onClick={detect}
          disabled={detecting}
          className="btn-secondary whitespace-nowrap px-3 text-sm disabled:opacity-60"
        >
          {detecting ? l.detecting : `📍 ${l.useMyLocation}`}
        </button>
      </div>
      {geoError && <p className="mt-1 text-xs text-red-500">{geoError}</p>}
    </div>
  );
}
