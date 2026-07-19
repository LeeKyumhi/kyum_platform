"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import GuideCard, { type GuideCardData } from "@/components/GuideCard";
import TrackNotice from "@/components/TrackNotice";
import PageHeader from "@/components/PageHeader";
import EmptyState from "@/components/EmptyState";
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
        <PageHeader accent="emerald" title={<>🤝 {c.title}</>} subtitle={c.sub} />
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
        {loading && (
          <div className="grid gap-4 sm:grid-cols-2">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="card animate-pulse p-5">
                <div className="flex items-start gap-4">
                  <div className="h-16 w-16 flex-shrink-0 rounded-2xl bg-stone-100" />
                  <div className="flex-1 pt-1">
                    <div className="mb-2 h-4 w-32 rounded bg-stone-100" />
                    <div className="h-3 w-20 rounded bg-stone-100" />
                  </div>
                </div>
                <div className="mt-4 h-3 w-full rounded bg-stone-100" />
                <div className="mt-2 h-3 w-2/3 rounded bg-stone-100" />
              </div>
            ))}
          </div>
        )}
        {error && <p className="text-sm text-red-600">{error}</p>}
        {!loading && !error && partners.length === 0 && (
          <EmptyState accent="emerald" icon="🤝" message={c.empty} />
        )}
        {!loading && (
          <div className="animate-fade-up grid gap-4 sm:grid-cols-2">
            {partners.map((g) => <GuideCard key={g.id} guide={g} track="companion" />)}
          </div>
        )}
      </div>
    </main>
  );
}
