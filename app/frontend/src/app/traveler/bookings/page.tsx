"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { localeOf } from "@/lib/i18n";
import { STATUS_CLS, STATUS_ICON } from "@/lib/bookingStatus";
import { CalendarIcon } from "@/components/icons";
import GuideCard, { type GuideCardData } from "@/components/GuideCard";

type Booking = {
  id: number; guideProfileId: number; guideName: string; guideHeadline: string;
  startAt: string; hours: number; totalPrice: number; currency: string; status: string;
};

/** 거절/취소된 예약 아래 "비슷한 가이드는 어때요?" 섹션 — 재요청까지 이어지도록 유도. */
function SimilarGuides({ guideProfileId }: { guideProfileId: number }) {
  const { t, lang } = useLanguage();
  const [guides, setGuides] = useState<GuideCardData[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api<GuideCardData[]>(`/api/guides/${guideProfileId}/similar?lang=${lang}`)
      .then((res) => { if (!cancelled) setGuides(res); })
      .catch(() => { if (!cancelled) setGuides([]); });
    return () => { cancelled = true; };
  }, [guideProfileId, lang]);

  if (guides === null || guides.length === 0) return null;

  return (
    <div className="mt-4 border-t border-stone-100 pt-4">
      <p className="mb-3 text-sm font-bold text-stone-700">{t.travelerBookings.similarGuidesTitle}</p>
      <div className="grid gap-3 sm:grid-cols-2">
        {guides.map((g) => <GuideCard key={g.id} guide={g} />)}
      </div>
    </div>
  );
}

export default function TravelerBookingsPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const l = t.travelerBookings;
  const locale = localeOf(lang);

  const [bookings, setBookings] = useState<Booking[]>([]);
  const [error, setError]       = useState("");
  const [loading, setLoading]   = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api<Booking[]>("/api/bookings/traveler", { auth: true });
      setBookings(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setLoading(false); }
  }, [t.common.error]);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load();
  }, [router, load]);

  async function onCancel(id: number) {
    if (!confirm(l.confirmCancel)) return;
    try { await api(`/api/bookings/${id}/cancel`, { method: "PATCH", auth: true }); await load(); }
    catch (err) { alert(err instanceof Error ? err.message : t.common.error); }
  }
  async function onComplete(id: number) {
    if (!confirm(l.confirmComplete)) return;
    try { await api(`/api/bookings/${id}/complete`, { method: "PATCH", auth: true }); await load(); }
    catch (err) { alert(err instanceof Error ? err.message : t.common.error); }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 flex items-center gap-3">
          <Link href="/traveler" className="btn-ghost text-sm">{l.backHome}</Link>
          <h1 className="section-title">{l.title}</h1>
        </div>

        {loading && (
          <div className="flex flex-col gap-3">
            {[1, 2, 3].map((i) => <div key={i} className="card h-32 animate-pulse p-5" />)}
          </div>
        )}
        {error && (
          <p className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
        )}
        {!loading && !error && bookings.length === 0 && (
          <div className="card p-8 py-16 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl shadow-md">
              📋
            </div>
            <p className="mb-1 font-bold text-stone-900">{l.emptyTitle}</p>
            <p className="mb-5 text-sm text-stone-500">{l.emptyDesc}</p>
            <Link href="/guides" className="btn-primary">{l.emptyBtn}</Link>
          </div>
        )}

        <div className="flex flex-col gap-4">
          {bookings.map((b) => {
            const cls   = STATUS_CLS[b.status]  ?? "badge-gray";
            const icon  = STATUS_ICON[b.status] ?? "•";
            const label = t.status[b.status as keyof typeof t.status] ?? b.status;
            return (
              <div key={b.id} className="card p-5">
                <div className="mb-3 flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2 className="font-bold text-stone-900">{b.guideName}</h2>
                    <p className="mt-0.5 truncate text-xs text-stone-500">{b.guideHeadline}</p>
                  </div>
                  <span className={`${cls} flex-shrink-0 py-1`}>{icon} {label}</span>
                </div>
                <div className="mb-4 flex items-center justify-between rounded-xl bg-stone-50 px-4 py-3 text-sm">
                  <span className="flex items-center gap-1.5 text-stone-600">
                    <CalendarIcon className="h-4 w-4 flex-shrink-0 text-sky-500" />
                    {new Date(b.startAt).toLocaleDateString(locale, { year: "numeric", month: "short", day: "numeric" })}
                    {" · "}{b.hours} {l.hours}
                  </span>
                  <span className="font-bold text-stone-900">{b.totalPrice.toLocaleString()} {b.currency}</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link href={`/bookings/${b.id}`} className="btn-primary px-4 py-1.5 text-sm">{t.bookingDetail.detailBtn}</Link>
                  {(b.status === "REQUESTED" || b.status === "ACCEPTED") && (
                    <Link href={`/chat/${b.id}`} className="btn-secondary px-4 py-1.5 text-sm">{l.chat}</Link>
                  )}
                  {b.status === "ACCEPTED" && (
                    <button
                      onClick={() => onComplete(b.id)}
                      className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-4 py-1.5 text-sm font-medium text-emerald-700 transition-colors hover:bg-emerald-100"
                    >
                      {l.complete}
                    </button>
                  )}
                  {(b.status === "REQUESTED" || b.status === "ACCEPTED") && (
                    <button onClick={() => onCancel(b.id)} className="btn-danger px-4 py-1.5 text-sm">{l.cancel}</button>
                  )}
                  {b.status === "COMPLETED" && (
                    <Link
                      href={`/review/${b.id}`}
                      className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-4 py-1.5 text-sm font-medium text-amber-700 transition-colors hover:bg-amber-100"
                    >
                      {l.review}
                    </Link>
                  )}
                </div>

                {/* 거절된 예약 — 비슷한 가이드 추천으로 재예약 유도 (지연 로딩).
                    CANCELLED는 BookingService.cancel()이 여행자 본인만 호출 가능(가이드發 취소 경로 없음)이라 제외 */}
                {b.status === "REJECTED" && (
                  <SimilarGuides guideProfileId={b.guideProfileId} />
                )}
              </div>
            );
          })}
        </div>
      </div>
    </main>
  );
}
