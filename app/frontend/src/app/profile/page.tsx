"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken, saveUserName } from "@/lib/api";
import { clearMode, getMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";

type Me = {
  id: number;
  fullName: string;
  email: string;
  nationality: string | null;
  mbti: string | null;
  interests: string[];
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
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.profilePage;

  const [me, setMe] = useState<Me | null>(null);
  const [guide, setGuide] = useState<GuideProfile | null>(null);
  const [postCount, setPostCount] = useState<number | null>(null);
  const [mode, setMode] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [togglingActive, setTogglingActive] = useState(false);

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
        <div className="text-gray-400 text-sm">{t.common.loading}</div>
      </main>
    );
  }

  if (!me) return null;

  const initial = me.fullName.slice(0, 1).toUpperCase();
  const avatarUrl = guide?.avatarUrl ?? null;

  return (
    <main className="page px-4">
      <div className="container-sm">

        {/* Profile hero */}
        <div className="flex flex-col items-center pt-4 pb-6">
          {/* Avatar */}
          <div className="relative mb-4">
            {avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={avatarUrl}
                alt={me.fullName}
                className="w-24 h-24 rounded-full object-cover ring-4 ring-white shadow-lg"
              />
            ) : (
              <div className="w-24 h-24 rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center text-white text-4xl font-bold shadow-lg ring-4 ring-white">
                {initial}
              </div>
            )}
            {mode && (
              <span className={`absolute -bottom-1 -right-1 text-xs font-semibold px-2 py-0.5 rounded-full border-2 border-white shadow-sm ${
                mode === "guide"
                  ? "bg-indigo-600 text-white"
                  : "bg-emerald-500 text-white"
              }`}>
                {mode === "guide" ? l.guideBadge : l.travelerBadge}
              </span>
            )}
          </div>

          {/* Name & email */}
          <h1 className="text-xl font-bold text-gray-900">{me.fullName}</h1>
          <p className="text-sm text-gray-400 mt-0.5">{me.email}</p>

          {/* Guide headline */}
          {guide && (
            <p className="text-sm text-gray-500 mt-1 text-center max-w-xs leading-relaxed">{guide.headline}</p>
          )}

          {/* Stats row — guide only */}
          {guide && (
            <div className="flex items-center gap-6 mt-5 pt-4 border-t border-gray-100 w-full justify-center">
              <div className="flex flex-col items-center">
                <span className="text-lg font-bold text-gray-900">{postCount ?? "—"}</span>
                <span className="text-xs text-gray-400 mt-0.5">{l.postsCount}</span>
              </div>
              <div className="flex flex-col items-center">
                <span className="text-lg font-bold text-gray-900">
                  {guide.reviewCount > 0 ? guide.avgRating.toFixed(1) : "—"}
                </span>
                <span className="text-xs text-gray-400 mt-0.5">{l.reviewsCount}</span>
              </div>
              <div className="flex flex-col items-center">
                <span className="text-lg font-bold text-gray-900">{guide.followerCount}</span>
                <span className="text-xs text-gray-400 mt-0.5">{l.followersCount}</span>
              </div>
            </div>
          )}

          {/* Traveler interests brief */}
          {mode === "traveler" && me.interests && me.interests.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-4 justify-center">
              {me.interests.slice(0, 6).map((k) => (
                <span key={k} className="rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100 px-2.5 py-0.5 text-xs font-medium">
                  {t.interests[k as keyof typeof t.interests] ?? k}
                </span>
              ))}
            </div>
          )}

          {/* MBTI badge */}
          {me.mbti && (
            <span className="mt-3 rounded-lg bg-violet-100 text-violet-700 px-3 py-1 text-sm font-bold">
              {me.mbti}
            </span>
          )}
        </div>

        {/* Guide region & rate */}
        {guide && (
          <div className="card p-4 mb-4">
            <div className="flex items-center gap-4">
              <div className="flex-1">
                <p className="text-xs text-gray-400">지역</p>
                <p className="font-medium text-gray-800 text-sm mt-0.5">📍 {guide.region}</p>
              </div>
              <div className="flex-1">
                <p className="text-xs text-gray-400">시급</p>
                <p className="font-medium text-gray-800 text-sm mt-0.5">
                  {guide.hourlyRate.toLocaleString()} {guide.currency}/hr
                </p>
              </div>
            </div>

            {/* 예약 상태 세그먼트 토글 */}
            <div className="mt-4 pt-4 border-t border-gray-100">
              <p className="text-xs text-gray-400 mb-2">{t.guideManage.activeLabel}</p>
              <div className="inline-flex rounded-full bg-gray-100 p-1">
                <button
                  onClick={() => onSetActive(true)}
                  disabled={togglingActive}
                  className={`flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-semibold transition-all disabled:opacity-60 ${
                    guide.active ? "bg-emerald-500 text-white shadow-sm" : "text-gray-500 hover:text-gray-700"
                  }`}
                >
                  <span className={`h-2 w-2 rounded-full ${guide.active ? "bg-white" : "bg-gray-300"}`} />
                  {t.guideManage.activeOn}
                </button>
                <button
                  onClick={() => onSetActive(false)}
                  disabled={togglingActive}
                  className={`flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-semibold transition-all disabled:opacity-60 ${
                    !guide.active ? "bg-gray-700 text-white shadow-sm" : "text-gray-500 hover:text-gray-700"
                  }`}
                >
                  <span className={`h-2 w-2 rounded-full ${!guide.active ? "bg-white" : "bg-gray-300"}`} />
                  {t.guideManage.activeOff}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div className="flex flex-col gap-3 mb-8">
          <Link href={editHref} className="btn-primary w-full py-3 text-center text-sm font-semibold">
            {l.editBtn}
          </Link>
          <Link href={dashboardHref} className="btn-secondary w-full py-3 text-center text-sm font-semibold">
            {l.dashboardBtn}
          </Link>
          {mode && (
            <Link href="/select-mode" className="btn-ghost w-full py-2.5 text-center text-sm text-gray-500">
              모드 전환
            </Link>
          )}
        </div>

        <div className="divider" />

        <button
          onClick={onLogout}
          className="w-full text-sm text-gray-400 hover:text-red-500 transition-colors py-2"
        >
          {l.logoutBtn}
        </button>
      </div>
    </main>
  );
}
