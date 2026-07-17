"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export type District = { ko: string; en: string; zh: string };

export type City = {
  key: string;
  nameKo: string;
  nameEn: string;
  nameZh: string;
  lat: number;
  lng: number;
  districts?: District[];
};

// 도시 목록은 자주 안 바뀌므로 모듈 캐시로 재요청 방지
let cachedCities: City[] | null = null;
let citiesPromise: Promise<City[]> | null = null;

/** 도시 목록 로드(캐시 공유). CitySelect / DistrictSelect 둘 다 사용. */
export function loadCities(): Promise<City[]> {
  if (cachedCities) return Promise.resolve(cachedCities);
  if (!citiesPromise) {
    citiesPromise = api<City[]>("/api/cities")
      .then((data) => { cachedCities = data; return data; })
      .catch(() => { citiesPromise = null; return []; });
  }
  return citiesPromise;
}

export function cityLabel(c: City, lang: string) {
  return lang === "ko" ? c.nameKo : lang === "zh" ? c.nameZh : c.nameEn;
}

/**
 * 도시 선택 드롭다운.
 * onChange(cityKey, lat, lng) — 좌표는 선택한 도시의 중심좌표(없으면 null).
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

  useEffect(() => {
    if (cachedCities) return;
    loadCities().then(setCities);
  }, []);

  function onSelect(key: string) {
    const c = cities.find((x) => x.key === key);
    if (c) onChange(c.key, c.lat, c.lng);
    else onChange(key, null, null);
  }

  return (
    <div className={className}>
      <select value={value} onChange={(e) => onSelect(e.target.value)} className="input w-full">
        <option value="">{l.selectCity}</option>
        {cities.map((c) => (
          <option key={c.key} value={c.key}>{cityLabel(c, lang)}</option>
        ))}
      </select>
    </div>
  );
}
