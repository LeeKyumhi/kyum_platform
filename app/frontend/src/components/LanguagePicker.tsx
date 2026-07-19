"use client";

import { useState } from "react";
import { useLanguage } from "@/context/LanguageContext";
import type { Lang } from "@/lib/i18n";
import { getToken } from "@/lib/api";
import { getMode, setMode, type Mode } from "@/lib/mode";

const LANGS: { code: Lang; flag: string; native: string; label: string }[] = [
  { code: "ko", flag: "🇰🇷", native: "한국어", label: "Korean" },
  { code: "en", flag: "🇺🇸", native: "English", label: "영어" },
  { code: "zh", flag: "🇨🇳", native: "中文",   label: "中国语" },
];

/**
 * 첫 방문 온보딩 모달 2단계:
 *  1) 언어 선택 → 2) 같은 모양 그대로 여행자/가이드 선택.
 * 역할 단계는 아직 모드가 없고 로그인 전일 때만 나온다 (로그인 사용자는 /select-mode에서 이미 선택).
 */
export default function LanguagePicker() {
  const { showPicker, setLang, t } = useLanguage();
  const [step, setStep] = useState<"lang" | "role">("lang");

  // 언어 단계는 컨텍스트(showPicker)가, 역할 단계는 로컬 step이 켜 둔다
  if (!showPicker && step !== "role") return null;

  function pickLang(code: Lang) {
    setLang(code); // 저장 + showPicker=false
    if (getMode() == null && !getToken()) setStep("role");
  }

  function pickRole(m: Mode) {
    setMode(m);
    // 랜딩("/")이 이미 마운트돼 있으면 localStorage만으론 못 알아채므로 이벤트로 알린다
    window.dispatchEvent(new Event("peerup-mode-changed"));
    setStep("lang"); // 모달 닫기 (showPicker는 이미 false)
  }

  const lm = t.selectMode;
  const ROLES: { id: Mode; icon: string; title: string; desc: string }[] = [
    { id: "traveler", icon: "🧳", title: lm.travelerTitle, desc: lm.travelerDesc },
    { id: "guide",    icon: "🗺️", title: lm.guideTitle,    desc: lm.guideDesc },
  ];

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm px-4 animate-fade-up">
      <div className="w-full max-w-sm bg-white rounded-3xl shadow-2xl overflow-hidden">
        {step === "lang" ? (
          <>
            {/* Header */}
            <div className="bg-gradient-to-br from-sky-500 to-cyan-500 px-8 pt-8 pb-10 text-white text-center">
              <div className="text-4xl mb-3">🌏</div>
              <h2 className="text-xl font-bold">Choose your language</h2>
              <p className="text-sm text-white/70 mt-1">언어를 선택하세요 · 请选择语言</p>
            </div>

            {/* Options */}
            <div className="px-6 py-5 flex flex-col gap-3">
              {LANGS.map((l) => (
                <button
                  key={l.code}
                  onClick={() => pickLang(l.code)}
                  className="flex items-center gap-4 rounded-2xl border-2 border-stone-100 p-4 text-left transition-all hover:border-sky-300 hover:bg-sky-50/60 active:scale-[0.98]"
                >
                  <span className="text-3xl flex-shrink-0">{l.flag}</span>
                  <div>
                    <p className="font-bold text-stone-900">{l.native}</p>
                    <p className="text-xs text-stone-400">{l.label}</p>
                  </div>
                </button>
              ))}
            </div>

            <p className="text-center text-xs text-stone-400 pb-6 px-6">
              You can change this anytime from the menu.
            </p>
          </>
        ) : (
          <>
            {/* Header — 언어 단계와 같은 형태, 역할 단계는 브랜드 그라디언트 */}
            <div className="bg-gradient-to-br from-sky-500 to-cyan-500 px-8 pt-8 pb-10 text-white text-center">
              <div className="text-4xl mb-3">👋</div>
              <h2 className="text-xl font-bold">{t.landing.modeQ}</h2>
              <p className="text-sm text-white/70 mt-1">{t.landing.modeQSub}</p>
            </div>

            {/* Options */}
            <div className="px-6 py-5 flex flex-col gap-3">
              {ROLES.map((r) => (
                <button
                  key={r.id}
                  onClick={() => pickRole(r.id)}
                  className={`flex items-center gap-4 rounded-2xl border-2 border-stone-100 p-4 text-left transition-all active:scale-[0.98] ${
                    r.id === "guide"
                      ? "hover:border-emerald-300 hover:bg-emerald-50/60"
                      : "hover:border-sky-300 hover:bg-sky-50/60"
                  }`}
                >
                  <span className="text-3xl flex-shrink-0">{r.icon}</span>
                  <div>
                    <p className="font-bold text-stone-900">{r.title}</p>
                    <p className="text-xs text-stone-400 leading-snug">{r.desc}</p>
                  </div>
                </button>
              ))}
            </div>

            <p className="h-6" />
          </>
        )}
      </div>
    </div>
  );
}
