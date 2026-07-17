"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import GuideCard, { type GuideCardData } from "@/components/GuideCard";
import TrackNotice from "@/components/TrackNotice";
import { NON_TOUR_CATEGORY_KEYS, type ServiceCategoryKey } from "@/lib/serviceCategories";

const CAT_ICONS: Record<string, string> = {
  MEDICAL_INTERPRETER: "🏥", DINING_COMPANION: "🍽️", CAFE_COMPANION: "☕",
  SHOPPING_INTERPRETER: "🛍️", LANGUAGE_EXCHANGE: "🗣️",
};

export default function CompanionsPage() {
  const { t, lang } = useLanguage();
  const c = t.companions;
  const [partners, setPartners] = useState<GuideCardData[]>([]);
  const [city, setCity] = useState("");
  const [category, setCategory] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function load(cityFilter: string, cat: string) {
    setLoading(true); setError("");
    try {
      const q = new URLSearchParams();
      if (cityFilter) q.set("city", cityFilter);
      if (cat) q.set("category", cat);
      q.set("lang", lang);
      const list = await api<GuideCardData[]>(`/api/guides?${q.toString()}`, { auth: true });
      // 카테고리 미선택 시엔 전체가 내려오므로 동행 카테고리 보유자만 남긴다
      setPartners(list.filter((g) => (g.serviceCategories ?? []).some((k) => k !== "TOUR_GUIDE")));
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setLoading(false); }
  }

  useEffect(() => {
    const initial = new URLSearchParams(window.location.search).get("category") ?? "";
    if (initial && (NON_TOUR_CATEGORY_KEYS as readonly string[]).includes(initial)) setCategory(initial);
    load("", initial);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function onCategory(cat: string) {
    const next = cat === category ? "" : cat;
    setCategory(next); load(city, next);
  }

  return (
    <main className="page px-4">
      <div className="container-lg">
        <div className="mb-5">
          <h1 className="text-2xl font-extrabold tracking-tight text-stone-900 md:text-3xl">🤝 {c.title}</h1>
          <p className="mt-1 text-sm text-stone-500">{c.sub}</p>
        </div>
        <TrackNotice />
        <p className="mb-2 text-sm font-semibold text-stone-700">{c.categoryPrompt}</p>
        <div className="shelf -mx-4 mb-3 px-4 !pb-3">
          <button onClick={() => onCategory("")} className={category === "" ? "chip-active" : "chip"}>{c.all}</button>
          {NON_TOUR_CATEGORY_KEYS.map((k) => (
            <button key={k} onClick={() => onCategory(k)} className={category === k ? "chip-active" : "chip"}>
              {CAT_ICONS[k]} {t.serviceCategories[k as ServiceCategoryKey]}
            </button>
          ))}
        </div>
        <div className="card mb-5 p-4">
          <CitySelect value={city} onChange={(key) => { setCity(key); load(key, category); }} />
        </div>
        {loading && <p className="py-10 text-center text-sm text-stone-400">…</p>}
        {error && <p className="text-sm text-red-600">{error}</p>}
        {!loading && !error && partners.length === 0 && (
          <p className="py-16 text-center text-stone-500">{c.empty}</p>
        )}
        {!loading && (
          <div className="grid gap-4 sm:grid-cols-2">
            {partners.map((g) => <GuideCard key={g.id} guide={g} track="companion" />)}
          </div>
        )}
      </div>
    </main>
  );
}
