"use client";

// 가이드 시급 인라인 편집. /profile 과 /guide/manage 가 함께 쓴다.
//
// 검증 규칙과 확인 문구를 한 곳에만 두려고 컴포넌트로 뺐다 — 두 벌로 갈라지면 한쪽만 고쳐진다.
// 서버(GuideProfileService.updateHourlyRate)가 같은 범위를 독립적으로 다시 막는다. 여기 검증은 UX용이다.
//
// 시급을 바꿔도 이미 잡힌 예약 금액은 변하지 않는다(예약이 생성 시점 시급을 스냅샷해 갖고 있다).

import { useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export const MIN_HOURLY_RATE = 1_000;
export const MAX_HOURLY_RATE = 1_000_000;

/** 기존 대비 50%를 넘게 오르내리면 한 번 확인받는다 — 자릿수 실수를 잡는 마지막 그물. */
const BIG_CHANGE_RATIO = 0.5;

type Props = {
  hourlyRate: number;
  currency: string;
  onSaved: (newRate: number) => void;
};

export default function HourlyRateEditor({ hourlyRate, currency, onSaved }: Props) {
  const { t } = useLanguage();
  const r = t.hourlyRate;

  const [editing, setEditing] = useState(false);
  const [input, setInput] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  function open() {
    setInput(String(hourlyRate));
    setError("");
    setEditing(true);
  }

  async function save() {
    const next = Number(input.replace(/[,\s]/g, ""));
    if (!Number.isInteger(next) || next < MIN_HOURLY_RATE || next > MAX_HOURLY_RATE) {
      setError(r.rangeError
        .replace("{min}", MIN_HOURLY_RATE.toLocaleString())
        .replace("{max}", MAX_HOURLY_RATE.toLocaleString()));
      return;
    }
    if (next === hourlyRate) { setEditing(false); return; }

    if (Math.abs(next - hourlyRate) > hourlyRate * BIG_CHANGE_RATIO) {
      const ok = confirm(r.bigChangeConfirm
        .replace("{old}", hourlyRate.toLocaleString())
        .replace("{new}", next.toLocaleString()));
      if (!ok) return;
    }

    setSaving(true);
    setError("");
    try {
      await api("/api/guide-profiles/me/hourly-rate", {
        method: "PATCH", body: { hourlyRate: next }, auth: true,
      });
      onSaved(next);
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setSaving(false);
    }
  }

  if (!editing) {
    return (
      <button onClick={open} className="inline-flex items-baseline gap-1 text-left hover:underline">
        <span className="font-bold text-stone-900">{hourlyRate.toLocaleString()}</span>
        <span className="text-xs text-stone-400">{currency}/hr</span>
        <span className="text-xs text-stone-400">✏️</span>
      </button>
    );
  }

  return (
    <div className="w-full max-w-xs">
      <div className="flex items-center gap-1.5">
        <input
          type="text"
          inputMode="numeric"
          value={input}
          onChange={(e) => { setInput(e.target.value); setError(""); }}
          autoFocus
          className="min-w-0 flex-1 rounded-lg border border-stone-200 px-2.5 py-1.5 text-sm outline-none focus:border-sky-300 focus:ring-2 focus:ring-sky-100"
        />
        <span className="flex-shrink-0 text-xs text-stone-400">{currency}/hr</span>
        <button
          onClick={save}
          disabled={saving}
          className="flex-shrink-0 rounded-lg bg-sky-500 px-3 py-1.5 text-xs font-bold text-white hover:bg-sky-600 disabled:opacity-60"
        >
          {r.save}
        </button>
        <button
          onClick={() => { setEditing(false); setError(""); }}
          className="flex-shrink-0 rounded-lg px-2 py-1.5 text-xs font-medium text-stone-400 hover:text-stone-600"
        >
          {r.cancel}
        </button>
      </div>
      {error && <p className="mt-1 text-xs text-red-500">{error}</p>}
      <p className="mt-1 text-[11px] text-stone-400">{r.appliesToNewBookings}</p>
    </div>
  );
}
