"use client";

// 공개 프로필 페이지 — 여행자/가이드 공용(트랙 무관).
// 비로그인도 조회 가능(공개 API). 팔로우 토글·게시글은 로그인 필요한 부분만 별도 처리.

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import PostCard, { type FeedPost } from "@/components/PostCard";
import FollowButton from "@/components/FollowButton";

type PublicProfile = {
  userId: number;
  handle: string;
  name: string;
  avatarUrl: string | null;
  nationality: string | null;
  mbti: string | null;
  interests: string[];
  isGuide: boolean;
  guideProfileId: number | null;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;
};

export default function PublicProfilePage() {
  const params = useParams();
  const handle = params.handle as string;
  const { t } = useLanguage();
  const l = t.publicProfile;

  const [profile, setProfile] = useState<PublicProfile | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [posts, setPosts] = useState<FeedPost[] | null>(null);
  const [meId, setMeId] = useState<number | null>(null);

  const load = useCallback(() => {
    setProfile(null);
    setNotFound(false);
    setPosts(null);
    setMeId(null);

    const authed = !!getToken();

    // 프로필 조회 실패(404 등)만 "찾을 수 없음"으로 취급 — 게시글 조회 실패는 빈 배열로 폴백.
    api<PublicProfile>(`/api/users/${handle}`, { auth: authed })
      .then(setProfile)
      .catch(() => setNotFound(true));

    api<FeedPost[]>(`/api/users/${handle}/posts`, { auth: authed })
      .then(setPosts)
      .catch(() => setPosts([]));

    if (authed) {
      api<{ id: number }>("/api/users/me", { auth: true })
        .then((me) => setMeId(me.id))
        .catch(() => setMeId(null)); // 만료된 토큰이어도 페이지 전체는 그대로 보여준다
    }
  }, [handle]);

  useEffect(() => { load(); }, [load]);

  function onLikeChange(postId: number, liked: boolean, likeCount: number) {
    setPosts((prev) => prev ? prev.map((p) => p.id === postId ? { ...p, isLiked: liked, likeCount } : p) : prev);
  }

  if (notFound) return (
    <main className="page px-4">
      <div className="container-sm">
        <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-center text-sm text-red-600">{l.notFound}</p>
      </div>
    </main>
  );

  if (!profile) return (
    <main className="page flex items-center justify-center px-4">
      <div className="h-10 w-10 animate-spin rounded-full border-4 border-sky-200 border-t-sky-500" />
    </main>
  );

  const isSelf = meId != null && meId === profile.userId;

  return (
    <main className="page px-4">
      <div className="container-sm">
        {/* Header */}
        <div className="card mb-5 flex flex-col items-center gap-4 p-6 text-center">
          {profile.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={profile.avatarUrl}
              alt={profile.name}
              className="h-20 w-20 flex-shrink-0 rounded-full object-cover shadow-md ring-2 ring-white"
            />
          ) : (
            <div className="flex h-20 w-20 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl font-bold text-white shadow-md ring-2 ring-white">
              {profile.name.slice(0, 1).toUpperCase()}
            </div>
          )}
          <div>
            <h1 className="text-xl font-extrabold tracking-tight text-stone-900">{profile.name}</h1>
            <p className="mt-0.5 text-sm font-medium text-stone-400">@{profile.handle}</p>
          </div>

          <div className="flex items-center gap-6 text-sm">
            <span className="flex flex-col items-center">
              <span className="font-extrabold text-stone-900">{profile.followerCount}</span>
              <span className="text-stone-400">{l.followers}</span>
            </span>
            <span className="flex flex-col items-center">
              <span className="font-extrabold text-stone-900">{profile.followingCount}</span>
              <span className="text-stone-400">{l.following}</span>
            </span>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-2">
            {isSelf ? (
              <Link href="/profile" className="btn-ghost text-sm">{l.editProfile}</Link>
            ) : (
              <FollowButton
                userId={profile.userId}
                initialFollowing={profile.isFollowing}
                onChange={(f) => setProfile((p) => p ? { ...p, isFollowing: f, followerCount: p.followerCount + (f ? 1 : -1) } : p)}
              />
            )}
            {profile.isGuide && profile.guideProfileId != null && (
              <Link
                href={`/guides/${profile.guideProfileId}`}
                className="rounded-full border border-stone-200 px-4 py-2 text-sm font-semibold text-stone-600 transition-colors hover:border-sky-300 hover:text-sky-500"
              >
                {l.viewGuideProfile}
              </Link>
            )}
          </div>
        </div>

        {/* Posts */}
        {posts === null ? (
          <div className="flex flex-col gap-4">
            {[1, 2, 3].map((i) => <div key={i} className="card h-48 animate-pulse" />)}
          </div>
        ) : posts.length === 0 ? (
          <div className="card p-8 text-center text-sm text-stone-400">{l.noPosts}</div>
        ) : (
          <div className="flex flex-col gap-4">
            {posts.map((p) => (
              <PostCard key={p.id} post={p} onLikeChange={onLikeChange} hideAuthorFollow />
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
