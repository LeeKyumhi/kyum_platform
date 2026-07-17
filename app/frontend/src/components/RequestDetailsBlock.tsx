"use client";

import { useLanguage } from "@/context/LanguageContext";
import { parseRequestDetails, CHECKBOX_FIELDS } from "@/lib/companionRequest";

/** 동행 예약 요청 내용 표시 — 예약 상세·요청 카드 공용. 값 없으면 null 렌더. */
export default function RequestDetailsBlock({ raw }: { raw?: string | null }) {
  const { t } = useLanguage();
  if (!raw) return null;
  const parsed = parseRequestDetails(raw);
  return (
    <div className="rounded-xl bg-stone-50 px-4 py-3">
      <p className="mb-1.5 text-xs font-semibold text-stone-500">{t.companionBooking.detailsTitle}</p>
      {parsed ? (
        <dl className="flex flex-col gap-1 text-sm">
          {Object.entries(parsed).map(([k, v]) => (
            <div key={k} className="flex gap-2">
              <dt className="shrink-0 font-semibold text-stone-600">
                {t.companionBooking[k as keyof typeof t.companionBooking] ?? k}
              </dt>
              <dd className="text-stone-800">{CHECKBOX_FIELDS.has(k) ? "✓" : v}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="text-sm text-stone-700">{raw}</p>
      )}
    </div>
  );
}
