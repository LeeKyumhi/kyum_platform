"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";
import { NON_TOUR_CATEGORY_KEYS, type ServiceCategoryKey } from "@/lib/serviceCategories";

const CAT_ICONS: Record<string, string> = {
  MEDICAL_INTERPRETER: "🏥", DINING_COMPANION: "🍽️", CAFE_COMPANION: "☕",
  SHOPPING_INTERPRETER: "🛍️", LANGUAGE_EXCHANGE: "🗣️",
};

/** 투트랙 진입 카드 — 랜딩·여행자 홈·/find 공용. */
export default function TrackEntryCards({ compact = false }: { compact?: boolean }) {
  const { t } = useLanguage();
  const tr = t.tracks;
  return (
    <div className={`grid gap-4 ${compact ? "" : "sm:grid-cols-2"}`}>
      <Link href="/guides" className="card-hover flex flex-col gap-2 border-2 border-emerald-100 p-6">
        <span className="text-3xl">🎫</span>
        <span className="text-lg font-extrabold text-stone-900">{tr.tourTitle}</span>
        <span className="text-sm leading-relaxed text-stone-500">{tr.tourDesc}</span>
        <span className="mt-2 text-sm font-bold text-emerald-600">{tr.tourCta} →</span>
      </Link>
      <div className="card flex flex-col gap-2 border-2 border-sky-100 p-6">
        <span className="text-3xl">🤝</span>
        <span className="text-lg font-extrabold text-stone-900">{tr.companionTitle}</span>
        <span className="text-sm leading-relaxed text-stone-500">{tr.companionDesc}</span>
        <div className="mt-1 flex flex-wrap gap-1.5">
          {NON_TOUR_CATEGORY_KEYS.map((k) => (
            <Link key={k} href={`/companions?category=${k}`} className="chip">
              {CAT_ICONS[k]} {t.serviceCategories[k as ServiceCategoryKey]}
            </Link>
          ))}
        </div>
        <Link href="/companions" className="mt-2 text-sm font-bold text-sky-600">{tr.companionCta} →</Link>
      </div>
    </div>
  );
}
