"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLanguage } from "@/context/LanguageContext";
import { getToken, api } from "@/lib/api";
import { getMode } from "@/lib/mode";

type Me = { id: number; fullName: string; email: string };

export default function Home() {
  const { t } = useLanguage();
  const l = t.landing;
  const router = useRouter();

  const [me, setMe] = useState<Me | null>(null);
  const [mode, setMode] = useState<"traveler" | "guide" | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const token = getToken();
    if (!token) { setReady(true); return; }
    const m = getMode();
    setMode(m);
    api<Me>("/api/users/me", { auth: true })
      .then(setMe)
      .catch(() => {})
      .finally(() => setReady(true));
  }, []);

  const features = [
    { icon: "🤝", title: l.f1title, desc: l.f1desc },
    { icon: "💬", title: l.f2title, desc: l.f2desc },
    { icon: "⭐", title: l.f3title, desc: l.f3desc },
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

  const isLoggedIn = ready && me !== null;

  return (
    <main className="min-h-screen">
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-br from-indigo-900 via-indigo-800 to-violet-900 pt-32 pb-28 px-4 text-center text-white">
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -top-32 -right-32 h-96 w-96 rounded-full bg-violet-600/20 blur-3xl" />
          <div className="absolute -bottom-32 -left-32 h-96 w-96 rounded-full bg-indigo-500/20 blur-3xl" />
        </div>

        <div className="relative mx-auto max-w-3xl">
          <span className="mb-6 inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-1.5 text-sm font-medium text-white/90 backdrop-blur-sm border border-white/20">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse-dot" />
            {l.badge}
          </span>

          <h1 className="mt-4 text-4xl font-extrabold leading-tight sm:text-6xl tracking-tight">
            {l.h1a}
            <br />
            <span className="text-amber-400">{l.h1b}</span>
          </h1>

          <p className="mt-6 text-lg text-white/70 max-w-xl mx-auto leading-relaxed">{l.subtitle}</p>

          {/* CTA — 로그인 여부에 따라 분기 */}
          <div className="mt-10">
            {!ready ? (
              /* 로딩 중: 레이아웃 안정성을 위해 빈 블록 유지 */
              <div className="h-12" />
            ) : isLoggedIn ? (
              /* 로그인된 회원 UI */
              <div className="mx-auto max-w-sm">
                <div className="rounded-2xl bg-white/10 backdrop-blur-sm border border-white/20 p-5">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-400 to-orange-500 flex items-center justify-center text-white font-bold text-lg flex-shrink-0">
                      {me!.fullName.slice(0, 1).toUpperCase()}
                    </div>
                    <div className="text-left">
                      <p className="font-semibold text-white">{me!.fullName}님, 환영합니다! 👋</p>
                      <p className="text-xs text-white/60">
                        {mode === "guide" ? "🗺️ 가이드 모드" : mode === "traveler" ? "🧳 여행자 모드" : "모드를 선택해 주세요"}
                      </p>
                    </div>
                  </div>

                  {mode === "guide" ? (
                    <div className="flex flex-col gap-2">
                      <Link href="/guide/manage"
                        className="w-full rounded-xl bg-white text-indigo-700 font-semibold py-2.5 text-sm hover:bg-white/90 transition-colors text-center">
                        🗺️ 가이드 홈으로
                      </Link>
                      <Link href="/select-mode"
                        className="w-full rounded-xl bg-white/10 text-white/80 font-medium py-2 text-sm hover:bg-white/20 transition-colors text-center border border-white/20">
                        모드 전환
                      </Link>
                    </div>
                  ) : mode === "traveler" ? (
                    <div className="flex flex-col gap-2">
                      <Link href="/traveler"
                        className="w-full rounded-xl bg-white text-indigo-700 font-semibold py-2.5 text-sm hover:bg-white/90 transition-colors text-center">
                        🧳 여행자 홈으로
                      </Link>
                      <Link href="/guides"
                        className="w-full rounded-xl bg-white/10 text-white/80 font-medium py-2 text-sm hover:bg-white/20 transition-colors text-center border border-white/20">
                        🔍 가이드 찾기
                      </Link>
                    </div>
                  ) : (
                    /* 모드 미설정 */
                    <div className="flex flex-col gap-2">
                      <Link href="/select-mode"
                        className="w-full rounded-xl bg-white text-indigo-700 font-semibold py-2.5 text-sm hover:bg-white/90 transition-colors text-center">
                        모드 선택하기
                      </Link>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              /* 비로그인 CTA */
              <div className="flex flex-wrap gap-3 justify-center">
                <Link href="/guides" className="btn-primary-lg bg-white text-indigo-700 hover:bg-white/90 shadow-lg shadow-indigo-900/30">
                  {l.cta1}
                </Link>
                <Link href="/signup" className="btn-secondary-lg border-white/30 bg-white/10 text-white hover:bg-white/20">
                  {l.cta2}
                </Link>
              </div>
            )}
          </div>

          <div className="mt-14 flex flex-wrap justify-center gap-8 text-white/60 text-sm">
            {stats.map((s) => (
              <div key={s.label} className="flex flex-col items-center gap-0.5">
                <span className="text-2xl font-bold text-white">{s.val}</span>
                <span>{s.label}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-20 px-4 bg-white">
        <div className="mx-auto max-w-4xl text-center mb-12">
          <h2 className="text-3xl font-bold text-gray-900">{l.whyTitle}</h2>
          <p className="mt-3 text-gray-500">{l.whySub}</p>
        </div>
        <div className="mx-auto max-w-4xl grid gap-6 sm:grid-cols-3">
          {features.map((f) => (
            <div key={f.title} className="card p-7 text-center hover:shadow-md transition-shadow">
              <div className="mb-4 text-4xl">{f.icon}</div>
              <h3 className="mb-2 font-semibold text-gray-900 text-lg">{f.title}</h3>
              <p className="text-sm text-gray-500 leading-relaxed">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section className="py-20 px-4 bg-gradient-to-b from-indigo-50/60 to-white">
        <div className="mx-auto max-w-4xl text-center mb-12">
          <h2 className="text-3xl font-bold text-gray-900">{l.howTitle}</h2>
          <p className="mt-3 text-gray-500">{l.howSub}</p>
        </div>
        <div className="mx-auto max-w-3xl grid gap-8 sm:grid-cols-3">
          {steps.map((s, i) => (
            <div key={s.num} className="relative flex flex-col items-center text-center">
              {i < steps.length - 1 && (
                <div className="hidden sm:block absolute top-5 left-[calc(50%+2rem)] right-0 h-px border-t-2 border-dashed border-indigo-200" />
              )}
              <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-full bg-indigo-600 text-white font-bold text-sm shadow-md shadow-indigo-200">
                {s.num}
              </div>
              <h3 className="mb-1.5 font-semibold text-gray-900">{s.title}</h3>
              <p className="text-sm text-gray-500 leading-relaxed">{s.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 px-4 text-center bg-white">
        <div className="mx-auto max-w-xl">
          <h2 className="text-3xl font-bold text-gray-900">{l.ctaTitle}</h2>
          <p className="mt-3 text-gray-500 mb-8">{l.ctaSub}</p>
          {isLoggedIn ? (
            <div className="flex flex-wrap gap-3 justify-center">
              <Link href={mode === "guide" ? "/guide/manage" : mode === "traveler" ? "/traveler" : "/select-mode"}
                className="btn-primary-lg">
                {mode === "guide" ? "가이드 홈으로" : mode === "traveler" ? "여행자 홈으로" : "시작하기"}
              </Link>
            </div>
          ) : (
            <div className="flex flex-wrap gap-3 justify-center">
              <Link href="/guides" className="btn-primary-lg">{l.ctaBtn1}</Link>
              <Link href="/signup" className="btn-secondary-lg">{l.ctaBtn2}</Link>
            </div>
          )}
        </div>
      </section>

      <footer className="border-t border-gray-100 py-8 text-center text-xs text-gray-400 bg-gray-50">
        {l.footerText}
      </footer>
    </main>
  );
}
