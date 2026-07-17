"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken } from "@/lib/api";
import { clearMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import { CameraIcon, HeartIcon, StarIcon } from "@/components/icons";
import PostComposeModal from "@/components/PostComposeModal";
import TrackEntryCards from "@/components/TrackEntryCards";

type Me = { id: number; fullName: string; email: string; handle: string; interests?: string[] };
type MyPost = { id: number; content: string; imageUrl: string | null };
type RecGuide = {
  id: number; guideName: string; avatarUrl: string | null; city: string | null; region: string;
  avgRating: number; reviewCount: number; matchScore: number | null;
};

export default function TravelerHome() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const l = t.travelerHome;

  const [me, setMe] = useState<Me | null>(null);
  const [followingCount, setFollowingCount] = useState<number | null>(null);
  const [posts, setPosts] = useState<MyPost[] | null>(null);
  const [recGuides, setRecGuides] = useState<RecGuide[]>([]);
  const [composeOpen, setComposeOpen] = useState(false);

  const loadPosts = useCallback(() => {
    api<MyPost[]>("/api/users/me/posts", { auth: true })
      .then(setPosts)
      .catch(() => setPosts([]));
  }, []);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Me>("/api/users/me", { auth: true }).then(setMe).catch(() => router.replace("/login"));
    api<unknown[]>("/api/users/me/following", { auth: true })
      .then((f) => setFollowingCount(f.length)).catch(() => {});
    // 오늘의 추천 — 궁합 점수(내 관심사·MBTI 기준) 상위 3명. 점수 없으면 섹션 자체를 숨긴다.
    api<RecGuide[]>(`/api/guides?lang=${lang}`, { auth: true })
      .then((gs) => setRecGuides(
        gs.filter((g) => g.matchScore != null)
          .sort((a, b) => (b.matchScore ?? 0) - (a.matchScore ?? 0))
          .slice(0, 3)
      ))
      .catch(() => {});
    loadPosts();
  }, [router, loadPosts, lang]);

  function onLogout() { clearToken(); clearMode(); router.push("/"); }

  const avatarInitial = me?.fullName ? me.fullName.slice(0, 1).toUpperCase() : "?";
  // 인스타그램식 @아이디 — 내 이메일 로컬파트
  const myHandle = me?.handle ?? null;
  // 관심사를 한 줄 바이오처럼 · 로 이어 붙인다 (없으면 표시하지 않음)
  const interestLine = (me?.interests ?? [])
    .map((k) => t.interests[k as keyof typeof t.interests])
    .filter(Boolean)
    .join(" · ");

  return (
    <main className="page px-4">
      <div className="container-sm">
        {/* ── Instagram-style profile header ── */}
        <div className="card mb-5 p-5">
          <div className="flex items-center gap-5">
            <span className="flex h-20 w-20 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl font-bold text-white shadow-sm">
              {avatarInitial}
            </span>
            <div className="flex min-w-0 flex-1 items-center justify-around text-center">
              <div>
                <p className="text-lg font-extrabold tracking-tight text-stone-900">
                  {posts ? posts.length : "—"}
                </p>
                <p className="text-xs font-medium text-stone-400">{l.statPosts}</p>
              </div>
              <div>
                <p className="text-lg font-extrabold tracking-tight text-stone-900">
                  {followingCount ?? "—"}
                </p>
                <p className="text-xs font-medium text-stone-400">{l.statFollowing}</p>
              </div>
            </div>
          </div>
          <div className="mt-4">
            <p className="flex items-baseline gap-1.5 font-bold text-stone-900">
              <span>{me?.fullName ?? ""}</span>
              {myHandle && <span className="text-xs font-medium text-stone-400">@{myHandle}</span>}
            </p>
            <p className="mt-0.5 text-sm font-medium text-sky-600">{l.tagline}</p>
            {interestLine && (
              <p className="mt-1 text-sm leading-relaxed text-stone-500 line-clamp-2">{interestLine}</p>
            )}
          </div>
          <Link href="/traveler/profile" className="btn-secondary mt-4 w-full py-2 text-xs">
            {t.travelerProfile.link}
          </Link>
        </div>

        {/* ── 오늘의 추천 가이드 — 궁합 상위 3명 ── */}
        {recGuides.length > 0 && (
          <div className="card mb-5 p-5">
            <div className="mb-1 flex items-center justify-between">
              <h2 className="flex items-center gap-2 font-bold text-stone-900">
                <HeartIcon className="h-5 w-5 text-rose-400" filled /> {l.recommendedTitle}
              </h2>
              <Link href="/guides" className="text-xs font-semibold text-sky-500 hover:underline">
                {l.recommendedMore}
              </Link>
            </div>
            <p className="mb-4 text-sm text-stone-500">{l.recommendedSub}</p>
            <div className="grid grid-cols-3 gap-2">
              {recGuides.map((g) => (
                <Link
                  key={g.id}
                  href={`/guides/${g.id}`}
                  className="flex flex-col items-center rounded-xl border border-stone-100 bg-white p-3 text-center transition-shadow hover:shadow-md"
                >
                  {g.avatarUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={g.avatarUrl} alt={g.guideName} className="h-14 w-14 rounded-full object-cover ring-2 ring-white shadow-md" />
                  ) : (
                    <span className="flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-lg font-bold text-white ring-2 ring-white shadow-md">
                      {g.guideName.slice(0, 1).toUpperCase()}
                    </span>
                  )}
                  <p className="mt-2 w-full truncate text-xs font-bold text-stone-900">{g.guideName}</p>
                  <p className="w-full truncate text-[11px] text-stone-400">{g.city ?? g.region}</p>
                  <span className="mt-1.5 inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-[11px] font-bold text-rose-500 ring-1 ring-rose-100">
                    <HeartIcon className="h-3 w-3" filled /> {g.matchScore}%
                  </span>
                  {g.reviewCount > 0 && (
                    <span className="mt-1 flex items-center gap-0.5 text-[11px] text-stone-400">
                      <StarIcon className="h-3 w-3 text-amber-400" /> {g.avgRating.toFixed(1)}
                    </span>
                  )}
                </Link>
              ))}
            </div>
          </div>
        )}

        {/* ── My posts grid ── */}
        <div className="card mb-5 p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="flex items-center gap-2 font-bold text-stone-900">
              <CameraIcon className="h-5 w-5 text-sky-500" /> {l.postsTitle}
            </h2>
            <button
              onClick={() => setComposeOpen(true)}
              className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-xl font-light text-white shadow-sm transition-transform hover:opacity-90 active:scale-95"
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

        <section className="mb-8">
          <TrackEntryCards />
        </section>

        {/* ── 커뮤니티 배너 — 피드는 /community 로 분리 ── */}
        <Link
          href="/community"
          className="mb-6 block overflow-hidden rounded-2xl bg-gradient-to-r from-sky-500 to-cyan-500 p-5 text-white shadow-md transition-transform hover:scale-[1.01] active:scale-[0.99]"
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
