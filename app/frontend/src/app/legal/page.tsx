"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";

/**
 * 상시 법적 안내 페이지 (공개) — 관광진흥법 §38 기준으로
 * 무엇이 불법이고 어떤 동행 서비스가 허용되는지, 플랫폼 정책까지.
 * 투어 세계 사이드바 ⚖️ 링크·법적 게이트의 "자세히 보기"가 이곳으로 온다.
 */
export default function LegalPage() {
  const { t } = useLanguage();
  const lp = t.legalPage;

  const sections = [
    { icon: "📜", title: lp.sec1Title, body: lp.sec1Body },
    { icon: "🚫", title: lp.sec2Title, body: lp.sec2Body },
    { icon: "🤝", title: lp.sec3Title, body: lp.sec3Body },
    { icon: "🛡️", title: lp.sec4Title, body: lp.sec4Body },
  ];

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-600 text-2xl shadow-md">
            ⚖️
          </div>
          <h1 className="section-title">{lp.title}</h1>
          <p className="section-subtitle">{lp.sub}</p>
        </div>

        <div className="flex flex-col gap-4">
          {sections.map((s) => (
            <section key={s.title} className="card p-6">
              <h2 className="mb-2 flex items-center gap-2 font-bold text-stone-900">
                <span className="text-xl">{s.icon}</span> {s.title}
              </h2>
              <p className="text-sm leading-relaxed text-stone-600">{s.body}</p>
            </section>
          ))}
        </div>

        <div className="mt-8 text-center">
          <Link href="/" className="btn-ghost text-sm">← {t.nav.home}</Link>
        </div>
      </div>
    </main>
  );
}
