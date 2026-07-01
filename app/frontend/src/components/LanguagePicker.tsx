"use client";

import { useLanguage } from "@/context/LanguageContext";
import type { Lang } from "@/lib/i18n";

const LANGS: { code: Lang; flag: string; native: string; label: string }[] = [
  { code: "ko", flag: "🇰🇷", native: "한국어", label: "Korean" },
  { code: "en", flag: "🇺🇸", native: "English", label: "영어" },
  { code: "zh", flag: "🇨🇳", native: "中文",   label: "中国语" },
];

export default function LanguagePicker() {
  const { showPicker, setLang } = useLanguage();

  if (!showPicker) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm px-4 animate-fade-up">
      <div className="w-full max-w-sm bg-white rounded-3xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="bg-gradient-to-br from-indigo-600 to-violet-700 px-8 pt-8 pb-10 text-white text-center">
          <div className="text-4xl mb-3">🌏</div>
          <h2 className="text-xl font-bold">Choose your language</h2>
          <p className="text-sm text-white/70 mt-1">언어를 선택하세요 · 请选择语言</p>
        </div>

        {/* Options */}
        <div className="px-6 py-5 flex flex-col gap-3">
          {LANGS.map((l) => (
            <button
              key={l.code}
              onClick={() => setLang(l.code)}
              className="flex items-center gap-4 rounded-2xl border-2 border-gray-100 p-4 text-left transition-all hover:border-indigo-300 hover:bg-indigo-50/60 active:scale-[0.98]"
            >
              <span className="text-3xl flex-shrink-0">{l.flag}</span>
              <div>
                <p className="font-bold text-gray-900">{l.native}</p>
                <p className="text-xs text-gray-400">{l.label}</p>
              </div>
            </button>
          ))}
        </div>

        <p className="text-center text-xs text-gray-400 pb-6 px-6">
          You can change this anytime from the menu.
        </p>
      </div>
    </div>
  );
}
