"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";
import { getToken, api } from "@/lib/api";

type Me = { id: number; fullName: string; email: string };

const WM = "https://upload.wikimedia.org/wikipedia/commons/thumb";
const SPOTS = [
  { img: `${WM}/5/5d/Gyeonghoeru_%28Royal_Banquet_Hall%29_at_Gyeongbokgung_Palace%2C_Seoul.jpg/960px-Gyeonghoeru_%28Royal_Banquet_Hall%29_at_Gyeongbokgung_Palace%2C_Seoul.jpg`, ko: "경복궁", en: "Gyeongbokgung", zh: "景福宫", city: { ko: "서울", en: "Seoul", zh: "首尔" } },
  { img: `${WM}/b/b4/N_Seoul_Tower_complex_staircase.jpg/960px-N_Seoul_Tower_complex_staircase.jpg`, ko: "N서울타워", en: "N Seoul Tower", zh: "N首尔塔", city: { ko: "서울", en: "Seoul", zh: "首尔" } },
  { img: `${WM}/4/41/Bukchon-ro_11-gil_street_with_hanok_houses_at_blue_hour_in_Bukchon_Hanok_Village_Seoul.jpg/960px-Bukchon-ro_11-gil_street_with_hanok_houses_at_blue_hour_in_Bukchon_Hanok_Village_Seoul.jpg`, ko: "북촌 한옥마을", en: "Bukchon Hanok Village", zh: "北村韩屋村", city: { ko: "서울", en: "Seoul", zh: "首尔" } },
  { img: `${WM}/a/a2/Haeundae_Beach_in_Busan.jpg/960px-Haeundae_Beach_in_Busan.jpg`, ko: "해운대", en: "Haeundae Beach", zh: "海云台", city: { ko: "부산", en: "Busan", zh: "釜山" } },
  { img: `${WM}/1/19/Hydrangea_macrophylla_in_front_of_Seongsan_Ilchulbong_volcano_at_blue_hour_in_Jeju_Island_South_Korea.jpg/960px-Hydrangea_macrophylla_in_front_of_Seongsan_Ilchulbong_volcano_at_blue_hour_in_Jeju_Island_South_Korea.jpg`, ko: "성산일출봉", en: "Seongsan Ilchulbong", zh: "城山日出峰", city: { ko: "제주", en: "Jeju", zh: "济州" } },
  { img: `${WM}/2/22/Jeonju_Hanok_Maeul_02.jpg/960px-Jeonju_Hanok_Maeul_02.jpg`, ko: "전주 한옥마을", en: "Jeonju Hanok Village", zh: "全州韩屋村", city: { ko: "전주", en: "Jeonju", zh: "全州" } },
];

