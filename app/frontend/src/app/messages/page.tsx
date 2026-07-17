"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { localeOf } from "@/lib/i18n";
import { ChatIcon } from "@/components/icons";
import { cardPreview } from "@/lib/placeCard";

type InboxThread = {
  type: "dm" | "booking";
  conversationId: number | null;
  bookingId: number | null;
  guideProfileId: number;
  otherUserId: number | null;
  otherName: string;
  otherAvatarUrl: string | null;
  otherIsGuide: boolean;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  bookingStatus: string | null;
  unread: boolean;
};

function formatWhen(iso: string | null, locale: string) {
  if (!iso) return "";
  const d = new Date(iso);
  const today = new Date();
  const sameDay = d.toDateString() === today.toDateString();
  return sameDay
    ? d.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })
    : d.toLocaleDateString(locale, { month: "short", day: "numeric" });
}

/** 통합 인박스 — 예약 전 문의(DM) + 예약 채팅을 한 목록으로 (여행자/가이드 양방향 모두). */
export default function MessagesPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const l = t.dm;

  const [threads, setThreads] = useState<InboxThread[] | null>(null);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<InboxThread[]>("/api/inbox", { auth: true })
      .then(setThreads)
      .catch(() => setThreads([]));
  }, [router]);

  const locale = localeOf(lang);

  const href = (thread: InboxThread) =>
    thread.type === "booking" ? `/chat/${thread.bookingId}` : `/messages/${thread.conversationId}`;

  return (
    <main className="page px-4">
      <div className="container-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-stone-900">💬 {l.inboxTitle}</h1>
        <p className="mb-5 mt-1 text-sm text-stone-500">{l.inboxSub}</p>

        {threads === null ? (
          <div className="flex flex-col gap-2">
            {[1, 2, 3].map((i) => <div key={i} className="card h-20 animate-pulse" />)}
          </div>
        ) : threads.length === 0 ? (
          <div className="card p-10 text-center">
            <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-stone-100 text-stone-400">
              <ChatIcon className="h-6 w-6" />
            </div>
            <p className="text-sm font-semibold text-stone-600">{l.empty}</p>
            <p className="mx-auto mt-1 max-w-xs text-xs leading-relaxed text-stone-400">{l.emptySub}</p>
            <Link href="/guides" className="btn-primary mt-4 inline-block">{t.nav.findGuide}</Link>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {threads.map((c) => (
              <Link key={`${c.type}-${c.conversationId ?? c.bookingId}`} href={href(c)} className="card-hover flex items-center gap-3.5 p-4">
                {c.otherAvatarUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={c.otherAvatarUrl} alt={c.otherName} className="h-12 w-12 flex-shrink-0 rounded-full object-cover ring-2 ring-white shadow-md" />
                ) : (
                  <span className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 font-bold text-white ring-2 ring-white shadow-md">
                    {c.otherName.slice(0, 1).toUpperCase()}
                  </span>
                )}
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span className="truncate text-sm font-bold text-stone-900">{c.otherName}</span>
                    {c.type === "booking" ? (
                      <span className="flex-shrink-0 rounded-md bg-amber-50 px-1.5 py-0.5 text-[10px] font-bold text-amber-600">
                        🎫 {l.bookingBadge}
                      </span>
                    ) : (
                      <span className={`flex-shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-bold ${
                        c.otherIsGuide ? "bg-emerald-50 text-emerald-600" : "bg-sky-50 text-sky-600"
                      }`}>
                        {c.otherIsGuide ? l.guideBadge : l.travelerBadge}
                      </span>
                    )}
                  </span>
                  <span className={`mt-0.5 block truncate text-xs ${c.unread ? "font-bold text-stone-800" : "text-stone-500"}`}>
                    {cardPreview(c.lastMessagePreview, { place: t.placeCard.sharedPreview, itinerary: t.share.sharedItinerary, course: t.share.sharedCourse }) ?? c.lastMessagePreview ?? l.noMessages}
                  </span>
                </span>
                <span className="flex flex-shrink-0 flex-col items-end gap-1.5 self-start pt-1">
                  <span className="text-[11px] text-stone-400">{formatWhen(c.lastMessageAt, locale)}</span>
                  {c.unread && <span className="h-2.5 w-2.5 rounded-full bg-sky-500 shadow-sm" aria-label={l.unreadDot} />}
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
