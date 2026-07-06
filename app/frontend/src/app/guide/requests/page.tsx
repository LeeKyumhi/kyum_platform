"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { CalendarIcon, ChatIcon } from "@/components/icons";

const STATUS_ICON: Record<string, string> = {
  REQUESTED: "⏳", ACCEPTED: "✅", REJECTED: "❌", CANCELLED: "🚫", COMPLETED: "🏁",
};
const STATUS_CLS: Record<string, string> = {
  REQUESTED: "badge-amber", ACCEPTED: "badge-emerald", REJECTED: "badge-red",
  CANCELLED: "badge-gray",  COMPLETED: "badge-indigo",
};

type Booking = {
  id: number; travelerName: string;
  startAt: string; hours: number; totalPrice: number; currency: string;
  status: string; message: string | null;
};

export default function GuideRequestsPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const l = t.guideRequests;
  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  const [bookings, setBookings] = useState<Booking[]>([]);
  const [error, setError]       = useState("");
  const [loading, setLoading]   = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api<Booking[]>("/api/bookings/guide", { auth: true });
      setBookings(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setLoading(false); }
  }, [t.common.error]);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load();
  }, [router, load]);

  async function act(id: number, action: "accept" | "reject") {
    try { await api(`/api/bookings/${id}/${action}`, { method: "PATCH", auth: true }); await load(); }
    catch (err) { alert(err instanceof Error ? err.message : t.common.error); }
  }

  const pendingCount = bookings.filter((b) => b.status === "REQUESTED").length;

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 flex items-center gap-3">
          <Link href="/guide" className="btn-ghost text-sm">{l.backHome}</Link>
          <h1 className="section-title">{l.title}</h1>
          {pendingCount > 0 && <span className="badge-amber py-1 text-sm font-bold">{pendingCount}</span>}
        </div>

        {loading && (
          <div className="flex flex-col gap-3">
            {[1, 2, 3].map((i) => <div key={i} className="card h-32 animate-pulse p-5" />)}
          </div>
        )}
        {error && <p className="text-sm text-red-600">{error}</p>}
        {!loading && !error && bookings.length === 0 && (
          <div className="card p-10 text-center">
            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-stone-100 text-stone-400">
              <CalendarIcon className="h-7 w-7" />
            </div>
            <p className="font-bold text-stone-900">{l.emptyTitle}</p>
            <p className="mx-auto mt-1 max-w-xs text-sm leading-relaxed text-stone-400">{l.emptyDesc}</p>
          </div>
        )}

        <div className="flex flex-col gap-4">
          {bookings.map((b) => {
            const cls   = STATUS_CLS[b.status]  ?? "badge-gray";
            const icon  = STATUS_ICON[b.status] ?? "•";
            const label = t.status[b.status as keyof typeof t.status] ?? b.status;
            return (
              <div key={b.id} className="card p-5">
                {/* Traveler header + status badge */}
                <div className="mb-3 flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-base font-bold text-white shadow-md ring-2 ring-white">
                      {b.travelerName.slice(0, 1).toUpperCase()}
                    </span>
                    <div>
                      <h2 className="font-bold text-stone-900">{b.travelerName}</h2>
                      <p className="mt-0.5 text-xs text-stone-400">{l.travelerLabel}</p>
                    </div>
                  </div>
                  <span className={`${cls} flex-shrink-0 py-1`}>{icon} {label}</span>
                </div>

                {/* Requested time + price */}
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-stone-50 px-4 py-3 text-sm">
                  <span className="flex items-center gap-2 text-stone-600">
                    <CalendarIcon className="h-4 w-4 flex-shrink-0 text-stone-400" />
                    {new Date(b.startAt).toLocaleDateString(locale, { year: "numeric", month: "short", day: "numeric" })}
                    {" · "}
                    {new Date(b.startAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    {" · "}{b.hours} {l.hours}
                  </span>
                  <span className="whitespace-nowrap">
                    <span className="font-extrabold text-stone-900">{b.totalPrice.toLocaleString()}</span>
                    <span className="text-xs font-medium text-stone-400"> {b.currency}</span>
                  </span>
                </div>

                {/* Traveler message */}
                {b.message && (
                  <div className="mb-4 flex gap-2.5 rounded-xl border border-stone-100 bg-stone-50 px-4 py-3 text-sm text-stone-600">
                    <ChatIcon className="mt-0.5 h-4 w-4 flex-shrink-0 text-stone-300" />
                    <span className="italic">&ldquo;{b.message}&rdquo;</span>
                  </div>
                )}

                {/* Actions */}
                <div className="flex flex-wrap gap-2">
                  {b.status === "REQUESTED" && (
                    <>
                      <button onClick={() => act(b.id, "accept")} className="btn-primary flex-1 px-4 py-2 text-sm">{l.accept}</button>
                      <button
                        onClick={() => act(b.id, "reject")}
                        className="btn-secondary flex-1 px-4 py-2 text-sm hover:border-red-200 hover:bg-red-50 hover:text-red-600"
                      >
                        {l.reject}
                      </button>
                    </>
                  )}
                  {(b.status === "REQUESTED" || b.status === "ACCEPTED") && (
                    <Link href={`/chat/${b.id}`} className="btn-secondary px-4 py-2 text-sm">{l.chat}</Link>
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
