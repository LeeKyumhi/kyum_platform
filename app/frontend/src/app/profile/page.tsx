"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken, saveUserName } from "@/lib/api";
import { clearMode, getMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import { PinIcon } from "@/components/icons";
import EmailVerifiedBanner from "@/components/EmailVerifiedBanner";
import BlockedUsersSection from "@/components/BlockedUsersSection";
import SavedGrid from "@/components/SavedGrid";

type Me = {
  id: number;
  fullName: string;
  email: string;
  nickname: string | null;
  handle: string;
  nationality: string | null;
  gender: string | null;
  mbti: string | null;
  interests: string[];
  emailVerified: boolean;
};

type GuideProfile = {
  id: number;
  headline: string;
  region: string;
  hourlyRate: number;
  currency: string;
  avatarUrl: string | null;
  active: boolean;
  avgRating: number;
  reviewCount: number;
  followerCount: number;
};

export default function ProfilePage() {
  return (
    <Suspense fallback={null}>
      <ProfileContent />
    </Suspense>
  );
}

function ProfileContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useLanguage();
  const l = t.profilePage;

  const [me, setMe] = useState<Me | null>(null);
  const [guide, setGuide] = useState<GuideProfile | null>(null);
  const [postCount, setPostCount] = useState<number | null>(null);
  const [mode, setMode] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [togglingActive, setTogglingActive] = useState(false);
  // ?tab=saved로 진입하면 프로필→저장됨 탭이 바로 열림 (구 /saved 딥링크 호환)
  const [tab, setTab] = useState<"posts" | "saved">(
    searchParams.get("tab") === "saved" ? "saved" : "posts"
  );

  // 닉네임(@핸들) 인라인 편집
  const [editingNick, setEditingNick] = useState(false);
  const [nickInput, setNickInput] = useState("");
  const [nickError, setNickError] = useState("");
  const [nickSaving, setNickSaving] = useState(false);

  async function saveNickname() {
    setNickSaving(true); setNickError("");
    try {
      const updated = await api<Me>("/api/users/me/nickname", {
        method: "PATCH", body: { nickname: nickInput.trim() }, auth: true,
      });
      setMe(updated);
      setEditingNick(false);
    } catch (err) {
      setNickError(err instanceof Error ? err.message : t.common.error);
    } finally { setNickSaving(false); }
  }

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    const currentMode = getMode();
    setMode(currentMode);

    async function load() {
      try {
        const user = await api<Me>("/api/users/me", { auth: true });
        setMe(user);
        saveUserName(user.fullName);

        if (currentMode === "guide") {
          try {
            const gp = await api<GuideProfile>("/api/guide-profiles/me", { auth: true });
            setGuide(gp);
            const posts = await api<{ id: number }[]>(`/api/guides/${gp.id}/posts`);
            setPostCount(posts.length);
          } catch {
            // guide profile not set up yet
          }
        }
      } catch {
        router.replace("/login");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [router]);

  function onLogout() {
    clearToken();
    clearMode();
    router.push("/");
  }

  async function onSetActive(next: boolean) {
    if (!guide || guide.active === next || togglingActive) return;
    setTogglingActive(true);
    try {
      const updated = await api<GuideProfile>("/api/guide-profiles/me/active", {
        method: "PATCH", auth: true,
        body: { active: next },
      });
      // PATCH returns the lean profile (no computed stats) — merge so
      // followerCount/avgRating/reviewCount survive the toggle.
      setGuide((prev) => (prev ? { ...prev, ...updated } : updated));
    } catch {
      // keep previous state on failure
    } finally {
      setTogglingActive(false);
    }
  }

  const dashboardHref = mode === "guide" ? "/guide" : mode === "traveler" ? "/traveler" : "/select-mode";
  const editHref = mode === "guide" ? "/guide/manage" : mode === "traveler" ? "/traveler/profile" : "/select-mode";

  if (loading) {
    return (
      <main className="page flex items-center justify-center">
        <div className="text-sm text-stone-400">{t.common.loading}</div>
      </main>
    );
  }

  if (!me) return null;

  const initial = me.fullName.slice(0, 1).toUpperCase();
  const avatarUrl = guide?.avatarUrl ?? null;
  const isGuideMode = mode === "guide";
  const avatarGrad = isGuideMode ? "from-emerald-400 to-teal-500" : "from-sky-400 to-cyan-400";
  const bannerGrad = isGuideMode
    ? "from-emerald-200 via-teal-100 to-teal-50"
    : "from-sky-200 via-cyan-100 to-teal-100";

  return (
    <main className="page px-4">
      <div className="animate-fade-up container-sm">

        <EmailVerifiedBanner emailVerified={me.emailVerified} />

        {/* Profile hero — banner + overlapping avatar */}
        <div className="card mb-5 overflow-hidden">
          <div className={`h-24 bg-gradient-to-r ${bannerGrad}`} />
          <div className="flex flex-col items-center px-6 pb-6">
            {/* Avatar */}
            <div className="relative -mt-12 mb-4">
              {avatarUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={avatarUrl}
                  alt={me.fullName}
                  className="h-24 w-24 rounded-full object-cover shadow-lg ring-4 ring-white"
                />
              ) : (
                <div className={`flex h-24 w-24 items-center justify-center rounded-full bg-gradient-to-br ${avatarGrad} text-4xl font-bold text-white shadow-lg ring-4 ring-white`}>
                  {initial}
                </div>
              )}
              {mode && (
                <span className={`absolute -bottom-1 -right-1 rounded-full border-2 border-white px-2 py-0.5 text-xs font-semibold text-white shadow-sm ${
                  isGuideMode ? "bg-emerald-500" : "bg-sky-500"
                }`}>
                  {isGuideMode ? l.guideBadge : l.travelerBadge}
                </span>
              )}
            </div>

            {/* Name & email */}
            <h1 className="text-xl font-extrabold tracking-tight text-stone-900">{me.fullName}</h1>
            <p className="mt-0.5 text-sm text-stone-400">{me.email}</p>

            {/* @핸들 (닉네임) — 인라인 편집 */}
            {editingNick ? (
              <div className="mt-2 w-full max-w-xs">
                <div className="flex items-center gap-1.5">
                  <span className="text-sm font-semibold text-stone-400">@</span>
                  <input
                    value={nickInput}
                    onChange={(e) => setNickInput(e.target.value)}
                    placeholder={l.nicknamePlaceholder}
                    maxLength={20}
                    autoFocus
                    className="min-w-0 flex-1 rounded-lg border border-stone-200 px-2.5 py-1.5 text-sm outline-none focus:border-sky-300 focus:ring-2 focus:ring-sky-100"
                  />
                  <button
                    onClick={saveNickname}
                    disabled={nickSaving}
                    className="flex-shrink-0 rounded-lg bg-sky-500 px-3 py-1.5 text-xs font-bold text-white hover:bg-sky-600 disabled:opacity-60"
                  >
                    {l.nicknameSave}
                  </button>
                  <button
                    onClick={() => { setEditingNick(false); setNickError(""); }}
                    className="flex-shrink-0 rounded-lg px-2 py-1.5 text-xs font-medium text-stone-400 hover:text-stone-600"
                  >
                    {l.nicknameCancel}
                  </button>
                </div>
                {nickError && <p className="mt-1 text-xs text-red-500">{nickError}</p>}
                <p className="mt-1 text-[11px] text-stone-400">{l.nicknameHint}</p>
              </div>
            ) : (
              <button
                onClick={() => { setNickInput(me.nickname ?? ""); setEditingNick(true); }}
                className="mt-1 inline-flex items-center gap-1 text-sm font-semibold text-sky-600 hover:underline"
              >
                @{me.handle}
                <span className="text-xs text-stone-400">
                  {me.nickname ? "✏️" : `· ${l.nicknameSet}`}
                </span>
              </button>
            )}

            {/* Guide headline */}
            {guide && (
              <p className="mt-1 max-w-xs text-center text-sm leading-relaxed text-stone-500">{guide.headline}</p>
            )}

            {/* Identity chips — MBTI(violet) · gender · nationality */}
            {(me.mbti || me.gender || me.nationality) && (
              <div className="mt-3 flex flex-wrap items-center justify-center gap-1.5">
                {me.mbti && (
                  <span className="rounded-lg bg-violet-100 px-3 py-1 text-sm font-bold text-violet-700">
                    {me.mbti}
                  </span>
                )}
                {me.gender && (
                  <span className="badge-gray py-1">
                    {me.gender === "male" ? `♂ ${t.personality.genderMale}`
                      : me.gender === "female" ? `♀ ${t.personality.genderFemale}`
                      : t.personality.genderOther}
                  </span>
                )}
                {me.nationality && (
                  <span className="badge-gray py-1">🌍 {me.nationality}</span>
                )}
              </div>
            )}

            {/* Traveler interests brief */}
            {mode === "traveler" && me.interests && me.interests.length > 0 && (
              <div className="mt-3 flex flex-wrap justify-center gap-1.5">
                {me.interests.slice(0, 6).map((k) => (
                  <span key={k} className="badge-indigo py-1">
                    {t.interests[k as keyof typeof t.interests] ?? k}
                  </span>
                ))}
              </div>
            )}

            {/* Stats row — guide only */}
            {guide && (
              <div className="mt-5 flex w-full items-center justify-center gap-8 border-t border-stone-100 pt-4">
                <div className="flex flex-col items-center">
                  <span className="text-lg font-extrabold tracking-tight text-stone-900">{postCount ?? "—"}</span>
                  <span className="mt-0.5 text-xs text-stone-400">{l.postsCount}</span>
                </div>
                <div className="flex flex-col items-center">
                  <span className="text-lg font-extrabold tracking-tight text-stone-900">
                    {guide.reviewCount > 0 ? guide.avgRating.toFixed(1) : "—"}
                  </span>
                  <span className="mt-0.5 text-xs text-stone-400">{l.reviewsCount}</span>
                </div>
                <div className="flex flex-col items-center">
                  <span className="text-lg font-extrabold tracking-tight text-stone-900">{guide.followerCount}</span>
                  <span className="mt-0.5 text-xs text-stone-400">{l.followersCount}</span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 게시글 | 저장됨 탭 */}
        <div className="mb-5 inline-flex w-full rounded-full bg-stone-100 p-1">
          <button
            onClick={() => setTab("posts")}
            className={`flex-1 rounded-full py-2 text-sm font-bold transition-all ${
              tab === "posts" ? "bg-white text-stone-900 shadow-sm" : "text-stone-500 hover:text-stone-800"
            }`}
          >
            {l.postsTab}
          </button>
          <button
            onClick={() => setTab("saved")}
            className={`flex-1 rounded-full py-2 text-sm font-bold transition-all ${
              tab === "saved" ? "bg-white text-stone-900 shadow-sm" : "text-stone-500 hover:text-stone-800"
            }`}
          >
            {l.savedTab}
          </button>
        </div>

        {tab === "saved" && <SavedGrid />}

        {tab === "posts" && (
        <>
        {/* Guide region & rate */}
        {guide && (
          <div className="card mb-5 p-5">
            <div className="flex items-center gap-4">
              <div className="flex-1">
                <p className="input-label !mb-0.5">{l.regionLabel}</p>
                <p className="flex items-center gap-1 text-sm font-medium text-stone-800">
                  <PinIcon className="h-3.5 w-3.5 flex-shrink-0 text-stone-400" /> {guide.region}
                </p>
              </div>
              <div className="flex-1">
                <p className="input-label !mb-0.5">{l.rateLabel}</p>
                <p className="text-sm font-medium text-stone-800">
                  <span className="font-bold text-stone-900">{guide.hourlyRate.toLocaleString()}</span>
                  <span className="text-xs text-stone-400"> {guide.currency}/hr</span>
                </p>
              </div>
            </div>

            {/* 예약 상태 세그먼트 토글 */}
            <div className="mt-4 border-t border-stone-100 pt-4">
              <p className="input-label">{t.guideManage.activeLabel}</p>
              <div className="inline-flex rounded-full bg-stone-100 p-1">
                <button
                  onClick={() => onSetActive(true)}
                  disabled={togglingActive}
                  className={`flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-bold transition-all disabled:opacity-60 ${
                    guide.active ? "bg-emerald-500 text-white shadow-sm" : "text-stone-500 hover:text-stone-800"
                  }`}
                >
                  <span className={`h-2 w-2 rounded-full ${guide.active ? "animate-pulse-dot bg-white" : "bg-stone-300"}`} />
                  {t.guideManage.activeOn}
                </button>
                <button
                  onClick={() => onSetActive(false)}
                  disabled={togglingActive}
                  className={`flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-bold transition-all disabled:opacity-60 ${
                    !guide.active ? "bg-stone-700 text-white shadow-sm" : "text-stone-500 hover:text-stone-800"
                  }`}
                >
                  <span className={`h-2 w-2 rounded-full ${!guide.active ? "bg-white" : "bg-stone-300"}`} />
                  {t.guideManage.activeOff}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div className="mb-8 flex flex-col gap-3">
          <Link href={editHref} className="btn-primary w-full py-3 text-center text-sm font-semibold">
            {l.editBtn}
          </Link>
          <Link href={dashboardHref} className="btn-secondary w-full py-3 text-center text-sm font-semibold">
            {l.dashboardBtn}
          </Link>
          {mode && (
            <Link href="/select-mode" className="btn-ghost w-full py-2.5 text-center text-sm text-stone-500">
              {l.switchModeBtn}
            </Link>
          )}
        </div>

        <BlockedUsersSection />

        <div className="divider" />

        <button
          onClick={onLogout}
          className="w-full py-2 text-sm text-stone-400 transition-colors hover:text-red-500"
        >
          {l.logoutBtn}
        </button>
        </>
        )}
      </div>
    </main>
  );
}
