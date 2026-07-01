"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

const STATUS_ICON: Record<string, string> = {
  REQUESTED: "⏳", ACCEPTED: "✅", REJECTED: "❌", CANCELLED: "🚫", COMPLETED: "🏁",
};
const STATUS_CLS: Record<string, string> = {
  REQUESTED: "badge-amber", ACCEPTED: "badge-emerald", REJECTED: "badge-red",
  CANCELLED: "badge-gray",  COMPLETED: "badge-indigo",
};

type Booking = {
  id: number; guideName: string; guideHeadline: string;
  startAt: string; hours: number; totalPrice: number; currency: string; status: string;
};

export default function TravelerBookingsPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const l = t.travelerBookings;

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
    if (!confirm(lang === "ko" ? "이 예약을 취소할까요?" : "Cancel this booking?")) return;
    try { await api(`/api/bookings/${id}/cancel`, { method: "PATCH", auth: true }); await load(); }
    catch (err) { alert(err instanceof Error ? err.message : t.common.error); }
  }
  async function onComplete(id: number) {
    if (!confirm(lang === "ko" ? "완료 처리할까요?" : "Mark as complete?")) return;
    try { await api(`/api/bookings/${id}/complete`, { method: "PATCH", auth: true }); await load(); }
    catch (err) { alert(err instanceof Error ? err.message : t.common.error); }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center gap-3 mb-6">
          <Link href="/traveler" className="btn-ghost text-sm">{l.backHome}</Link>
          <h1 className="section-title">{l.title}</h1>
        </div>

        {loading && <div className="flex flex-col gap-3">{[1,2,3].map((i) => <div key={i} className="card p-5 animate-pulse h-32" />)}</div>}
        {error && <p className="text-red-600 text-sm">{error}</p>}
        {!loading && bookings.length === 0 && (
          <div className="text-center py-16 card p-8">
            <div className="text-4xl mb-3">📋</div>
            <p className="font-semibold text-gray-700 mb-1">{l.emptyTitle}</p>
            <p className="text-sm text-gray-400 mb-5">{l.emptyDesc}</p>
            <Link href="/guides" className="btn-primary">{l.emptyBtn}</Link>
          </div>
        )}

        <div className="flex flex-col gap-4">
          {bookings.map((b) => {
            const cls  = STATUS_CLS[b.status]  ?? "badge-gray";
            const icon = STATUS_ICON[b.status] ?? "•";
            const label = t.status[b.status as keyof typeof t.status] ?? b.status;
            return (
              <div key={b.id} className="card p-5">
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <h2 className="font-bold text-gray-900">{b.guideName}</h2>
                    <p className="text-xs text-gray-500 mt-0.5">{b.guideHeadline}</p>
                  </div>
                  <span className={cls}>{icon} {label}</span>
                </div>
                <div className="rounded-xl bg-gray-50 px-4 py-3 text-sm flex justify-between items-center mb-4">
                  <span className="text-gray-600">
                    {new Date(b.startAt).toLocaleDateString()} · {b.hours} {l.hours}
                  </span>
                  <span className="font-semibold text-indigo-600">{b.totalPrice.toLocaleString()} {b.currency}</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {(b.status === "REQUESTED" || b.status === "ACCEPTED") && (
                    <Link href={`/chat/${b.id}`} className="btn-secondary text-sm py-1.5 px-3">{l.chat}</Link>
                  )}
                  {b.status === "ACCEPTED" && (
                    <button onClick={() => onComplete(b.id)}
                      className="inline-flex items-center gap-1 rounded-xl bg-emerald-50 border border-emerald-200 px-3 py-1.5 text-sm font-medium text-emerald-700 hover:bg-emerald-100 transition-colors">
                      {l.complete}
                    </button>
                  )}
                  {(b.status === "REQUESTED" || b.status === "ACCEPTED") && (
                    <button onClick={() => onCancel(b.id)} className="btn-danger text-sm py-1.5 px-3">{l.cancel}</button>
                  )}
                  {b.status === "COMPLETED" && (
                    <Link href={`/review/${b.id}`}
                      className="inline-flex items-center gap-1 rounded-xl bg-amber-50 border border-amber-200 px-3 py-1.5 text-sm font-medium text-amber-700 hover:bg-amber-100 transition-colors">
                      {l.review}
                    </Link>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </main>
  );
}
