"use client";

// 채팅에서 내 여행 일정 / 투어 코스를 골라 공유하는 모달.
// 스냅샷을 만들어 onShare로 넘기면 ChatRoom이 계획 카드 메시지로 전송한다(권한 문제 없음, 공유 시점 고정).
// 코스 탭은 가이드가 아니면 조회가 400을 던지므로 catch → 빈 목록으로 처리한다.

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { useModalDismiss } from "@/lib/useModalDismiss";
import { encodePlanCard, type PlanPayload, type PlanItem } from "@/lib/placeCard";

type ItinSummary = { id: number; title: string; city: string | null; startDate: string | null; endDate: string | null; itemCount: number };
type ItinFull = {
  id: number; title: string; startDate: string | null;
  items: { dayIndex: number; placeName: string; category: string | null; startHour: number | null; durationHours: number | null; sortOrder: number }[];
};
type CourseX = { id: number; title: string; durationHours: number; price: number; currency: string; waypoints: { placeName: string; category: string | null }[] };

/** YYYY-MM-DD + n일 */
function addDays(dateStr: string, n: number): string {
  const d = new Date(dateStr + "T00:00");
  d.setDate(d.getDate() + n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export default function SharePickerModal({ onShare, onClose }: {
  onShare: (content: string) => void;
  onClose: () => void;
}) {
  const { t } = useLanguage();
  const sh = t.share;
  const [tab, setTab] = useState<"itinerary" | "course">("itinerary");
  const [itineraries, setItineraries] = useState<ItinSummary[] | null>(null);
  const [courses, setCourses] = useState<CourseX[] | null>(null);
  const [courseDate, setCourseDate] = useState("");
  const [busy, setBusy] = useState(false);

  useModalDismiss(onClose);
  useEffect(() => {
    api<ItinSummary[]>("/api/itineraries/me", { auth: true }).then(setItineraries).catch(() => setItineraries([]));
    // 가이드 아니면 400 → 빈 목록
    api<CourseX[]>("/api/guide-profiles/me/courses", { auth: true }).then(setCourses).catch(() => setCourses([]));
  }, []);

  async function shareItinerary(summary: ItinSummary) {
    setBusy(true);
    try {
      const full = await api<ItinFull>(`/api/itineraries/me/${summary.id}`, { auth: true });
      const byDay = new Map<number, PlanItem[]>();
      for (const it of full.items) {
        const arr = byDay.get(it.dayIndex) ?? [];
        arr.push({ name: it.placeName, startHour: it.startHour, durationHours: it.durationHours, category: it.category });
        byDay.set(it.dayIndex, arr);
      }
      const days = [...byDay.entries()].sort((a, b) => a[0] - b[0]).map(([day, items]) => ({
        day,
        date: full.startDate ? addDays(full.startDate, day - 1) : null,
        items: items.sort((a, b) => (a.startHour ?? 99) - (b.startHour ?? 99)),
      }));
      const snapshot: PlanPayload = { kind: "itinerary", title: full.title || "여행 일정", startDate: full.startDate, days };
      onShare(encodePlanCard(snapshot));
      onClose();
    } catch {
      setBusy(false);
    }
  }

  function shareCourse(c: CourseX) {
    const snapshot: PlanPayload = {
      kind: "course", title: c.title, date: courseDate || null,
      durationHours: c.durationHours, price: c.price, currency: c.currency,
      stops: c.waypoints.map((w) => ({ name: w.placeName, category: w.category })),
    };
    onShare(encodePlanCard(snapshot));
    onClose();
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center sm:items-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 flex max-h-[85vh] w-full flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl sm:max-w-md sm:rounded-2xl">
        <div className="flex items-center justify-between gap-3 border-b border-stone-100 px-5 py-4">
          <h2 className="text-base font-bold text-stone-900">{sh.title}</h2>
          <button onClick={onClose} aria-label={sh.close}
            className="flex h-8 w-8 items-center justify-center rounded-full text-lg text-stone-400 hover:bg-stone-100 hover:text-stone-700">✕</button>
        </div>

        {/* 탭 */}
        <div className="flex gap-2 px-5 pt-3">
          <button onClick={() => setTab("itinerary")}
            className={`${tab === "itinerary" ? "chip-active" : "chip"} px-3 py-1.5 text-xs`}>📅 {sh.tabItinerary}</button>
          <button onClick={() => setTab("course")}
            className={`${tab === "course" ? "chip-active" : "chip"} px-3 py-1.5 text-xs`}>🎫 {sh.tabCourse}</button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
          {tab === "itinerary" ? (
            itineraries === null ? (
              <p className="py-6 text-center text-xs text-stone-400">…</p>
            ) : itineraries.length === 0 ? (
              <p className="py-6 text-center text-xs text-stone-400">{sh.itinEmpty}</p>
            ) : (
              <ul className="flex flex-col gap-1.5">
                {itineraries.map((it) => (
                  <li key={it.id}>
                    <button onClick={() => shareItinerary(it)} disabled={busy}
                      className="flex w-full items-center gap-3 rounded-xl border border-stone-100 px-3 py-2.5 text-left transition-colors hover:border-sky-300 hover:bg-sky-50 disabled:opacity-50">
                      <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl bg-sky-100 text-lg">📅</span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-bold text-stone-900">{it.title}</span>
                        <span className="block truncate text-[11px] text-stone-400">
                          {it.city ? `${it.city} · ` : ""}{sh.placesCount.replace("{n}", String(it.itemCount))}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )
          ) : (
            <>
              {/* 코스에 붙일 날짜(선택) */}
              <div className="mb-2 flex items-center gap-2 px-1">
                <label className="text-xs text-stone-500">📆 {sh.dateOptional}</label>
                <input type="date" value={courseDate} onChange={(e) => setCourseDate(e.target.value)}
                  className="input flex-1 py-1.5 text-sm" />
              </div>
              {courses === null ? (
                <p className="py-6 text-center text-xs text-stone-400">…</p>
              ) : courses.length === 0 ? (
                <p className="py-6 text-center text-xs text-stone-400">{sh.courseEmpty}</p>
              ) : (
                <ul className="flex flex-col gap-1.5">
                  {courses.map((c) => (
                    <li key={c.id}>
                      <button onClick={() => shareCourse(c)}
                        className="flex w-full items-center gap-3 rounded-xl border border-stone-100 px-3 py-2.5 text-left transition-colors hover:border-amber-300 hover:bg-amber-50">
                        <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl bg-amber-100 text-lg">🎫</span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-bold text-stone-900">{c.title}</span>
                          <span className="block truncate text-[11px] text-stone-400">
                            {sh.stopsCount.replace("{n}", String(c.waypoints.length))} · {c.price.toLocaleString()} {c.currency}
                          </span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
