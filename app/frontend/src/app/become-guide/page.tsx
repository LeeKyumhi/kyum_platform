"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import InterestPicker from "@/components/InterestPicker";

const LEVELS_KEYS = ["NATIVE", "FLUENT", "INTERMEDIATE", "BASIC"] as const;
const CURRENCIES  = ["KRW", "USD", "EUR", "JPY"];

const MBTI_TYPES = [
  ["INTJ","INTP","ENTJ","ENTP"],
  ["INFJ","INFP","ENFJ","ENFP"],
  ["ISTJ","ISFJ","ESTJ","ESFJ"],
  ["ISTP","ISFP","ESTP","ESFP"],
] as const;


type Language = { language: string; level: string };

export default function BecomeGuidePage() {
  const router  = useRouter();
  const { t }   = useLanguage();
  const l       = t.becomeGuide;
  const lp      = t.personality;

  const [form, setForm] = useState({ headline: "", introduction: "", hourlyRate: "", currency: "KRW", region: "" });
  const [languages, setLanguages] = useState<Language[]>([{ language: "", level: "FLUENT" }]);
  const [mbti, setMbti]           = useState("");
  const [interests, setInterests] = useState<string[]>([]);
  const [error, setError]         = useState("");
  const [loading, setLoading]     = useState(false);

  function onChange(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }
  function updateLanguage(i: number, key: keyof Language, value: string) {
    const next = [...languages]; next[i] = { ...next[i], [key]: value }; setLanguages(next);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      await api("/api/guide-profiles", {
        method: "POST", auth: true,
        body: { ...form, hourlyRate: Number(form.hourlyRate), languages, mbti: mbti || null, interests },
      });
      router.push("/guide/manage");
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setLoading(false); }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="text-center mb-8">
          <span className="text-3xl">🗺️</span>
          <h1 className="mt-3 text-2xl font-bold text-gray-900">{l.title}</h1>
          <p className="mt-1 text-sm text-gray-500">{l.sub}</p>
        </div>

        <form onSubmit={onSubmit} className="flex flex-col gap-5">
          {/* Sec 1 — 기본 정보 */}
          <div className="card p-6">
            <h2 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-indigo-700 text-xs font-bold">1</span>
              {l.sec1}
            </h2>
            <div className="flex flex-col gap-4">
              <div>
                <label className="input-label">{l.headlineLabel}</label>
                <input name="headline" placeholder={l.headlinePlaceholder}
                  value={form.headline} onChange={onChange} required className="input" />
              </div>
              <div>
                <label className="input-label">{l.introLabel} <span className="text-gray-400 normal-case font-normal">{l.introOpt}</span></label>
                <textarea name="introduction" placeholder={l.introPlaceholder}
                  value={form.introduction} onChange={onChange} rows={4} className="input resize-none" />
              </div>
              <div>
                <label className="input-label">{l.regionLabel}</label>
                <input name="region" placeholder={l.regionPlaceholder}
                  value={form.region} onChange={onChange} required className="input" />
              </div>
            </div>
          </div>

          {/* Sec 2 — 요금 */}
          <div className="card p-6">
            <h2 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-indigo-700 text-xs font-bold">2</span>
              {l.sec2}
            </h2>
            <div className="flex gap-3">
              <div className="flex-1">
                <label className="input-label">{l.rateLabel}</label>
                <input name="hourlyRate" type="number" min={1} placeholder={l.ratePlaceholder}
                  value={form.hourlyRate} onChange={onChange} required className="input" />
              </div>
              <div>
                <label className="input-label">{l.currencyLabel}</label>
                <select name="currency" value={form.currency} onChange={onChange} className="input">
                  {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
            </div>
          </div>

          {/* Sec 3 — 언어 */}
          <div className="card p-6">
            <h2 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-indigo-700 text-xs font-bold">3</span>
              {l.sec3}
            </h2>
            <div className="flex flex-col gap-3">
              {languages.map((lang, i) => (
                <div key={i} className="flex gap-2 items-center">
                  <input placeholder={l.langPlaceholder} value={lang.language}
                    onChange={(e) => updateLanguage(i, "language", e.target.value)}
                    required className="input flex-1" />
                  <select value={lang.level} onChange={(e) => updateLanguage(i, "level", e.target.value)} className="input w-28">
                    {LEVELS_KEYS.map((lk) => (
                      <option key={lk} value={lk}>{t.level[lk]}</option>
                    ))}
                  </select>
                  {languages.length > 1 && (
                    <button type="button" onClick={() => setLanguages(languages.filter((_, j) => j !== i))}
                      className="h-10 w-10 flex items-center justify-center rounded-xl border border-gray-200 text-gray-400 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-colors flex-shrink-0">
                      ✕
                    </button>
                  )}
                </div>
              ))}
              <button type="button" onClick={() => setLanguages([...languages, { language: "", level: "FLUENT" }])}
                className="self-start text-sm text-indigo-600 hover:underline font-medium">
                {l.addLang}
              </button>
            </div>
          </div>

          {/* Sec 4 — MBTI */}
          <div className="card p-6">
            <h2 className="font-semibold text-gray-900 mb-1 flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-violet-100 text-violet-700 text-xs font-bold">4</span>
              {lp.mbtiLabel}
              <span className="text-xs font-normal text-gray-400 ml-1">(선택)</span>
            </h2>
            <p className="text-xs text-gray-400 mb-4">MBTI를 공유하면 여행자들이 나를 더 잘 이해할 수 있어요.</p>
            <div className="flex flex-col gap-2">
              {MBTI_TYPES.map((row, ri) => (
                <div key={ri} className="grid grid-cols-4 gap-2">
                  {row.map((type) => (
                    <button
                      key={type} type="button"
                      onClick={() => setMbti(mbti === type ? "" : type)}
                      className={`rounded-xl py-2 text-sm font-semibold border transition-colors ${
                        mbti === type
                          ? "bg-violet-600 text-white border-violet-600"
                          : "bg-gray-50 text-gray-600 border-gray-200 hover:border-violet-300 hover:text-violet-600"
                      }`}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              ))}
            </div>
            {mbti && (
              <p className="mt-3 text-sm text-center text-violet-600 font-semibold">선택됨: {mbti}</p>
            )}
          </div>

          {/* Sec 5 — 관심사 */}
          <div className="card p-6">
            <h2 className="font-semibold text-gray-900 mb-1 flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 text-xs font-bold">5</span>
              {lp.interestsLabel}
              <span className="text-xs font-normal text-gray-400 ml-1">(선택)</span>
            </h2>
            <p className="text-xs text-gray-400 mb-4">{lp.interestsHint}</p>
            <InterestPicker selected={interests} onChange={setInterests} />
          </div>

          {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 border border-red-100">{error}</p>}

          <button type="submit" disabled={loading} className="btn-primary w-full py-3.5 text-base">
            {loading ? l.submitting : l.submitBtn}
          </button>
        </form>
      </div>
    </main>
  );
}
