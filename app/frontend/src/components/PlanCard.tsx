"use client";

// 채팅 계획 카드 — 여행 일정 / 투어 코스 공유(스냅샷). 텍스트 버블 밖에서 자체 스타일로 렌더.
// 카드 탭 → PlanPreviewModal(일정=날짜별 아이템, 코스=정차지) 팝업.

import type { PlanPayload } from "@/lib/placeCard";
import { translations as i18nDict } from "@/lib/i18n";
import { useModalDismiss } from "@/lib/useModalDismiss";

type ShareLabels = typeof i18nDict["ko"]["share"];

const hourLabel = (h: number | null | undefined) =>
  h == null ? "" : `${String(h).padStart(2, "0")}:00`;

function fmtDate(dateStr: string | null | undefined, locale: string) {
  if (!dateStr) return "";
  const d = new Date(dateStr + "T00:00");
  if (isNaN(d.getTime())) return "";
  return d.toLocaleDateString(locale, { month: "short", day: "numeric", weekday: "short" });
}

/** 채팅 말풍선 안에 뜨는 계획 카드(요약 + 보기 버튼). */
export function PlanMessageCard({ plan, mine, onOpen, labels }: {
  plan: PlanPayload; mine: boolean; onOpen: () => void; labels: ShareLabels;
}) {
  const isItin = plan.kind === "itinerary";
  const summary = isItin
    ? `${labels.daysCount.replace("{n}", String(plan.days.length))} · ${labels.placesCount.replace("{n}", String(plan.days.reduce((s, d) => s + d.items.length, 0)))}`
    : labels.stopsCount.replace("{n}", String(plan.stops.length));
  return (
    <div className={`w-64 max-w-full overflow-hidden rounded-2xl border shadow-sm ${
      mine ? "border-sky-200 bg-sky-50" : "border-stone-200 bg-white"
    }`}>
      <button onClick={onOpen} className="flex w-full items-start gap-2.5 px-3 py-2.5 text-left transition-colors hover:bg-black/[0.02]">
        <span className={`mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl text-lg ${
          isItin ? "bg-sky-100" : "bg-amber-100"
        }`}>{isItin ? "📅" : "🎫"}</span>
        <span className="min-w-0 flex-1">
          <span className="block text-[10px] font-bold uppercase tracking-wide text-stone-400">
            {isItin ? labels.itineraryBadge : labels.courseBadge}
          </span>
          <span className="block truncate text-sm font-bold text-stone-900">{plan.title}</span>
          <span className="mt-0.5 block truncate text-[11px] text-stone-500">{summary}</span>
          <span className="mt-1 block text-[11px] font-semibold text-sky-600">
            {isItin ? labels.viewItinerary : labels.viewCourse} →
          </span>
        </span>
      </button>
    </div>
  );
}

/** 공유된 계획 상세 미리보기 (읽기 전용). 일정=날짜별, 코스=정차지 순서. */
export function PlanPreviewModal({ plan, onClose, labels, locale }: {
  plan: PlanPayload; onClose: () => void; labels: ShareLabels; locale: string;
}) {
  useModalDismiss(onClose);

  const isItin = plan.kind === "itinerary";
  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center sm:items-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 flex max-h-[85vh] w-full flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl sm:max-w-md sm:rounded-2xl">
        <div className="flex items-start justify-between gap-3 border-b border-stone-100 px-5 py-4">
          <div className="min-w-0">
            <p className="text-[11px] font-bold uppercase tracking-wide text-stone-400">
              {isItin ? labels.itineraryBadge : labels.courseBadge}
            </p>
            <h2 className="truncate text-base font-bold text-stone-900">{isItin ? "📅" : "🎫"} {plan.title}</h2>
            {!isItin && plan.date && <p className="mt-0.5 text-xs text-amber-600">📆 {fmtDate(plan.date, locale)}</p>}
          </div>
          <button onClick={onClose} aria-label={labels.close}
            className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-lg text-stone-400 hover:bg-stone-100 hover:text-stone-700">✕</button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          {isItin ? (
            plan.days.length === 0 ? (
              <p className="py-6 text-center text-sm text-stone-400">—</p>
            ) : (
              <div className="flex flex-col gap-4">
                {plan.days.map((d) => (
                  <div key={d.day}>
                    <p className="mb-2 flex items-baseline gap-2 text-sm font-bold text-stone-800">
                      <span className="rounded-md bg-sky-100 px-1.5 py-0.5 text-[11px] text-sky-700">
                        {labels.dayLabel.replace("{n}", String(d.day))}
                      </span>
                      {d.date && <span className="text-xs font-medium text-stone-400">{fmtDate(d.date, locale)}</span>}
                    </p>
                    {d.items.length === 0 ? (
                      <p className="pl-1 text-xs text-stone-300">—</p>
                    ) : (
                      <ol className="flex flex-col gap-1.5">
                        {d.items.map((it, i) => (
                          <li key={i} className="flex items-center gap-2.5 rounded-xl bg-stone-50 px-3 py-2">
                            <span className="w-10 flex-shrink-0 text-[11px] font-bold text-sky-500">{hourLabel(it.startHour) || "·"}</span>
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-sm text-stone-800">{it.name}</span>
                              {it.category && <span className="block truncate text-[10px] text-stone-400">{it.category}</span>}
                            </span>
                          </li>
                        ))}
                      </ol>
                    )}
                  </div>
                ))}
              </div>
            )
          ) : (
            plan.stops.length === 0 ? (
              <p className="py-6 text-center text-sm text-stone-400">—</p>
            ) : (
              <ol className="flex flex-col gap-1.5">
                {plan.stops.map((s, i) => (
                  <li key={i} className="flex items-center gap-2.5 rounded-xl bg-stone-50 px-3 py-2">
                    <span className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-amber-100 text-[11px] font-bold text-amber-700">{i + 1}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm text-stone-800">{s.name}</span>
                      {s.category && <span className="block truncate text-[10px] text-stone-400">{s.category}</span>}
                    </span>
                  </li>
                ))}
              </ol>
            )
          )}
        </div>
      </div>
    </div>
  );
}