export default function Home() {
  const { t, lang } = useLanguage();
  const l = t.landing;

  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    if (!getToken()) return;
    api<Me>("/api/users/me", { auth: true }).then(setMe).catch(() => {});
  }, []);

  const features = [
    { icon: "🤝", title: l.f1title, desc: l.f1desc, href: "/guides" },
    { icon: "💬", title: l.f2title, desc: l.f2desc },
    { icon: "🧭", title: t.explore.title, desc: t.explore.subtitle, href: "/explore" },
    { icon: "🗺️", title: t.itinerary.title, desc: t.itinerary.subtitle, href: "/trips" },
    { icon: "⭐", title: l.f3title, desc: l.f3desc },
    { icon: "❤️", title: l.followTitle, desc: l.followDesc },
  ];
  const steps = [
    { num: "01", title: l.s1title, desc: l.s1desc },
    { num: "02", title: l.s2title, desc: l.s2desc },
    { num: "03", title: l.s3title, desc: l.s3desc },
  ];
  const stats = [
    { val: l.stat1val, label: l.stat1label },
    { val: l.stat2val, label: l.stat2label },
    { val: l.stat3val, label: l.stat3label },
  ];

  return (
    <main className="min-h-screen bg-white">
      {/* ───────────── Hero ───────────── */}
      <section className="relative overflow-hidden px-4 pt-24 pb-14 md:pt-16 md:pb-16 text-center">
        <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
          <div className="absolute -top-24 right-0 h-80 w-80 rounded-full bg-indigo-100/70 blur-3xl" />
          <div className="absolute top-40 -left-24 h-80 w-80 rounded-full bg-violet-100/60 blur-3xl" />
        </div>

        <div className="mx-auto max-w-3xl">
          {me && (
            <p className="mb-3 text-sm font-medium text-indigo-600">👋 {me.fullName}님, 환영합니다!</p>
          )}
          <span className="inline-flex items-center gap-2 rounded-full border border-indigo-100 bg-indigo-50 px-4 py-1.5 text-sm font-medium text-indigo-700">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse-dot" />
            {l.badge}
          </span>

          <h1 className="mt-5 text-4xl font-extrabold leading-[1.1] tracking-tight text-gray-900 sm:text-6xl">
            {l.h1a}{" "}
            <span className="bg-gradient-to-r from-indigo-600 to-violet-600 bg-clip-text text-transparent">
              {l.h1b}
            </span>
          </h1>

          <p className="mx-auto mt-6 max-w-xl text-lg leading-relaxed text-gray-500">{l.subtitle}</p>

          <div className="mt-9 flex flex-wrap justify-center gap-3">
            {me ? (
              <Link href="/explore" className="btn-primary-lg shadow-lg shadow-indigo-200">🧭 {t.explore.title}</Link>
            ) : (
              <>
                <Link href="/signup" className="btn-primary-lg shadow-lg shadow-indigo-200">{l.ctaBtn2}</Link>
                <Link href="/explore" className="btn-secondary-lg">🧭 {t.explore.title}</Link>
              </>
            )}
          </div>

          <div className="mt-12 flex flex-wrap justify-center gap-8">
            {stats.map((s) => (
              <div key={s.label} className="flex flex-col items-center gap-0.5">
                <span className="text-2xl font-extrabold text-gray-900">{s.val}</span>
                <span className="text-sm text-gray-400">{s.label}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ───────────── Feature highlights ───────────── */}
      <section className="px-4 py-16">
        <div className="mx-auto mb-10 max-w-4xl text-center">
          <h2 className="text-3xl font-bold text-gray-900">{l.whyTitle}</h2>
          <p className="mt-3 text-gray-500">{l.whySub}</p>
        </div>
        <div className="mx-auto grid max-w-5xl gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => {
            const inner = (
              <>
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-2xl">
                  {f.icon}
                </div>
                <h3 className="mb-1.5 text-lg font-semibold text-gray-900">{f.title}</h3>
                <p className="text-sm leading-relaxed text-gray-500">{f.desc}</p>
                {f.href && <span className="mt-3 inline-block text-sm font-semibold text-indigo-600">{l.goLink}</span>}
              </>
            );
            return f.href ? (
              <Link key={f.title} href={f.href} className="card-hover p-6 text-left">{inner}</Link>
            ) : (
              <div key={f.title} className="card p-6 text-left">{inner}</div>
            );
          })}
        </div>
      </section>

      {/* ───────────── Korean landmarks gallery ───────────── */}
      <section className="bg-gray-50/70 px-4 py-16">
        <div className="mx-auto mb-10 max-w-4xl text-center">
          <h2 className="text-3xl font-bold text-gray-900">{l.spotsTitle}</h2>
          <p className="mx-auto mt-3 max-w-xl text-gray-500">{l.spotsSub}</p>
        </div>
        <div className="mx-auto grid max-w-5xl grid-cols-2 gap-4 lg:grid-cols-3">
          {SPOTS.map((s) => {
            const name = lang === "ko" ? s.ko : lang === "zh" ? s.zh : s.en;
            const cityName = lang === "ko" ? s.city.ko : lang === "zh" ? s.city.zh : s.city.en;
            return (
              <Link
                key={s.en}
                href="/explore"
                className="group relative aspect-[4/3] overflow-hidden rounded-2xl bg-gray-200 shadow-sm ring-1 ring-black/5"
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={s.img}
                  alt={name}
                  className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
                <div className="absolute bottom-0 left-0 right-0 p-3 text-left text-white">
                  <p className="text-sm font-semibold drop-shadow">{name}</p>
                  <p className="text-xs text-white/80">📍 {cityName}</p>
                </div>
              </Link>
            );
          })}
        </div>
        <div className="mt-8 text-center">
          <Link href="/explore" className="btn-secondary">🧭 {t.explore.title}</Link>
        </div>
      </section>

      {/* ───────────── How it works ───────────── */}
      <section className="bg-gradient-to-b from-indigo-50/60 to-white px-4 py-16">
        <div className="mx-auto mb-10 max-w-4xl text-center">
          <h2 className="text-3xl font-bold text-gray-900">{l.howTitle}</h2>
          <p className="mt-3 text-gray-500">{l.howSub}</p>
        </div>
        <div className="mx-auto grid max-w-3xl gap-8 sm:grid-cols-3">
          {steps.map((s, i) => (
            <div key={s.num} className="relative flex flex-col items-center text-center">
              {i < steps.length - 1 && (
                <div className="absolute left-[calc(50%+2rem)] right-0 top-5 hidden h-px border-t-2 border-dashed border-indigo-200 sm:block" />
              )}
              <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-full bg-indigo-600 text-sm font-bold text-white shadow-md shadow-indigo-200">
                {s.num}
              </div>
              <h3 className="mb-1.5 font-semibold text-gray-900">{s.title}</h3>
              <p className="text-sm leading-relaxed text-gray-500">{s.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ───────────── Final CTA ───────────── */}
      <section className="px-4 py-16">
        <div className="mx-auto max-w-4xl overflow-hidden rounded-3xl bg-gradient-to-br from-indigo-600 to-violet-700 px-8 py-14 text-center shadow-xl shadow-indigo-200">
          <h2 className="text-3xl font-bold text-white">{l.ctaTitle}</h2>
          <p className="mx-auto mt-3 mb-8 max-w-md text-indigo-100">{l.ctaSub}</p>
          <div className="flex flex-wrap justify-center gap-3">
            {me ? (
              <Link href="/explore" className="btn-primary-lg bg-white text-indigo-700 hover:bg-white/90">🧭 {t.explore.title}</Link>
            ) : (
              <>
                <Link href="/signup" className="btn-primary-lg bg-white text-indigo-700 hover:bg-white/90">{l.ctaBtn2}</Link>
                <Link href="/explore" className="btn-secondary-lg border-white/40 bg-white/10 text-white hover:bg-white/20">🧭 {t.explore.title}</Link>
              </>
            )}
          </div>
        </div>
      </section>

      <footer className="border-t border-gray-100 bg-gray-50 py-8 pb-24 text-center text-xs text-gray-400 md:pb-8">
        {l.footerText}
      </footer>
    </main>
  );
}
