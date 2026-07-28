"use client";

// 결제용 연락처 입력 모달. PG(스마트로)가 결제창 호출 시 구매자 연락처를 필수로 요구해서 받는다.
//
// 한국 번호가 없는 외국인 여행자가 주 사용자이므로 국가번호를 직접 고르게 하고
// 국내 형식(010-)을 강제하지 않는다. 저장은 국가번호를 포함한 E.164 한 줄로 한다.
// 저장된 번호는 본인과 PG에만 가고 가이드/여행자 상대방에게는 노출되지 않는다.

import { useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { useModalDismiss } from "@/lib/useModalDismiss";
import { groupedCountries, flagOf, countryName, toE164, isValidE164 } from "@/lib/countryCodes";

/** 브라우저 로캘의 지역(en-US → US)을 기본 국가로. 못 알아내면 한국. */
function defaultIso(): string {
  try {
    const region = new Intl.Locale(navigator.language).region;
    return region ?? "KR";
  } catch {
    return "KR";
  }
}

export default function PhoneCollectModal({ onSaved, onClose }: {
  onSaved: (e164: string) => void;
  onClose: () => void;
}) {
  const { t, lang } = useLanguage();
  const p = t.payment;
  const { popular, rest } = groupedCountries(lang);

  const [iso, setIso] = useState(() => {
    const guess = defaultIso();
    return [...popular, ...rest].some((c) => c.iso === guess) ? guess : "KR";
  });
  const [local, setLocal] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useModalDismiss(onClose);

  const dial = [...popular, ...rest].find((c) => c.iso === iso)?.dial ?? "82";

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const e164 = toE164(dial, local);
    if (!isValidE164(e164)) { setError(p.phoneInvalid); return; }

    setSaving(true);
    setError("");
    try {
      await api("/api/users/me/phone", { method: "PATCH", body: { phone: e164 }, auth: true });
      onSaved(e164);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center sm:items-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <form onSubmit={submit}
        className="relative z-10 flex w-full flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl sm:max-w-md sm:rounded-2xl">
        <div className="flex items-center justify-between gap-3 border-b border-stone-100 px-5 py-4">
          <h2 className="text-base font-bold text-stone-900">{p.phoneTitle}</h2>
          <button type="button" onClick={onClose} aria-label={p.phoneCancel}
            className="flex h-8 w-8 items-center justify-center rounded-full text-lg text-stone-400 hover:bg-stone-100 hover:text-stone-700">✕</button>
        </div>

        <div className="space-y-4 px-5 py-4">
          <p className="text-sm leading-relaxed text-stone-500">{p.phoneDesc}</p>

          <div className="flex gap-2">
            <label className="flex-shrink-0">
              <span className="sr-only">{p.phoneCountry}</span>
              <select value={iso} onChange={(e) => setIso(e.target.value)}
                className="h-11 w-32 rounded-xl border border-stone-200 bg-white px-2 text-sm focus:border-stone-400 focus:outline-none">
                <optgroup label={p.phoneCountry}>
                  {popular.map((c) => (
                    <option key={c.iso} value={c.iso}>
                      {flagOf(c.iso)} +{c.dial} {countryName(c.iso, lang)}
                    </option>
                  ))}
                </optgroup>
                <optgroup label="—">
                  {rest.map((c) => (
                    <option key={c.iso} value={c.iso}>
                      {flagOf(c.iso)} +{c.dial} {countryName(c.iso, lang)}
                    </option>
                  ))}
                </optgroup>
              </select>
            </label>

            <label className="min-w-0 flex-1">
              <span className="sr-only">{p.phoneLabel}</span>
              <input type="tel" inputMode="tel" autoFocus value={local}
                onChange={(e) => { setLocal(e.target.value); setError(""); }}
                placeholder={p.phoneLabel}
                className="h-11 w-full rounded-xl border border-stone-200 px-3 text-sm focus:border-stone-400 focus:outline-none" />
            </label>
          </div>

          <p className="text-xs text-stone-400">
            {countryName(iso, lang)} · +{dial} {local ? toE164(dial, local) : ""}
          </p>

          {error && (
            <p className="rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>
          )}
        </div>

        <div className="flex gap-2 border-t border-stone-100 px-5 py-4">
          <button type="button" onClick={onClose} className="btn-secondary flex-1">{p.phoneCancel}</button>
          <button type="submit" disabled={saving || !local.trim()} className="btn-primary flex-1 disabled:opacity-50">
            {saving ? p.processing : p.phoneSubmit}
          </button>
        </div>
      </form>
    </div>
  );
}
