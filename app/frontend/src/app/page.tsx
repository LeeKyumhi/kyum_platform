"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLanguage } from "@/context/LanguageContext";
import { getToken, api } from "@/lib/api";
import { getMode, setMode, clearMode, type Mode } from "@/lib/mode";
import { getTrack, TRACK_CHANGED_EVENT, type Track } from "@/lib/track";
import { SPOTS } from "@/lib/spots";
import CitySelect from "@/components/CitySelect";
import { SearchIcon, PinIcon, CompassIcon, ArrowRightIcon } from "@/components/icons";
import CompanionLanding from "@/components/CompanionLanding";

type Me = { id: number; fullName: string; email: string };

const HERO_IMG = SPOTS[2].img;      // Bukchon at blue hour
const CTA_IMG = SPOTS[3].img;       // Haeundae Beach
const GUIDE_HERO_IMG = SPOTS[5].img; // Jeonju Hanok Village

/* ── 모드 선택 카드 (select-mode 페이지의 타일 패턴을 그대로 미러링,
      단 라우팅 대신 로컬 상태만 바꿔 같은 페이지를 맞춤 렌더링) ── */
function ModeChooser({ onPick }: { onPick: (m: Mode) => void }) {
  const { t } = useLanguage();
  const l = t.landing;
  const lm = t.selectMode;

  const MODES = [
    { id: "traveler" as Mode, icon: "🧳", title: lm.travelerTitle, desc: lm.travelerDesc, grad: "from-sky-400 to-cyan-400" },
    { id: "guide" as Mode, icon: "🗺️", title: lm.guideTitle, desc: lm.guideDesc, grad: "from-emerald-400 to-teal-500" },
  ];

  return (
    <section className="px-3 pt-[4.25rem] md:px-6 md:pt-10">
      <div className="mx-auto max-w-4xl animate-fade-up">
        <div className="card p-6 md:p-8">
          <div className="mb-6 text-center">
            <h2 className="text-2xl font-extrabold tracking-tight text-stone-900 md:text-3xl">{l.modeQ}</h2>
            <p className="mt-2 text-sm text-stone-500">{l.modeQSub}</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {MODES.map((m) => (
              <button
                key={m.id}
                onClick={() => onPick(m.id)}
                className="card-hover group flex flex-col p-6 text-left active:scale-[0.98] md:p-8"
              >
                <div className={`mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br ${m.grad} text-2xl shadow-md`}>
                  {m.icon}
                </div>
                <h3 className="mb-1.5 text-lg font-bold text-stone-900">{m.title}</h3>
                <p className="text-sm leading-relaxed text-stone-500">{m.desc}</p>
                <span className={`mt-4 inline-flex items-center gap-1.5 text-sm font-semibold ${m.id === "guide" ? "text-emerald-500" : "text-sky-500"}`}>
                  {lm.choose}
                  <ArrowRightIcon className="h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5" />
                </span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

/* ── 히어로 우상단 "다른 모드로 보기" 버튼 ── */
function SwitchViewButton({ onClick }: { onClick: () => void }) {
  const { t } = useLanguage();
  return (
    <button
      onClick={onClick}
      className="absolute right-4 top-4 z-10 inline-flex items-center gap-1.5 rounded-full border border-white/25 bg-white/15 px-3.5 py-1.5 text-xs font-semibold text-white backdrop-blur-md transition-colors hover:bg-white/25"
    >
      ⇄ {t.landing.switchView}
    </button>
  );
}

/* ── Airbnb 스타일 3분할 검색 바 (여행자 모드 히어로 전용) ── */
function TravelerSearchBar() {
  const { t } = useLanguage();
  const l = t.landing;
  const router = useRouter();
  const [city, setCity] = useState("");
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [guests, setGuests] = useState(1);

  function submit() {
    const q = new URLSearchParams();
    if (city) q.set("city", city);
    // 체크인·체크아웃이 모두 있으면 해당 기간에 가능 슬롯이 있는 가이드만 필터
    if (checkIn && checkOut) { q.set("from", checkIn); q.set("to", checkOut); }
    q.set("guests", String(guests));
    router.push(`/guides?${q.toString()}`);
  }

  return (
    <div className="mt-9 w-full max-w-3xl rounded-3xl bg-white p-4 text-left shadow-2xl md:rounded-full md:p-2 md:pl-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:gap-0">
        {/* 지역 */}
        <div className="min-w-0 md:flex-[1.4] md:pr-4">
          <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-stone-500">{l.searchCity}</p>
          <CitySelect value={city} onChange={(c) => setCity(c)} />
        </div>
        <div className="hidden h-12 w-px flex-shrink-0 bg-stone-200 md:block" />
        {/* 체크인 */}
        <div className="md:w-36 md:flex-shrink-0 md:px-3">
          <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-stone-500">{l.searchCheckIn}</p>
          <input
            type="date"
            value={checkIn}
            onChange={(e) => { setCheckIn(e.target.value); if (checkOut && e.target.value > checkOut) setCheckOut(e.target.value); }}
            className="input w-full text-sm"
          />
        </div>
        <div className="hidden h-12 w-px flex-shrink-0 bg-stone-200 md:block" />
        {/* 체크아웃 */}
        <div className="md:w-36 md:flex-shrink-0 md:px-3">
          <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-stone-500">{l.searchCheckOut}</p>
          <input
            type="date"
            value={checkOut}
            min={checkIn || undefined}
            onChange={(e) => setCheckOut(e.target.value)}
            className="input w-full text-sm"
          />
        </div>
        <div className="hidden h-12 w-px flex-shrink-0 bg-stone-200 md:block" />
        {/* 인원 */}
        <div className="md:w-24 md:flex-shrink-0 md:px-3">
          <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-stone-500">{l.searchGuests}</p>
          <input
            type="number"
            min={1}
            value={guests}
            onChange={(e) => setGuests(Math.max(1, Number(e.target.value) || 1))}
            className="input w-full"
          />
        </div>
        {/* 검색 */}
        <button
          onClick={submit}
          className="flex h-11 flex-shrink-0 items-center justify-center gap-2 rounded-full bg-gradient-to-r from-sky-500 to-cyan-500 px-6 font-bold text-white shadow-md shadow-sky-500/30 transition-all hover:shadow-lg hover:brightness-[1.06] md:ml-2 md:h-12 md:w-12 md:px-0"
          aria-label={l.searchGo}
        >
          <SearchIcon className="h-5 w-5" />
          <span className="md:hidden">{l.searchGo}</span>
        </button>
      </div>
    </div>
  );
}

export default function Home() {
  const { t, lang } = useLanguage();
  const l = t.landing;

  const [mode, setModeState] = useState<Mode | null>(null);
  const [track, setTrackState] = useState<Track | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [hasToken, setHasToken] = useState(false);

  useEffect(() => {
    setModeState(getMode());
    setTrackState(getTrack());
    // 첫 방문 온보딩 모달(언어→역할)·TrackGate(세계)가 값을 정하면 이벤트로 알려준다
    const onModeChanged = () => setModeState(getMode());
    const onTrackChanged = () => setTrackState(getTrack());
    window.addEventListener("peerup-mode-changed", onModeChanged);
    window.addEventListener(TRACK_CHANGED_EVENT, onTrackChanged);
    if (getToken()) {
      setHasToken(true);
      api<Me>("/api/users/me", { auth: true }).then(setMe).catch(() => {});
    }
    return () => {
      window.removeEventListener("peerup-mode-changed", onModeChanged);
      window.removeEventListener(TRACK_CHANGED_EVENT, onTrackChanged);
    };
  }, []);

  function pickMode(m: Mode) {
    setMode(m);          // localStorage (기존 mode.ts 키 재사용)
    setModeState(m);     // 페이지 이동 없이 같은 "/"를 맞춤 렌더링
    // TrackGate가 모드 확정을 감지해 세계 선택을 띄우도록 알린다 (모드 먼저 → 세계)
    window.dispatchEvent(new Event("peerup-mode-changed"));
  }
  function resetMode() {
    clearMode();
    setModeState(null);  // 선택 화면으로 복귀 (새로고침 없이)
    window.dispatchEvent(new Event("peerup-mode-changed"));
  }

  const features = [
    { icon: "🤝", title: l.f1title, desc: l.f1desc, href: "/guides", grad: "from-sky-400 to-cyan-400" },
    { icon: "💬", title: l.f2title, desc: l.f2desc, grad: "from-violet-400 to-purple-400" },
    { icon: "🧭", title: t.explore.title, desc: t.explore.subtitle, href: "/explore", grad: "from-emerald-400 to-teal-500" },
    { icon: "🗺️", title: t.itinerary.title, desc: t.itinerary.subtitle, href: "/trips", grad: "from-amber-400 to-orange-400" },
    { icon: "⭐", title: l.f3title, desc: l.f3desc, grad: "from-yellow-400 to-amber-500" },
    { icon: "❤️", title: l.followTitle, desc: l.followDesc, grad: "from-rose-400 to-pink-500" },
  ];
  const steps = [
    { num: "01", title: l.s1title, desc: l.s1desc },
    { num: "02", title: l.s2title, desc: l.s2desc },
    { num: "03", title: l.s3title, desc: l.s3desc },
  ];
  const stats = [
    { val: l.stat1val, label: l.stat1label },
    { val: l.stat2val, label: l.stat2label },
    { val: l.stat3val, label: l.stat3label },
  ];
  const guidePerks = [
    { icon: "💰", title: l.guidePerk1title, desc: l.guidePerk1desc },
    { icon: "💬", title: l.guidePerk2title, desc: l.guidePerk2desc },
    { icon: "⭐", title: l.guidePerk3title, desc: l.guidePerk3desc },
  ];

  /* ═══════════ 동행 세계 전용 랜딩 — 투어·가이드·명소 흔적 없음 ═══════════ */
  if (track === "companion") {
    return <CompanionLanding />;
  }

  /* ═══════════ 가이드 모드 맞춤 랜딩 ═══════════ */
  if (mode === "guide") {
    return (
      <main className="min-h-screen">
        <section className="px-3 pt-[4.25rem] md:px-6 md:pt-6">
          <div className="relative mx-auto max-w-6xl overflow-hidden rounded-[2rem] shadow-2xl shadow-emerald-950/25 ring-1 ring-black/5">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={GUIDE_HERO_IMG} alt={l.guideHeroTitle2} className="absolute inset-0 h-full w-full object-cover animate-hero-drift" />
            <div className="absolute inset-0 bg-gradient-to-t from-emerald-950/85 via-emerald-900/55 to-teal-900/40" />
            <SwitchViewButton onClick={resetMode} />

            <div className="relative flex min-h-[480px] flex-col items-center justify-center px-5 py-16 text-center md:min-h-[540px] md:px-10">
              {me && (
                <p className="mb-4 text-sm font-semibold text-white/90">
                  👋 {l.welcome.replace("{name}", me.fullName)}
                </p>
              )}

              <span className="inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/15 px-4 py-1.5 text-xs font-semibold tracking-wide text-white backdrop-blur-md">
                {t.guideHome.badge}
              </span>

              <h1 className="mt-6 max-w-3xl text-4xl font-extrabold leading-[1.08] tracking-tight text-white sm:text-5xl md:text-6xl">
                {l.guideHeroTitle1}{" "}
                <span className="bg-gradient-to-r from-emerald-300 to-teal-200 bg-clip-text text-transparent">
                  {l.guideHeroTitle2}
                </span>
              </h1>

              <p className="mx-auto mt-5 max-w-xl text-base leading-relaxed text-white/85 md:text-lg">
                {l.guideHeroSub}
              </p>

              <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
                <Link
                  href="/become-guide"
                  className="inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-emerald-400 to-teal-500 px-7 py-3.5 font-bold text-white shadow-lg transition-transform duration-200 hover:scale-[1.03] active:scale-100"
                >
                  {l.guideHeroCta} <ArrowRightIcon className="h-4 w-4" />
                </Link>
                {hasToken && (
                  <Link
                    href="/guide"
                    className="inline-flex items-center gap-2 rounded-full border border-white/40 bg-white/10 px-7 py-3.5 font-semibold text-white backdrop-blur-md transition-colors hover:border-white/70 hover:bg-white/20"
                  >
                    {l.guideHeroDash}
                  </Link>
                )}
              </div>
            </div>
          </div>
        </section>

        {/* 가이드 활동의 장점 */}
        <section className="px-4 py-14 md:px-6 md:py-16">
          <div className="mx-auto grid max-w-5xl gap-5 sm:grid-cols-3">
            {guidePerks.map((p) => (
              <div key={p.title} className="card p-6 text-left">
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 text-2xl shadow-md">
                  {p.icon}
                </div>
                <h3 className="mb-1.5 text-lg font-bold text-stone-900">{p.title}</h3>
                <p className="text-sm leading-relaxed text-stone-500">{p.desc}</p>
              </div>
            ))}
          </div>
        </section>

      </main>
    );
  }

  /* ═══════════ 여행자 모드 / 모드 미선택(제네릭) 랜딩 ═══════════ */
  const traveler = mode === "traveler";

  return (
    <main className="min-h-screen">
      {/* 모드 미선택 시: 상단에 선택 카드 노출 */}
      {mode === null && <ModeChooser onPick={pickMode} />}

      {/* ───────────── Hero — photo-forward, Airbnb style ───────────── */}
      <section className={`px-3 md:px-6 ${mode === null ? "pt-6" : "pt-[4.25rem] md:pt-6"}`}>
        <div className="relative mx-auto max-w-6xl overflow-hidden rounded-[2rem] shadow-2xl shadow-sky-950/25 ring-1 ring-black/5">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={HERO_IMG} alt={l.spotsTitle} className="absolute inset-0 h-full w-full object-cover animate-hero-drift" />
          <div className="absolute inset-0 bg-gradient-to-t from-slate-950/85 via-slate-900/45 to-sky-900/25" />
          {traveler && <SwitchViewButton onClick={resetMode} />}

          <div className="relative flex min-h-[540px] flex-col items-center justify-center px-5 py-16 text-center md:min-h-[600px] md:px-10">
            {me && (
              <p className="mb-4 text-sm font-semibold text-white/90">
                👋 {l.welcome.replace("{name}", me.fullName)}
              </p>
            )}

            <span className="inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/15 px-4 py-1.5 text-xs font-semibold tracking-wide text-white backdrop-blur-md">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse-dot" />
              {traveler ? t.travelerHome.badge : l.badge}
            </span>

            <h1 className="mt-6 max-w-3xl text-4xl font-extrabold leading-[1.08] tracking-tight text-white sm:text-5xl md:text-6xl">
              {l.h1a}{" "}
              <span className="bg-gradient-to-r from-sky-300 via-cyan-200 to-teal-300 bg-clip-text text-transparent">
                {l.h1b}
              </span>
            </h1>

            <p className="mx-auto mt-5 max-w-xl text-base leading-relaxed text-white/85 md:text-lg">
              {traveler ? t.travelerHome.sub : l.subtitle}
            </p>

            {traveler ? (
              /* 여행자 모드: Airbnb 스타일 3분할 검색 바 */
              <TravelerSearchBar />
            ) : (
              /* 제네릭: 기존 단일 검색 CTA */
              <Link
                href="/guides"
                className="group mt-9 flex w-full max-w-md items-center gap-3 rounded-full bg-white p-2 pl-6 text-left shadow-2xl transition-transform duration-200 hover:scale-[1.02] active:scale-100"
              >
                <span className="flex-1 truncate text-sm font-medium text-stone-500 md:text-base">
                  {l.heroSearchLabel}
                </span>
                <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-r from-sky-500 to-cyan-500 text-white shadow-md shadow-sky-500/30 transition-all group-hover:brightness-[1.06]">
                  <SearchIcon className="h-5 w-5" />
                </span>
              </Link>
            )}

            <div className="mt-5 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm font-semibold text-white/90">
              <Link href="/explore" className="inline-flex items-center gap-1.5 transition-colors hover:text-white">
                <CompassIcon className="h-4 w-4" /> {t.explore.title}
              </Link>
              <span className="hidden h-1 w-1 rounded-full bg-white/40 sm:block" />
              <Link href="/become-guide" className="inline-flex items-center gap-1.5 transition-colors hover:text-white">
                {l.cta2} <ArrowRightIcon className="h-4 w-4" />
              </Link>
            </div>

            {/* Trust stats — frosted pill */}
            <div className="mt-12 flex flex-wrap items-center justify-center gap-y-3 rounded-3xl glass px-3 py-4 md:rounded-full md:px-2">
              {stats.map((s, i) => (
                <div
                  key={s.label}
                  className={`flex flex-col items-center gap-0.5 px-6 md:px-9 ${i > 0 ? "sm:border-l sm:border-white/15" : ""}`}
                >
                  <span className="text-2xl font-extrabold tracking-tight text-white">{s.val}</span>
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-white/70">{s.label}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ───────────── Popular destinations — snap shelf ───────────── */}
      <section className="px-4 py-14 md:px-6 md:py-16">
        <div className="mx-auto max-w-6xl">
          <div className="mb-6 flex items-end justify-between gap-4">
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-sky-500">{l.exploreCities}</p>
              <h2 className="section-title mt-1 md:text-3xl">{l.spotsTitle}</h2>
              <p className="section-subtitle max-w-xl">{l.spotsSub}</p>
            </div>
            <Link href="/explore" className="btn-ghost hidden flex-shrink-0 md:inline-flex">
              {l.goLink}
            </Link>
          </div>

          <div className="shelf -mx-4 px-4 md:-mx-6 md:px-6">
            {SPOTS.map((s) => {
              const name = s.name[lang];
              const cityName = s.city[lang];
              return (
                <Link
                  key={s.slug}
                  href={`/spots/${s.slug}`}
                  className="group relative aspect-[3/4] w-56 flex-shrink-0 snap-start overflow-hidden rounded-3xl bg-stone-200 shadow-md ring-1 ring-black/5 transition-shadow duration-300 hover:shadow-xl hover:shadow-sky-950/20 md:w-64"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={s.img}
                    alt={name}
                    className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/10 to-transparent" />
                  <div className="absolute bottom-0 left-0 right-0 p-4 text-left text-white">
                    <p className="inline-flex items-center gap-1 rounded-full glass px-2.5 py-1 text-[11px] font-semibold">
                      <PinIcon className="h-3 w-3" /> {cityName}
                    </p>
                    <p className="mt-1.5 text-lg font-bold leading-snug drop-shadow">{name}</p>
                  </div>
                </Link>
              );
            })}
          </div>

          <div className="mt-4 text-center md:hidden">
            <Link href="/explore" className="btn-secondary">
              <CompassIcon className="h-4 w-4" /> {t.explore.title}
            </Link>
          </div>
        </div>
      </section>

      {/* ───────────── Feature highlights ───────────── */}
      <section className="border-y border-stone-100 bg-white px-4 py-14 md:px-6 md:py-16">
        <div className="mx-auto mb-10 max-w-4xl text-center">
          <span className="mx-auto mb-4 block h-1.5 w-12 rounded-full bg-gradient-to-r from-sky-400 to-cyan-400" />
          <h2 className="text-3xl font-extrabold tracking-tight text-stone-900">{l.whyTitle}</h2>
          <p className="mt-3 text-stone-500">{l.whySub}</p>
        </div>
        <div className="mx-auto grid max-w-5xl gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => {
            const inner = (
              <>
                <div className={`mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br ${f.grad} text-2xl shadow-md`}>
                  {f.icon}
                </div>
                <h3 className="mb-1.5 text-lg font-bold text-stone-900">{f.title}</h3>
                <p className="text-sm leading-relaxed text-stone-500">{f.desc}</p>
                {f.href && (
                  <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-sky-500">
                    {l.goLink}
                  </span>
                )}
              </>
            );
            return f.href ? (
              <Link key={f.title} href={f.href} className="card-hover p-6 text-left">{inner}</Link>
            ) : (
              <div key={f.title} className="card p-6 text-left">{inner}</div>
            );
          })}
        </div>
      </section>

      {/* ───────────── How it works ───────────── */}
      <section className="px-4 py-14 md:px-6 md:py-16">
        <div className="mx-auto mb-10 max-w-4xl text-center">
          <span className="mx-auto mb-4 block h-1.5 w-12 rounded-full bg-gradient-to-r from-sky-400 to-cyan-400" />
          <h2 className="text-3xl font-extrabold tracking-tight text-stone-900">{l.howTitle}</h2>
          <p className="mt-3 text-stone-500">{l.howSub}</p>
        </div>
        <div className="mx-auto grid max-w-3xl gap-8 sm:grid-cols-3">
          {steps.map((s, i) => (
            <div key={s.num} className="relative flex flex-col items-center text-center">
              {i < steps.length - 1 && (
                <div className="absolute left-[calc(50%+2rem)] right-0 top-5 hidden h-px border-t-2 border-dashed border-sky-200 sm:block" />
              )}
              <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-sky-500 to-cyan-500 text-sm font-bold text-white shadow-lg shadow-sky-300/50">
                {s.num}
              </div>
              <h3 className="mb-1.5 font-bold text-stone-900">{s.title}</h3>
              <p className="text-sm leading-relaxed text-stone-500">{s.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ───────────── Final CTA — photo card ───────────── */}
      <section className="px-4 pb-16 md:px-6">
        <div className="relative mx-auto max-w-5xl overflow-hidden rounded-[2rem] shadow-2xl shadow-sky-950/20 ring-1 ring-black/5">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={CTA_IMG} alt={l.ctaTitle} className="absolute inset-0 h-full w-full object-cover animate-hero-drift" />
          <div className="absolute inset-0 bg-gradient-to-r from-slate-950/90 via-slate-900/60 to-sky-950/30" />
          <div className="relative px-8 py-14 text-left md:px-14 md:py-16">
            <h2 className="max-w-md text-3xl font-extrabold tracking-tight text-white md:text-4xl">{l.ctaTitle}</h2>
            <p className="mt-3 mb-8 max-w-md text-white/80">{l.ctaSub}</p>
            <div className="flex flex-wrap gap-3">
              {me ? (
                <Link href="/guides" className="btn-primary-lg">
                  <SearchIcon className="h-4 w-4" /> {l.ctaBtn1}
                </Link>
              ) : (
                <>
                  <Link href="/signup" className="btn-primary-lg">{l.ctaBtn2}</Link>
                  <Link href="/guides" className="btn-secondary-lg border-white/40 bg-white/10 text-white hover:border-white/70 hover:bg-white/20">
                    {l.ctaBtn1}
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      </section>

    </main>
  );
}
