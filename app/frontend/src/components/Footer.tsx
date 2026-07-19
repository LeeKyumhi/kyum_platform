"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useLanguage } from "@/context/LanguageContext";
import type { Lang } from "@/lib/i18n";

/* 채팅·관리자 같은 앱형 전체화면 라우트에서는 푸터를 숨긴다 */
const HIDDEN_PREFIXES = ["/admin", "/chat", "/messages"];

const LANGS: { code: Lang; label: string }[] = [
  { code: "ko", label: "한국어" },
  { code: "en", label: "English" },
  { code: "zh", label: "中文" },
];

export default function Footer() {
  const pathname = usePathname();
  const { t, lang, setLang } = useLanguage();
  const f = t.footer;

  if (HIDDEN_PREFIXES.some((p) => pathname === p || pathname.startsWith(p + "/"))) {
    return null;
  }

  const cols = [
    {
      title: f.colService,
      links: [
        { label: f.tours, href: "/guides" },
        { label: f.companions, href: "/companions" },
        { label: f.explore, href: "/explore" },
        { label: f.trips, href: "/trips" },
        { label: f.spots, href: "/spots" },
        { label: f.community, href: "/community" },
      ],
    },
    {
      title: f.colPartner,
      links: [
        { label: f.becomePartner, href: "/become-guide" },
        { label: f.partnerHome, href: "/guide" },
        { label: f.courses, href: "/guide/courses" },
        { label: f.verification, href: "/guide/manage" },
        { label: f.responsible, href: "/legal" },
      ],
    },
    {
      title: f.colCompany,
      links: [
        { label: f.about, href: "/" },
        { label: f.newsroom, href: "#" },
        { label: f.careers, href: "#" },
        { label: f.legalNotice, href: "/legal" },
      ],
    },
  ];

  return (
    <footer className="mt-16 border-t border-stone-200/70 bg-white/70 backdrop-blur-sm">
      <div className="mx-auto w-full max-w-6xl px-4 py-10 md:px-6 md:py-12">
        {/* ── 링크 컬럼 ── */}
        <div className="grid grid-cols-2 gap-x-6 gap-y-10 md:grid-cols-3">
          {cols.map((col) => (
            <div key={col.title}>
              <h3 className="text-sm font-bold text-stone-900">{col.title}</h3>
              <ul className="mt-4 flex flex-col gap-3">
                {col.links.map((l) => (
                  <li key={l.label}>
                    <Link
                      href={l.href}
                      className="text-sm text-stone-500 transition-colors hover:text-sky-600 hover:underline"
                    >
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="my-8 border-t border-stone-200/70" />

        {/* ── 하단 바: © + 법적 링크 | 언어 + 통화 + SNS ── */}
        <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
          <div className="flex flex-wrap items-center gap-x-1.5 gap-y-2 text-xs text-stone-500">
            <span className="font-medium text-stone-600">{f.copyright}</span>
            <span aria-hidden>·</span>
            <Link href="/legal" className="hover:text-sky-600 hover:underline">
              {f.legalNotice}
            </Link>
            <span aria-hidden>·</span>
            <Link href="/legal" className="hover:text-sky-600 hover:underline">
              {f.terms}
            </Link>
            <span aria-hidden>·</span>
            <Link href="/legal" className="hover:text-sky-600 hover:underline">
              {f.privacy}
            </Link>
          </div>

          <div className="flex flex-wrap items-center gap-4">
            {/* 언어 전환 */}
            <div className="flex items-center text-xs">
              <svg
                className="mr-1.5 h-4 w-4 text-stone-500"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                aria-hidden
              >
                <circle cx="12" cy="12" r="9" />
                <path d="M3.6 9h16.8M3.6 15h16.8M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18" />
              </svg>
              {LANGS.map((l, i) => (
                <span key={l.code} className="flex items-center">
                  {i > 0 && (
                    <span className="mx-1.5 text-stone-300" aria-hidden>
                      |
                    </span>
                  )}
                  <button
                    onClick={() => setLang(l.code)}
                    className={
                      lang === l.code
                        ? "font-bold text-stone-900"
                        : "text-stone-500 transition-colors hover:text-sky-600"
                    }
                  >
                    {l.label}
                  </button>
                </span>
              ))}
            </div>

            <span className="text-xs font-semibold text-stone-600">₩ KRW</span>

            {/* SNS */}
            <div className="flex items-center gap-3 text-stone-500">
              <a href="#" aria-label="Instagram" className="transition-colors hover:text-sky-600">
                <svg className="h-[18px] w-[18px]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                  <rect x="3" y="3" width="18" height="18" rx="5" />
                  <circle cx="12" cy="12" r="4" />
                  <circle cx="17.2" cy="6.8" r="1.1" fill="currentColor" stroke="none" />
                </svg>
              </a>
              <a href="#" aria-label="X" className="transition-colors hover:text-sky-600">
                <svg className="h-[18px] w-[18px]" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M17.7 3H21l-7.2 8.2L22.2 21h-6.6l-5.2-6.1L4.6 21H1.3l7.7-8.8L1.8 3h6.8l4.7 5.6L17.7 3Zm-1.2 16h1.8L7.6 4.9H5.7L16.5 19Z" />
                </svg>
              </a>
              <a href="#" aria-label="Facebook" className="transition-colors hover:text-sky-600">
                <svg className="h-[18px] w-[18px]" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M22 12a10 10 0 1 0-11.6 9.9v-7H7.9V12h2.5V9.8c0-2.5 1.5-3.9 3.8-3.9 1.1 0 2.2.2 2.2.2v2.4h-1.2c-1.2 0-1.6.8-1.6 1.6V12h2.7l-.4 2.9h-2.3v7A10 10 0 0 0 22 12Z" />
                </svg>
              </a>
            </div>
          </div>
        </div>

        {/* ── 법적 고지 (통신판매 중개 + 관광진흥법) ── */}
        <p className="mt-6 text-[11px] leading-relaxed text-stone-400">{f.disclaimer}</p>
      </div>

      {/* 모바일 하단 탭바에 가리지 않도록 여백 */}
      <div className="h-20 md:hidden" />
    </footer>
  );
}
