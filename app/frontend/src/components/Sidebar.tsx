"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { getToken, clearToken, getUserName } from "@/lib/api";
import { getMode, clearMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import type { Lang } from "@/lib/i18n";

const LANG_OPTIONS: { code: Lang; flag: string; label: string }[] = [
  { code: "ko", flag: "🇰🇷", label: "한국어" },
  { code: "en", flag: "🇺🇸", label: "English" },
  { code: "zh", flag: "🇨🇳", label: "中文" },
];

type Item = { href: string; icon: string; label: string; active: boolean };

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
      it("/explore", "🧭", n.explore, under("/explore")),
      it("/trips", "🗺️", n.trips, under("/trips")),
    ];
  } else if (mode === "guide") {
    items = [
      it("/", "🏠", n.home, exact("/")),
      it("/guide", "💼", n.guideHome, exact("/guide")),
      it("/guide/requests", "📨", n.requests, under("/guide/requests")),
      it("/guide/posts", "📝", n.posts, under("/guide/posts")),
      it("/guide/manage", "⚙️", n.manage, under("/guide/manage")),
      it("/explore", "🧭", n.explore, under("/explore")),
      it("/trips", "🗺️", n.trips, under("/trips")),
    ];
  } else {
    items = [
      it("/", "🏠", n.home, exact("/")),
      it("/traveler", "🧳", n.travelerHome, exact("/traveler")),
      it("/guides", "🔍", n.findGuide, under("/guides")),
      it("/explore", "🧭", n.explore, under("/explore")),
      it("/trips", "🗺️", n.trips, under("/trips")),
      it("/traveler/bookings", "📋", n.bookings, under("/traveler/bookings")),
      it("/traveler/following", "❤️", n.following, under("/traveler/following")),
    ];
  }

  // Compact 5-item set for the mobile bottom bar
  let mobileItems: Item[];
  if (!loggedIn) {
    mobileItems = [
      items[0], items[1], items[2], items[3],
      it("/login", "👤", n.login, under("/login")),
    ];
  } else if (mode === "guide") {
    mobileItems = [
      it("/", "🏠", n.home, exact("/")),
      it("/guide", "💼", n.guideHome, exact("/guide")),
      it("/guide/requests", "📨", n.requests, under("/guide/requests")),
      it("/guide/posts", "📝", n.posts, under("/guide/posts")),
      it("/profile", "👤", n.profile, under("/profile")),
    ];
  } else {
    mobileItems = [
      it("/", "🏠", n.home, exact("/")),
      it("/traveler", "🧳", n.travelerHome, exact("/traveler")),
      it("/guides", "🔍", n.findGuide, under("/guides")),
      it("/trips", "🗺️", n.trips, under("/trips")),
      it("/profile", "👤", n.profile, under("/profile")),
    ];
  }

  const avatarInitial = userName ? userName.slice(0, 1).toUpperCase() : "?";

  const langMenu = (
    <div className="absolute bottom-full left-0 z-50 mb-2 w-full min-w-[10rem] rounded-xl border border-gray-100 bg-white py-1 shadow-lg">
      {LANG_OPTIONS.map((opt) => (
        <button
          key={opt.code}
          onClick={() => { setLang(opt.code); setLangOpen(false); }}
          className={`flex w-full items-center gap-2.5 px-3 py-2 text-sm transition-colors hover:bg-indigo-50 ${
            lang === opt.code ? "font-semibold text-indigo-600" : "text-gray-700"
          }`}
        >
          <span>{opt.flag}</span>
          <span>{opt.label}</span>
          {lang === opt.code && <span className="ml-auto text-indigo-500">✓</span>}
        </button>
      ))}
    </div>
  );

  function railLink(item: Item) {
    return (
      <Link
        key={item.href + item.label}
        href={item.href}
        className={`flex items-center gap-3.5 rounded-xl px-3 py-2.5 text-[15px] transition-colors ${
          item.active
            ? "bg-indigo-50 font-semibold text-indigo-700"
            : "font-medium text-gray-700 hover:bg-gray-100"
        }`}
      >
        <span className="text-xl leading-none">{item.icon}</span>
        <span>{item.label}</span>
      </Link>
    );
  }

  return (
    <>
      {/* ───────── Desktop left rail ───────── */}
      <aside className="fixed left-0 top-0 z-40 hidden h-screen w-64 flex-col border-r border-gray-100 bg-white px-3 py-5 md:flex">
        <Link href="/" className="mb-6 flex items-center px-2 transition-opacity hover:opacity-80">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo.png" alt="peerup" className="h-9 w-auto" />
        </Link>

        <nav className="flex flex-col gap-1 overflow-y-auto">{items.map(railLink)}</nav>

        <div className="mt-auto flex flex-col gap-2 pt-4">
          <div data-langmenu className="relative">
            <button
              onClick={() => setLangOpen((o) => !o)}
              className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100"
              title="언어 선택 / Language"
            >
              <span className="text-lg">{currentLang.flag}</span>
              <span>{currentLang.label}</span>
              <span className="ml-auto text-xs text-gray-400">▾</span>
            </button>
            {langOpen && langMenu}
          </div>

          {loggedIn ? (
            <>
              <Link href="/select-mode" className="rounded-xl px-3 py-2 text-left text-sm font-medium text-gray-500 transition-colors hover:bg-gray-100">
                🔄 {n.switchMode}
              </Link>
              <Link href="/profile" className="flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors hover:bg-gray-100">
                <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-indigo-600 text-sm font-bold text-white">
                  {avatarInitial}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-gray-900">{userName ?? ""}</span>
                  <span className="block text-xs text-gray-400">
                    {mode === "guide" ? "🗺️ Guide" : mode === "traveler" ? "🧳 Traveler" : ""}
                  </span>
                </span>
              </Link>
              <button
                onClick={onLogout}
                className="rounded-xl px-3 py-2 text-left text-sm font-medium text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500"
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
      <header className="fixed left-0 right-0 top-0 z-40 flex h-14 items-center justify-between border-b border-gray-100 bg-white/95 px-4 backdrop-blur-md md:hidden">
        <Link href="/" className="flex items-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo.png" alt="peerup" className="h-8 w-auto" />
        </Link>
        <div data-langmenu className="relative">
          <button
            onClick={() => setLangOpen((o) => !o)}
            className="flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm font-medium text-gray-600"
            title="언어 선택 / Language"
          >
            <span className="text-base">{currentLang.flag}</span>
            <span className="text-xs">{currentLang.label}</span>
            <span className="text-[10px] text-gray-400">▾</span>
          </button>
          {langOpen && (
            <div className="absolute right-0 top-full z-50 mt-2 w-36 rounded-xl border border-gray-100 bg-white py-1 shadow-lg">
              {LANG_OPTIONS.map((opt) => (
                <button
                  key={opt.code}
                  onClick={() => { setLang(opt.code); setLangOpen(false); }}
                  className={`flex w-full items-center gap-2.5 px-3 py-2 text-sm transition-colors hover:bg-indigo-50 ${
                    lang === opt.code ? "font-semibold text-indigo-600" : "text-gray-700"
                  }`}
                >
                  <span>{opt.flag}</span>
                  <span>{opt.label}</span>
                  {lang === opt.code && <span className="ml-auto text-indigo-500">✓</span>}
                </button>
              ))}
            </div>
          )}
        </div>
      </header>

      {/* ───────── Mobile bottom tab bar ───────── */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 flex h-16 border-t border-gray-100 bg-white/95 backdrop-blur-md md:hidden">
        {mobileItems.map((item) => (
          <Link
            key={item.href + item.label}
            href={item.href}
            className={`flex flex-1 flex-col items-center justify-center gap-0.5 text-[10px] font-medium transition-colors ${
              item.active ? "text-indigo-600" : "text-gray-400"
            }`}
          >
            <span className="text-xl leading-none">{item.icon}</span>
            <span className="max-w-full truncate px-0.5">{item.label}</span>
          </Link>
        ))}
      </nav>
    </>
  );
}
