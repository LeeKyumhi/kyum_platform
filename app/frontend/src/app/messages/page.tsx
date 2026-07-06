"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { ChatIcon } from "@/components/icons";

type Conversation = {
  id: number; guideProfileId: number; otherName: string;
  otherAvatarUrl: string | null; otherIsGuide: boolean;
  lastMessagePreview: string | null; lastMessageAt: string | null;
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

/** 메시지 인박스 — 예약 전 문의 대화 목록 (여행자로 보낸 방 + 가이드로 받은 방 모두). */
export default function MessagesPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const l = t.dm;

  const [conversations, setConversations] = useState<Conversation[] | null>(null);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Conversation[]>("/api/conversations", { auth: true })
      .then(setConversations)
      .catch(() => setConversations([]));
  }, [router]);

  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  return (
    <main className="page px-4">
      <div className="container-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-stone-900">💬 {l.inboxTitle}</h1>
        <p className="mb-5 mt-1 text-sm text-stone-500">{l.inboxSub}</p>

        {conversations === null ? (
          <div className="flex flex-col gap-2">
            {[1, 2, 3].map((i) => <div key={i} className="card h-20 animate-pulse" />)}
          </div>
        ) : conversations.length === 0 ? (
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
            {conversations.map((c) => (
              <Link key={c.id} href={`/messages/${c.id}`} className="card-hover flex items-center gap-3.5 p-4">
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
                    <span className={`flex-shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-bold ${
                      c.otherIsGuide ? "bg-emerald-50 text-emerald-600" : "bg-sky-50 text-sky-600"
                    }`}>
                      {c.otherIsGuide ? l.guideBadge : l.travelerBadge}
                    </span>
                  </span>
                  <span className="mt-0.5 block truncate text-xs text-stone-500">
                    {c.lastMessagePreview ?? l.noMessages}
                  </span>
                </span>
                <span className="flex-shrink-0 self-start pt-1 text-[11px] text-stone-400">
                  {formatWhen(c.lastMessageAt, locale)}
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
