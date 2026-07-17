"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import InterestPicker from "@/components/InterestPicker";
import ServiceCategoryPicker from "@/components/ServiceCategoryPicker";
import CitySelect from "@/components/CitySelect";

const LEVELS_KEYS = ["NATIVE", "FLUENT", "INTERMEDIATE", "BASIC"] as const;
const CURRENCIES  = ["KRW", "USD", "EUR", "JPY"];

const MBTI_TYPES = [
  ["INTJ","INTP","ENTJ","ENTP"],
  ["INFJ","INFP","ENFJ","ENFP"],
  ["ISTJ","ISFJ","ESTJ","ESFJ"],
  ["ISTP","ISFP","ESTP","ESFP"],
] as const;


type Language = { language: string; level: string };

function SectionHeading({ num, label, optional }: { num: string; label: string; optional?: string }) {
  return (
    <h2 className="flex items-center gap-2.5 font-bold text-stone-900">
      <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-sky-500 text-xs font-bold text-white shadow-sm shadow-sky-200">
        {num}
      </span>
      {label}
      {optional && <span className="text-xs font-normal text-stone-400">{optional}</span>}
    </h2>
  );
}

export default function BecomeGuidePage() {
  const router  = useRouter();
  const { t }   = useLanguage();
  const l       = t.becomeGuide;
  const lp      = t.personality;

  const [form, setForm] = useState({ headline: "", introduction: "", hourlyRate: "", currency: "KRW", region: "" });
  const [coords, setCoords] = useState<{ lat: number | null; lng: number | null }>({ lat: null, lng: null });
  const [languages, setLanguages] = useState<Language[]>([{ language: "", level: "FLUENT" }]);
  const [mbti, setMbti]           = useState("");
  const [interests, setInterests] = useState<string[]>([]);
  const [serviceCategories, setServiceCategories] = useState<string[]>([]);
  const [hostTermsAgreed, setHostTermsAgreed] = useState(false);
  const [error, setError]         = useState("");
  const [loading, setLoading]     = useState(false);

  function onChange(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }
  function updateLanguage(i: number, key: keyof Language, value: string) {
    const next = [...languages]; next[i] = { ...next[i], [key]: value }; setLanguages(next);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault(); setError("");
    if (!hostTermsAgreed) { setError(t.hostTerms.required); return; }
    setLoading(true);
    try {
      await api("/api/guide-profiles", {
        method: "POST", auth: true,
        body: {
          ...form,
          hourlyRate: Number(form.hourlyRate),
          city: form.region, latitude: coords.lat, longitude: coords.lng,
          languages, mbti: mbti || null, interests, serviceCategories,
          hostTermsAgreed,
        },
      });
      router.push("/guide/manage");
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setLoading(false); }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        {/* Page header */}
        <div className="mb-8 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 text-2xl shadow-md">
            🗺️
          </div>
          <h1 className="section-title mt-4">{l.title}</h1>
          <p className="section-subtitle">{l.sub}</p>
        </div>

        <form onSubmit={onSubmit} className="flex flex-col gap-5">
          {/* Sec 1 — 기본 정보 */}
          <div className="card p-6">
            <div className="mb-4">
              <SectionHeading num="1" label={l.sec1} />
            </div>
            <div className="flex flex-col gap-4">
              <div>
                <label className="input-label">{l.headlineLabel}</label>
                <input name="headline" placeholder={l.headlinePlaceholder}
                  value={form.headline} onChange={onChange} required className="input" />
              </div>
              <div>
                <label className="input-label">
                  {l.introLabel} <span className="font-normal normal-case text-stone-400">{l.introOpt}</span>
                </label>
                <textarea name="introduction" placeholder={l.introPlaceholder}
                  value={form.introduction} onChange={onChange} rows={4} className="input resize-none" />
              </div>
              <div>
                <label className="input-label">{t.location.cityLabel}</label>
                <CitySelect
                  value={form.region}
                  onChange={(city, lat, lng) => {
                    setForm((f) => ({ ...f, region: city }));
                    setCoords({ lat, lng });
                  }}
                />
              </div>
            </div>
          </div>

          {/* Sec 2 — 요금 */}
          <div className="card p-6">
            <div className="mb-4">
              <SectionHeading num="2" label={l.sec2} />
            </div>
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
            <div className="mb-4">
              <SectionHeading num="3" label={l.sec3} />
            </div>
            <div className="flex flex-col gap-3">
              {languages.map((lang, i) => (
                <div key={i} className="flex items-center gap-2">
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
                      className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl border border-stone-200 text-stone-400 transition-colors hover:border-red-200 hover:bg-red-50 hover:text-red-500">
                      ✕
                    </button>
                  )}
                </div>
              ))}
              <button type="button" onClick={() => setLanguages([...languages, { language: "", level: "FLUENT" }])}
                className="self-start text-sm font-semibold text-sky-500 transition-colors hover:text-sky-600 hover:underline">
                {l.addLang}
              </button>
            </div>
          </div>

          {/* Sec 4 — MBTI */}
          <div className="card p-6">
            <div className="mb-1">
              <SectionHeading num="4" label={lp.mbtiLabel} optional={l.introOpt} />
            </div>
            <p className="mb-4 text-xs text-stone-400">{l.mbtiHint}</p>
            <div className="flex flex-col gap-2">
              {MBTI_TYPES.map((row, ri) => (
                <div key={ri} className="grid grid-cols-4 gap-2">
                  {row.map((type) => (
                    <button
                      key={type} type="button"
                      onClick={() => setMbti(mbti === type ? "" : type)}
                      className={`rounded-xl border py-2 text-sm font-semibold transition-colors ${
                        mbti === type
                          ? "border-violet-600 bg-violet-600 text-white"
                          : "border-stone-200 bg-white text-stone-600 hover:border-violet-300 hover:text-violet-600"
                      }`}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              ))}
            </div>
            {mbti && (
              <p className="mt-3 text-center text-sm font-semibold text-violet-600">{l.mbtiSelected} {mbti}</p>
            )}
          </div>

          {/* Sec 5 — 관심사 */}
          <div className="card p-6">
            <div className="mb-1">
              <SectionHeading num="5" label={lp.interestsLabel} optional={l.introOpt} />
            </div>
            <p className="mb-4 text-xs text-stone-400">{lp.interestsHint}</p>
            <InterestPicker selected={interests} onChange={setInterests} />
          </div>

          {/* Sec 6 — 제공 서비스 (신규 가이드는 미인증 → 관광 잠금) */}
          <div className="card p-6">
            <div className="mb-1">
              <SectionHeading num="6" label={t.serviceCategories.sectionTitle} />
            </div>
            <p className="mb-4 text-xs text-stone-400">{t.serviceCategories.sectionHint}</p>
            <ServiceCategoryPicker selected={serviceCategories} onChange={setServiceCategories} verified={false} />
          </div>

          {/* Sec 7 — 호스트 약관 동의 (계정·자격 대여 금지) */}
          <div className="card p-6">
            <div className="mb-1">
              <SectionHeading num="7" label={t.hostTerms.title} />
            </div>
            <p className="mb-3 text-xs text-stone-400">{t.hostTerms.intro}</p>
            <ul className="mb-4 flex flex-col gap-2 text-sm text-stone-600">
              <li className="flex gap-2"><span className="text-emerald-500">•</span>{t.hostTerms.rule1}</li>
              <li className="flex gap-2"><span className="text-emerald-500">•</span>{t.hostTerms.rule2}</li>
              <li className="flex gap-2"><span className="text-emerald-500">•</span>{t.hostTerms.rule3}</li>
            </ul>
            <label className="flex cursor-pointer items-center gap-2.5 rounded-xl bg-stone-50 px-4 py-3 text-sm font-semibold text-stone-800">
              <input type="checkbox" checked={hostTermsAgreed}
                onChange={(e) => setHostTermsAgreed(e.target.checked)}
                className="h-4 w-4 accent-emerald-600" />
              {t.hostTerms.agree}
            </label>
          </div>

          {error && (
            <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
          )}

          <button type="submit" disabled={loading} className="btn-primary-lg w-full">
            {loading ? l.submitting : l.submitBtn}
          </button>
        </form>
      </div>
    </main>
  );
}
