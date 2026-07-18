"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";
import { NON_TOUR_CATEGORY_KEYS, type ServiceCategoryKey } from "@/lib/serviceCategories";
import { ArrowRightIcon } from "@/components/icons";

const CAT_ICONS: Record<string, string> = {
  MEDICAL_INTERPRETER: "🏥", DINING_COMPANION: "🍽️", CAFE_COMPANION: "☕",
  SHOPPING_INTERPRETER: "🛍️", LANGUAGE_EXCHANGE: "🗣️",
};

/**
 * 동행 세계 전용 랜딩 — 투어·가이드·명소 흔적 없음.
 * 히어로(동행 소개 + 카테고리 타일) → 여행일정 카드 → 파트너 되기 배너.
 */
export default function CompanionLanding() {
  const { t } = useLanguage();
  const c = t.companions;
  const tr = t.tracks;
  const cl = t.companionLanding;

  return (
    <main className="min-h-screen">
      {/* ── 히어로 ── */}
      <section className="px-3 pt-[4.25rem] md:px-6 md:pt-6">
        <div className="relative mx-auto max-w-6xl overflow-hidden rounded-[2rem] bg-gradient-to-br from-sky-600 via-sky-500 to-cyan-500 shadow-xl">
          <div className="relative flex min-h-[440px] flex-col items-center justify-center px-5 py-16 text-center md:min-h-[500px] md:px-10">
            <span className="inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/15 px-4 py-1.5 text-xs font-semibold tracking-wide text-white backdrop-blur-md">
              🤝 {tr.companionTitle}
            </span>

            <h1 className="mt-6 max-w-3xl text-4xl font-extrabold leading-[1.1] tracking-tight text-white sm:text-5xl">
              {c.title}
            </h1>
            <p className="mx-auto mt-4 max-w-xl text-base leading-relaxed text-white/85 md:text-lg">
              {c.sub}
            </p>

            {/* 카테고리 타일 — 바로 해당 동행 목록으로 */}
            <div className="mt-8 flex max-w-2xl flex-wrap items-center justify-center gap-2.5">
              {NON_TOUR_CATEGORY_KEYS.map((k) => (
                <Link
                  key={k}
                  href={`/companions?category=${k}`}
                  className="rounded-full bg-white/95 px-4 py-2.5 text-sm font-semibold text-stone-800 shadow-md transition-transform hover:scale-[1.04] active:scale-100"
                >
                  {CAT_ICONS[k]} {t.serviceCategories[k as ServiceCategoryKey]}
                </Link>
              ))}
            </div>

            <Link
              href="/companions"
              className="mt-8 inline-flex items-center gap-2 rounded-full bg-white px-7 py-3.5 font-bold text-sky-600 shadow-lg transition-transform duration-200 hover:scale-[1.03] active:scale-100"
            >
              {tr.companionCta} <ArrowRightIcon className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </section>

      {/* ── 여행일정 카드 ── */}
      <section className="px-4 py-12 md:px-6 md:py-14">
        <div className="mx-auto grid max-w-5xl gap-5 sm:grid-cols-2">
          <Link href="/trips" className="card-hover p-6 text-left">
            <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-sky-50 text-2xl">🗺️</div>
            <h3 className="mb-1.5 text-lg font-bold text-stone-900">{t.itinerary.title}</h3>
            <p className="text-sm leading-relaxed text-stone-500">{t.itinerary.subtitle}</p>
            <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-sky-500">
              {t.landing.goLink}
            </span>
          </Link>
          <Link href="/explore" className="card-hover p-6 text-left">
            <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-sky-50 text-2xl">🧭</div>
            <h3 className="mb-1.5 text-lg font-bold text-stone-900">{t.explore.title}</h3>
            <p className="text-sm leading-relaxed text-stone-500">{t.explore.subtitle}</p>
            <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-sky-500">
              {t.landing.goLink}
            </span>
          </Link>
        </div>
      </section>

      {/* ── 파트너 되기 배너 ── */}
      <section className="px-4 pb-16 md:px-6">
        <div className="mx-auto max-w-5xl overflow-hidden rounded-[2rem] bg-stone-900 shadow-xl">
          <div className="px-8 py-12 text-left md:px-14 md:py-14">
            <h2 className="max-w-md text-2xl font-extrabold tracking-tight text-white md:text-3xl">
              {cl.becomeTitle}
            </h2>
            <p className="mt-3 mb-7 max-w-md text-white/70">{cl.becomeDesc}</p>
            <Link
              href="/become-guide?license=no"
              className="inline-flex items-center gap-2 rounded-full bg-sky-500 px-7 py-3.5 font-bold text-white shadow-lg transition-colors hover:bg-sky-400"
            >
              🤝 {cl.becomeCta} <ArrowRightIcon className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </section>

      <footer className="border-t border-stone-100 py-8 pb-24 text-center text-xs text-stone-400 md:pb-8">
        {t.landing.footerText}
      </footer>
    </main>
  );
}
