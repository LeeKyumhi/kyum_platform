"use client";

import { useEffect, useState } from "react";
import { loadCities, type District } from "@/components/CitySelect";
import { useLanguage } from "@/context/LanguageContext";

/**
 * 세부 지역(구) 선택 드롭다운. 선택된 도시에 구 목록이 있을 때만 렌더된다.
 * 라벨은 언어별(ko/en/zh)로 표시하되, 값은 항상 한국어(ko) — 백엔드 매칭·Kakao 지오코딩 기준.
 */
export default function DistrictSelect({
  city,
  value,
  onChange,
  className = "",
}: {
  city: string;
  value: string;
  onChange: (district: string) => void;
  className?: string;
}) {
  const { t, lang } = useLanguage();
  const l = t.location;
  const [districts, setDistricts] = useState<District[]>([]);

  useEffect(() => {
    if (!city) { setDistricts([]); return; }
    let alive = true;
    loadCities().then((cs) => {
      if (!alive) return;
      setDistricts(cs.find((c) => c.key === city)?.districts ?? []);
    });
    return () => { alive = false; };
  }, [city]);

  if (!city || districts.length === 0) return null;

  const label = (d: District) => (lang === "ko" ? d.ko : lang === "zh" ? d.zh : d.en);

  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={`input ${className}`}
      aria-label={l.selectDistrict}
    >
      <option value="">{l.allDistricts}</option>
      {districts.map((d) => (
        <option key={d.ko} value={d.ko}>{label(d)}</option>
      ))}
    </select>
  );
}
