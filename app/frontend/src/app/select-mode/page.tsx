"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getToken } from "@/lib/api";
import { setMode, type Mode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";

export default function SelectModePage() {
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.selectMode;

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  function choose(mode: Mode) {
    setMode(mode);
    router.push(mode === "guide" ? "/guide" : "/traveler");
  }

  const MODES = [
    {
      id: "traveler" as Mode,
      icon: "🧳",
      title: l.travelerTitle,
      desc: l.travelerDesc,
      color: "from-emerald-500 to-teal-500",
      bg: "hover:border-emerald-300 hover:bg-emerald-50/40",
    },
    {
      id: "guide" as Mode,
      icon: "🗺️",
      title: l.guideTitle,
      desc: l.guideDesc,
      color: "from-indigo-500 to-violet-500",
      bg: "hover:border-indigo-300 hover:bg-indigo-50/40",
    },
  ];

  return (
    <main className="page flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-2xl animate-fade-up">
        <div className="text-center mb-10">
          <h1 className="text-3xl font-bold text-gray-900">{l.title}</h1>
          <p className="mt-2 text-gray-500">{l.sub}</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          {MODES.map((m) => (
            <button key={m.id} onClick={() => choose(m.id)}
              className={`card p-8 text-left transition-all duration-200 active:scale-[0.98] border-2 border-transparent ${m.bg}`}>
              <div className={`mb-5 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br ${m.color} text-2xl shadow-md`}>
                {m.icon}
              </div>
              <h2 className="text-lg font-bold text-gray-900 mb-2">{m.title}</h2>
              <p className="text-sm text-gray-500 leading-relaxed">{m.desc}</p>
              <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-indigo-600">
                {l.choose} →
              </span>
            </button>
          ))}
        </div>
      </div>
    </main>
  );
}
