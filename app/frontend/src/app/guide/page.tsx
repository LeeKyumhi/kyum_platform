"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken } from "@/lib/api";
import { clearMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import { CameraIcon } from "@/components/icons";
import PostComposeModal from "@/components/PostComposeModal";

type Me = { id: number; fullName: string; email: string; handle: string };
type Profile = { id: number; headline: string; introduction?: string | null; avatarUrl?: string | null; serviceCategories: string[] };
type GuideStats = { followerCount: number; avgRating: number; reviewCount: number };
type MyPost = { id: number; content: string; imageUrl: string | null };

export default function GuideHome() {
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.guideHome;

  const [me, setMe] = useState<Me | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [hasProfile, setHasProfile] = useState<boolean | null>(null);
  const [stats, setStats] = useState<GuideStats | null>(null);
  const [posts, setPosts] = useState<MyPost[] | null>(null);
  const [composeOpen, setComposeOpen] = useState(false);

  const loadPosts = useCallback(() => {
    api<MyPost[]>("/api/users/me/posts", { auth: true })
      .then(setPosts)
      .catch(() => setPosts([]));
  }, []);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Me>("/api/users/me", { auth: true }).then(setMe).catch(() => router.replace("/login"));
    api<Profile>("/api/guide-profiles/me", { auth: true })
      .then((p) => {
        setProfile(p);
        setHasProfile(true);
        api<GuideStats>(`/api/guides/${p.id}`).then(setStats).catch(() => {});
      })
      .catch(() => setHasProfile(false));
    loadPosts();
  }, [router, loadPosts]);

  function onLogout() { clearToken(); clearMode(); router.push("/"); }

  const avatarInitial = me?.fullName ? me.fullName.slice(0, 1).toUpperCase() : "?";
  // 인스타그램식 @아이디 — 닉네임 우선(백엔드 handle), 없으면 이메일 로컬파트
  const myHandle = me?.handle ?? null;
  const bio = profile?.introduction?.trim() || profile?.headline || "";

  return (
    <main className="page px-4">
      <div className="container-sm">
        {hasProfile === false && (
          <>
            {/* Greeting (프로필이 없을 때만 — 있으면 아래 프로필 헤더가 대신함) */}
            <div className="mb-6">
              <h1 className="text-2xl font-extrabold tracking-tight text-stone-900">
                {l.greeting}{me ? `, ${me.fullName}` : ""}! 👋
              </h1>
              <p className="mt-1 text-sm text-stone-500">{l.sub}</p>
            </div>
            <div className="card mb-5 border-emerald-100 bg-gradient-to-br from-emerald-50/70 to-teal-50/40 p-8 text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 text-2xl shadow-md">
                🚀
              </div>
              <p className="mb-1 font-bold text-stone-900">{l.noProfileTitle}</p>
              <p className="mb-5 text-sm text-stone-500">{l.noProfileDesc}</p>
              <Link href="/become-guide" className="btn-primary">{l.noProfileBtn}</Link>
            </div>
          </>
        )}

        {hasProfile === null && (
          <div className="mb-5 flex flex-col gap-3">
            {[1, 2, 3].map((i) => <div key={i} className="card h-20 animate-pulse p-5" />)}
          </div>
        )}

        {hasProfile && (
          <>
            {profile && profile.serviceCategories.length === 0 && (
              <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
                <p className="text-sm font-semibold text-amber-800">⚠️ {l.declareBanner}</p>
                <Link href="/guide/manage" className="btn-primary px-4 py-1.5 text-sm">{l.declareCta}</Link>
              </div>
            )}

            {/* ── Instagram-style profile header ── */}
            <div className="card mb-5 p-5">
              <div className="flex items-center gap-5">
                {profile?.avatarUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={profile.avatarUrl}
                    alt={me?.fullName ?? ""}
                    className="h-20 w-20 flex-shrink-0 rounded-full object-cover"
                  />
                ) : (
                  <span className="flex h-20 w-20 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-emerald-400 to-teal-500 text-2xl font-bold text-white shadow-sm">
                    {avatarInitial}
                  </span>
                )}
                <div className="flex min-w-0 flex-1 items-center justify-around text-center">
                  <div>
                    <p className="text-lg font-extrabold tracking-tight text-stone-900">
                      {posts ? posts.length : "—"}
                    </p>
                    <p className="text-xs font-medium text-stone-400">{l.statPosts}</p>
                  </div>
                  <div>
                    <p className="text-lg font-extrabold tracking-tight text-stone-900">
                      {stats ? stats.followerCount : "—"}
                    </p>
                    <p className="text-xs font-medium text-stone-400">{l.statFollowers}</p>
                  </div>
                  <div>
                    <p className="text-lg font-extrabold tracking-tight text-stone-900">
                      {stats && stats.reviewCount > 0 ? stats.avgRating.toFixed(1) : "—"}
                    </p>
                    <p className="text-xs font-medium text-stone-400">{l.statRating}</p>
                  </div>
                </div>
              </div>
              <div className="mt-4">
                <p className="flex items-baseline gap-1.5 font-bold text-stone-900">
                  <span>{me?.fullName ?? ""}</span>
                  {myHandle && <span className="text-xs font-medium text-stone-400">@{myHandle}</span>}
                </p>
                {profile?.headline && (
                  <p className="mt-0.5 text-sm font-medium text-emerald-600">{profile.headline}</p>
                )}
                {bio && bio !== profile?.headline && (
                  <p className="mt-1 text-sm leading-relaxed text-stone-500 line-clamp-2">{bio}</p>
                )}
              </div>
              <Link href="/guide/manage" className="btn-secondary mt-4 w-full py-2 text-xs">
                {l.link1title}
              </Link>
            </div>

            {/* ── My posts grid ── */}
            <div className="card mb-5 p-5">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="flex items-center gap-2 font-bold text-stone-900">
                  <CameraIcon className="h-5 w-5 text-emerald-500" /> {l.postsTitle}
                </h2>
                <button
                  onClick={() => setComposeOpen(true)}
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-emerald-400 to-teal-500 text-xl font-light text-white shadow-sm transition-transform hover:opacity-90 active:scale-95"
                  aria-label={l.newPostBtn}
                >
                  +
                </button>
              </div>

              {posts === null ? (
                <div className="grid grid-cols-3 gap-1">
                  {[1, 2, 3].map((i) => (
                    <div key={i} className="aspect-square animate-pulse rounded-lg bg-stone-100" />
                  ))}
                </div>
              ) : posts.length === 0 ? (
                <div className="py-10 text-center">
                  <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-stone-100 text-stone-400">
                    <CameraIcon className="h-6 w-6" />
                  </div>
                  <p className="mx-auto max-w-xs text-sm leading-relaxed text-stone-500">{l.postsEmpty}</p>
                  <button onClick={() => setComposeOpen(true)} className="btn-primary mt-4">{l.newPostBtn}</button>
                </div>
              ) : (
                <div className="grid grid-cols-3 gap-1 overflow-hidden rounded-xl">
                  {posts.map((p) => (
                    <Link key={p.id} href="/guide/posts" className="block aspect-square overflow-hidden bg-stone-50">
                      {p.imageUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={p.imageUrl} alt="" className="h-full w-full object-cover" />
                      ) : (
                        <span className="flex h-full w-full items-center justify-center p-2">
                          <span className="line-clamp-4 text-center text-[11px] leading-snug text-stone-500">
                            {p.content}
                          </span>
                        </span>
                      )}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </>
        )}

        {/* ── 커뮤니티 배너 — 피드는 /community 로 분리 (가이드 모드는 에메랄드 계열) ── */}
        <Link
          href="/community"
          className="mb-6 block overflow-hidden rounded-2xl bg-gradient-to-r from-emerald-500 to-teal-500 p-5 text-white shadow-md transition-transform hover:scale-[1.01] active:scale-[0.99]"
        >
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-base font-extrabold tracking-tight">👥 {t.guidePosts.communityTitle}</p>
              <p className="mt-1 text-sm text-white/80">{t.guidePosts.communitySub}</p>
            </div>
            <span className="flex-shrink-0 rounded-full bg-white/20 px-3.5 py-1.5 text-xs font-bold backdrop-blur-sm">
              {t.guidePosts.communityCta} →
            </span>
          </div>
        </Link>

        <div className="divider" />
        <button onClick={onLogout} className="w-full py-2 text-sm text-stone-400 transition-colors hover:text-red-500">
          {l.logout}
        </button>
      </div>

      <PostComposeModal
        open={composeOpen}
        onClose={() => setComposeOpen(false)}
        onCreated={() => { setComposeOpen(false); loadPosts(); }}
      />
    </main>
  );
}
