"use client";

import { useEffect, useRef, useState } from "react";
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

export default function Navbar() {
  const pathname  = usePathname();
  const router    = useRouter();
  const { t, lang, setLang } = useLanguage();

  const [loggedIn, setLoggedIn]   = useState(false);
  const [mode, setMode]           = useState<string | null>(null);
  const [userName, setUserName]   = useState<string | null>(null);
  const [scrolled, setScrolled]   = useState(false);
  const [menuOpen, setMenuOpen]   = useState(false);
  const [langOpen, setLangOpen]   = useState(false);
  const langRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const token = !!getToken();
    setLoggedIn(token);
    setMode(getMode());
    setUserName(token ? getUserName() : null);
  }, [pathname]);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  // Close lang dropdown on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (langRef.current && !langRef.current.contains(e.target as Node)) {
        setLangOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  function onLogout() {
    clearToken(); clearMode(); setMenuOpen(false); router.push("/");
  }

  function selectLang(code: Lang) {
    setLang(code); setLangOpen(false); setMenuOpen(false);
  }

  const currentLang = LANG_OPTIONS.find((l) => l.code === lang) ?? LANG_OPTIONS[0];
  const dashboardHref = mode === "guide" ? "/guide" : mode === "traveler" ? "/traveler" : "/select-mode";
  const isLanding = pathname === "/";
  const isTransparent = !scrolled && isLanding;

  const ghostCls = isTransparent
    ? "btn-ghost text-white/80 hover:text-white hover:bg-white/10"
    : "btn-ghost";

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-200 ${
        isTransparent ? "bg-transparent" : "bg-white/90 backdrop-blur-md border-b border-gray-100 shadow-sm"
      }`}
    >
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
        {/* Logo */}
        <Link href="/"
          className="flex items-center hover:opacity-80 transition-opacity">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/logo.png"
            alt="peerup"
            className={`h-10 w-auto ${isTransparent ? "drop-shadow-[0_1px_4px_rgba(0,0,0,0.35)]" : ""}`}
          />
        </Link>

        {/* Desktop nav */}
        <nav className="hidden sm:flex items-center gap-1">
          <Link href="/guides" className={ghostCls}>{t.nav.findGuide}</Link>

          {loggedIn ? (
            <>
              <Link href={dashboardHref} className={ghostCls}>{t.nav.dashboard}</Link>
              <Link
                href="/profile"
                className={`relative flex items-center justify-center w-8 h-8 rounded-full font-bold text-sm transition-opacity hover:opacity-80 flex-shrink-0 ml-1 ${
                  isTransparent
                    ? "bg-white/20 text-white ring-2 ring-white/40"
                    : "bg-indigo-600 text-white ring-2 ring-indigo-100"
                }`}
                title={userName ?? ""}
              >
                {userName ? userName.slice(0, 1).toUpperCase() : "?"}
              </Link>
            </>
          ) : (
            <>
              <Link href="/login" className={ghostCls}>{t.nav.login}</Link>
              <Link href="/signup" className="btn-primary py-2 text-sm ml-1">{t.nav.signup}</Link>
            </>
          )}

          {/* Language dropdown */}
          <div ref={langRef} className="relative ml-1">
            <button
              onClick={() => setLangOpen(!langOpen)}
              className={`${ghostCls} flex items-center gap-1.5 text-sm font-medium`}
              title="언어 선택"
            >
              <span>{currentLang.flag}</span>
              <span className="text-xs opacity-80">{currentLang.label}</span>
              <span className="text-xs opacity-50">▾</span>
            </button>
            {langOpen && (
              <div className="absolute right-0 top-full mt-2 w-36 bg-white rounded-xl shadow-lg border border-gray-100 py-1 z-50">
                {LANG_OPTIONS.map((opt) => (
                  <button
                    key={opt.code}
                    onClick={() => selectLang(opt.code)}
                    className={`w-full flex items-center gap-2.5 px-3 py-2 text-sm hover:bg-indigo-50 transition-colors ${
                      lang === opt.code ? "text-indigo-600 font-semibold" : "text-gray-700"
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
        </nav>

        {/* Mobile hamburger */}
        <button className="sm:hidden btn-ghost" onClick={() => setMenuOpen(!menuOpen)} aria-label={t.nav.menu}>
          <span className={`text-xl ${isTransparent ? "text-white" : ""}`}>{menuOpen ? "✕" : "☰"}</span>
        </button>
      </div>

      {/* Mobile menu */}
      {menuOpen && (
        <div className="sm:hidden bg-white border-t border-gray-100 px-4 pb-4 flex flex-col gap-1 shadow-lg">
          <Link href="/guides" className="btn-ghost justify-start py-3" onClick={() => setMenuOpen(false)}>
            {t.nav.findGuide}
          </Link>
          {loggedIn ? (
            <>
              <Link href={dashboardHref} className="btn-ghost justify-start py-3" onClick={() => setMenuOpen(false)}>
                {t.nav.dashboard}
              </Link>
              <Link href="/profile" className="btn-ghost justify-start py-3 flex items-center gap-3" onClick={() => setMenuOpen(false)}>
                <span className="flex items-center justify-center w-7 h-7 rounded-full bg-indigo-600 text-white text-xs font-bold flex-shrink-0">
                  {userName ? userName.slice(0, 1).toUpperCase() : "?"}
                </span>
                <span>{userName ?? t.nav.dashboard}</span>
              </Link>
              <button onClick={onLogout} className="btn-ghost justify-start py-3 text-red-500 hover:text-red-600 hover:bg-red-50">
                {t.nav.logout}
              </button>
            </>
          ) : (
            <>
              <Link href="/login" className="btn-ghost justify-start py-3" onClick={() => setMenuOpen(false)}>
                {t.nav.login}
              </Link>
              <Link href="/signup" className="btn-primary mt-1 w-full" onClick={() => setMenuOpen(false)}>
                {t.nav.signup}
              </Link>
            </>
          )}

          {/* Language selector mobile */}
          <div className="border-t border-gray-100 mt-1 pt-2">
            <p className="text-xs text-gray-400 px-2 py-1">언어 / Language</p>
            <div className="flex gap-1">
              {LANG_OPTIONS.map((opt) => (
                <button
                  key={opt.code}
                  onClick={() => selectLang(opt.code)}
                  className={`flex-1 flex flex-col items-center gap-0.5 py-2 rounded-xl text-xs transition-colors ${
                    lang === opt.code
                      ? "bg-indigo-100 text-indigo-700 font-semibold"
                      : "text-gray-500 hover:bg-gray-50"
                  }`}
                >
                  <span className="text-xl">{opt.flag}</span>
                  <span>{opt.label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
