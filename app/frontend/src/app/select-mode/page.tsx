"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getToken } from "@/lib/api";
import { setMode, type Mode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import { ArrowRightIcon } from "@/components/icons";

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
      grad: "from-sky-400 to-cyan-400",
    },
    {
      id: "guide" as Mode,
      icon: "🗺️",
      title: l.guideTitle,
      desc: l.guideDesc,
      grad: "from-emerald-400 to-teal-500",
    },
  ];

  return (
    <main className="page flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-2xl animate-fade-up">
        <div className="mb-10 text-center">
          <h1 className="text-3xl font-extrabold tracking-tight text-stone-900">{l.title}</h1>
          <p className="mt-2 text-stone-500">{l.sub}</p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          {MODES.map((m) => (
            <button
              key={m.id}
              onClick={() => choose(m.id)}
              className="card-hover group flex flex-col p-8 text-left active:scale-[0.98]"
            >
              <div className={`mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br ${m.grad} text-2xl shadow-md`}>
                {m.icon}
              </div>
              <h2 className="mb-1.5 text-lg font-bold text-stone-900">{m.title}</h2>
              <p className="text-sm leading-relaxed text-stone-500">{m.desc}</p>
              <span className="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-sky-500">
                {l.choose}
                <ArrowRightIcon className="h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5" />
              </span>
            </button>
          ))}
        </div>
      </div>
    </main>
  );
}
