"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { getToken, clearToken, getUserName } from "@/lib/api";
import { getMode, clearMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import { usePolledCount } from "@/lib/usePolledCount";
import type { Lang } from "@/lib/i18n";

const LANG_OPTIONS: { code: Lang; flag: string; label: string }[] = [
  { code: "ko", flag: "🇰🇷", label: "한국어" },
  { code: "en", flag: "🇺🇸", label: "English" },
  { code: "zh", flag: "🇨🇳", label: "中文" },
];

type Item = { href: string; icon: string; label: string; active: boolean; badge?: number };

export default function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { t, lang, setLang } = useLanguage();

  const [loggedIn, setLoggedIn] = useState(false);
  const [mode, setMode] = useState<string | null>(null);
  const [userName, setUserName] = useState<string | null>(null);
  const [langOpen, setLangOpen] = useState(false);

  useEffect(() => {
    const token = !!getToken();
    setLoggedIn(token);
    setMode(getMode());
    setUserName(token ? getUserName() : null);
  }, [pathname]);

  // 알림 배지 3종 — 30초 폴링 + 경로 변경 시 refetch (usePolledCount가 공통 처리)
  const pendingCount  = usePolledCount("/api/bookings/guide/pending-count",     loggedIn && mode === "guide");    // 가이드: 대기 중 예약 요청
  const unreadCount   = usePolledCount("/api/inbox/unread-count",               loggedIn);                        // 전 역할: 안읽은 메시지(DM+예약채팅)
  const rejectedCount = usePolledCount("/api/bookings/traveler/rejected-count", loggedIn && mode === "traveler"); // 여행자: 미확인 거절

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (!(e.target as Element).closest("[data-langmenu]")) setLangOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  function onLogout() {
    clearToken();
    clearMode();
    router.push("/");
  }

  const currentLang = LANG_OPTIONS.find((l) => l.code === lang) ?? LANG_OPTIONS[0];
  const exact = (p: string) => pathname === p;
  const under = (p: string) => pathname === p || pathname.startsWith(p + "/");
  const it = (href: string, icon: string, label: string, active: boolean): Item => ({ href, icon, label, active });

  const n = t.nav;
  // Role-based navigation
  let items: Item[];
  if (!loggedIn) {
    items = [
      it("/", "🏠", n.home, exact("/")),
      it("/guides", "🔍", n.findGuide, under("/guides")),
      it("/community", "👥", n.community, under("/community")),
      it("/explore", "🧭", n.explore, under("/explore")),
      it("/trips", "🗺️", n.trips, under("/trips")),
    ];
  } else if (mode === "guide") {
    items = [
      it("/", "🏠", n.home, exact("/")),
      it("/guide", "💼", n.guideHome, exact("/guide")),
      { ...it("/guide/requests", "📨", n.requests, under("/guide/requests")), badge: pendingCount || undefined },
      { ...it("/messages", "💬", n.messages, under("/messages")), badge: unreadCount || undefined },
      it("/community", "👥", n.community, under("/community")),
      it("/guide/availability", "📅", n.availability, under("/guide/availability")),
      it("/guide/courses", "🎫", n.courses, under("/guide/courses")),
      it("/guide/manage", "⚙️", n.manage, under("/guide/manage")),
    ];
  } else {
    items = [
      it("/", "🏠", n.home, exact("/")),
      it("/traveler", "🧳", n.travelerHome, exact("/traveler")),
      it("/guides", "🔍", n.findGuide, under("/guides")),
      { ...it("/messages", "💬", n.messages, under("/messages")), badge: unreadCount || undefined },
      it("/community", "👥", n.community, under("/community")),
      it("/explore", "🧭", n.explore, under("/explore")),
      it("/trips", "🗺️", n.trips, under("/trips")),
      { ...it("/traveler/bookings", "📋", n.bookings, under("/traveler/bookings")), badge: rejectedCount || undefined },
      it("/traveler/following", "💙", n.following, under("/traveler/following")),
    ];
  }

  // Compact 5-item set for the mobile bottom bar
  let mobileItems: Item[];
  if (!loggedIn) {
    // 홈·가이드찾기·커뮤니티·여행일정 + 로그인 (탐색은 데스크탑/홈 배너로)
    mobileItems = [
      items[0], items[1], items[2], items[4],
      it("/login", "👤", n.login, under("/login")),
    ];
  } else if (mode === "guide") {
    // 커뮤니티는 가이드 홈의 배너로 진입 (하단 탭 5개 제한)
    mobileItems = [
      it("/guide", "💼", n.guideHome, exact("/guide")),
      { ...it("/guide/requests", "📨", n.requests, under("/guide/requests")), badge: pendingCount || undefined },
      { ...it("/messages", "💬", n.messages, under("/messages")), badge: unreadCount || undefined },
      it("/guide/availability", "📅", n.availability, under("/guide/availability")),
      it("/profile", "👤", n.profile, under("/profile")),
    ];
  } else {
    // 커뮤니티는 여행자 홈의 배너로 진입 (하단 탭 5개 제한)
    mobileItems = [
      it("/traveler", "🧳", n.travelerHome, exact("/traveler")),
      it("/guides", "🔍", n.findGuide, under("/guides")),
      { ...it("/messages", "💬", n.messages, under("/messages")), badge: unreadCount || undefined },
      it("/trips", "🗺️", n.trips, under("/trips")),
      it("/profile", "👤", n.profile, under("/profile")),
    ];
  }

  const avatarInitial = userName ? userName.slice(0, 1).toUpperCase() : "?";

  const langMenu = (
    <div className="absolute bottom-full left-0 z-50 mb-2 w-full min-w-[10rem] rounded-xl border border-stone-100 bg-white py-1 shadow-lg">
      {LANG_OPTIONS.map((opt) => (
        <button
          key={opt.code}
          onClick={() => { setLang(opt.code); setLangOpen(false); }}
          className={`flex w-full items-center gap-2.5 px-3 py-2 text-sm transition-colors hover:bg-sky-50 ${
            lang === opt.code ? "font-semibold text-sky-600" : "text-stone-700"
          }`}
        >
          <span>{opt.flag}</span>
          <span>{opt.label}</span>
          {lang === opt.code && <span className="ml-auto text-sky-500">✓</span>}
        </button>
      ))}
    </div>
  );

  function railLink(item: Item) {
    return (
      <Link
        key={item.href + item.label}
        href={item.href}
        className={`flex items-center gap-3.5 rounded-full px-4 py-2.5 text-[15px] transition-colors ${
          item.active
            ? "bg-sky-50 font-semibold text-sky-600"
            : "font-medium text-stone-700 hover:bg-stone-100"
        }`}
      >
        <span className="text-xl leading-none">{item.icon}</span>
        <span>{item.label}</span>
        {item.badge != null && item.badge > 0 && (
          <span className="ml-auto flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-red-500 px-1.5 text-[11px] font-bold text-white">
            {item.badge > 99 ? "99+" : item.badge}
          </span>
        )}
      </Link>
    );
  }

  return (
    <>
      {/* ───────── Desktop left rail ───────── */}
      <aside className="fixed left-0 top-0 z-40 hidden h-screen w-64 flex-col border-r border-stone-100 bg-white px-3 py-5 md:flex">
        <Link href="/" className="mb-6 flex items-center px-2 transition-opacity hover:opacity-80">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo.png" alt="peerup" className="h-9 w-auto" />
        </Link>

        <nav className="flex flex-col gap-1 overflow-y-auto">{items.map(railLink)}</nav>

        <div className="mt-auto flex flex-col gap-2 pt-4">
          <div data-langmenu className="relative">
            <button
              onClick={() => setLangOpen((o) => !o)}
              className="flex w-full items-center gap-2.5 rounded-full px-4 py-2.5 text-sm font-medium text-stone-600 transition-colors hover:bg-stone-100"
              title="언어 선택 / Language"
            >
              <span className="text-lg">{currentLang.flag}</span>
              <span>{currentLang.label}</span>
              <span className="ml-auto text-xs text-stone-400">▾</span>
            </button>
            {langOpen && langMenu}
          </div>

          {loggedIn ? (
            <>
              <Link href="/select-mode" className="rounded-full px-4 py-2 text-left text-sm font-medium text-stone-500 transition-colors hover:bg-stone-100">
                🔄 {n.switchMode}
              </Link>
              <Link href="/profile" className="flex items-center gap-3 rounded-2xl border border-stone-100 bg-stone-50/60 px-3 py-2.5 transition-colors hover:bg-stone-100">
                <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-500 to-cyan-500 text-sm font-bold text-white">
                  {avatarInitial}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-stone-900">{userName ?? ""}</span>
                  <span className="block text-xs text-stone-400">
                    {mode === "guide" ? "🗺️ Guide" : mode === "traveler" ? "🧳 Traveler" : ""}
                  </span>
                </span>
              </Link>
              <button
                onClick={onLogout}
                className="rounded-full px-4 py-2 text-left text-sm font-medium text-stone-400 transition-colors hover:bg-red-50 hover:text-red-500"
              >
                {n.logout}
              </button>
            </>
          ) : (
            <div className="flex flex-col gap-2">
              <Link href="/login" className="btn-secondary w-full">{n.login}</Link>
              <Link href="/signup" className="btn-primary w-full">{n.signup}</Link>
            </div>
          )}
        </div>
      </aside>

      {/* ───────── Mobile top bar ───────── */}
      <header className="fixed left-0 right-0 top-0 z-40 flex h-14 items-center justify-between border-b border-stone-100 bg-white/95 px-4 backdrop-blur-md md:hidden">
        <Link href="/" className="flex items-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo.png" alt="peerup" className="h-8 w-auto" />
        </Link>
        <div data-langmenu className="relative">
          <button
            onClick={() => setLangOpen((o) => !o)}
            className="flex items-center gap-1.5 rounded-full border border-stone-200 px-3 py-1.5 text-sm font-medium text-stone-600"
            title="언어 선택 / Language"
          >
            <span className="text-base">{currentLang.flag}</span>
            <span className="text-xs">{currentLang.label}</span>
            <span className="text-[10px] text-stone-400">▾</span>
          </button>
          {langOpen && (
            <div className="absolute right-0 top-full z-50 mt-2 w-36 rounded-xl border border-stone-100 bg-white py-1 shadow-lg">
              {LANG_OPTIONS.map((opt) => (
                <button
                  key={opt.code}
                  onClick={() => { setLang(opt.code); setLangOpen(false); }}
                  className={`flex w-full items-center gap-2.5 px-3 py-2 text-sm transition-colors hover:bg-sky-50 ${
                    lang === opt.code ? "font-semibold text-sky-600" : "text-stone-700"
                  }`}
                >
                  <span>{opt.flag}</span>
                  <span>{opt.label}</span>
                  {lang === opt.code && <span className="ml-auto text-sky-500">✓</span>}
                </button>
              ))}
            </div>
          )}
        </div>
      </header>

      {/* ───────── Mobile bottom tab bar ───────── */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 flex h-16 border-t border-stone-100 bg-white/95 backdrop-blur-md md:hidden">
        {mobileItems.map((item) => (
          <Link
            key={item.href + item.label}
            href={item.href}
            className={`flex flex-1 flex-col items-center justify-center gap-0.5 text-[10px] transition-colors ${
              item.active ? "font-semibold text-sky-600" : "font-medium text-stone-400"
            }`}
          >
            <span className={`relative flex h-7 items-center justify-center rounded-full px-4 text-xl leading-none transition-colors ${
              item.active ? "bg-sky-50" : ""
            }`}>
              {item.icon}
              {item.badge != null && item.badge > 0 && (
                <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-red-500 px-1 text-[9px] font-bold text-white">
                  {item.badge > 99 ? "99+" : item.badge}
                </span>
              )}
            </span>
            <span className="max-w-full truncate px-0.5">{item.label}</span>
          </Link>
        ))}
      </nav>
    </>
  );
}
