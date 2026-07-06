"use client";

import { useCallback, useEffect, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import PostCard, { type FeedPost } from "@/components/PostCard";
import PostComposeModal from "@/components/PostComposeModal";

/**
 * 커뮤니티 피드 — 가이드/여행자 홈에 섞여 있던 피드를 독립 페이지로 분리.
 * 비로그인도 볼 수 있고(공개 API), 글쓰기만 로그인 필요.
 */
export default function CommunityPage() {
  const { t } = useLanguage();
  const l = t.guidePosts;

  const [feed, setFeed] = useState<FeedPost[] | null>(null);
  const [loggedIn, setLoggedIn] = useState(false);
  const [composeOpen, setComposeOpen] = useState(false);

  const loadFeed = useCallback(() => {
    // auth를 붙여야 로그인 사용자의 isLiked가 반영된다 (토큰 없으면 헤더 생략됨)
    api<FeedPost[]>("/api/posts", { auth: true })
      .then(setFeed)
      .catch(() => setFeed([]));
  }, []);

  useEffect(() => {
    setLoggedIn(!!getToken());
    loadFeed();
  }, [loadFeed]);

  function onLikeChange(postId: number, liked: boolean, likeCount: number) {
    setFeed((prev) => prev ? prev.map((p) => p.id === postId ? { ...p, isLiked: liked, likeCount } : p) : prev);
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-5 flex items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-extrabold tracking-tight text-stone-900">👥 {t.nav.community}</h1>
            <p className="mt-1 text-sm text-stone-500">{l.communitySub}</p>
          </div>
          {loggedIn && (
            <button
              onClick={() => setComposeOpen(true)}
              className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl font-light text-white shadow-sm transition-transform hover:opacity-90 active:scale-95"
              aria-label={t.travelerHome.newPostBtn}
            >
              +
            </button>
          )}
        </div>

        {feed === null ? (
          <div className="flex flex-col gap-4">
            {[1, 2, 3].map((i) => <div key={i} className="card h-48 animate-pulse" />)}
          </div>
        ) : feed.length === 0 ? (
          <div className="card p-8 text-center text-sm text-stone-400">{l.noPosts}</div>
        ) : (
          <div className="flex flex-col gap-4">
            {feed.map((p) => (
              <PostCard key={p.id} post={p} onLikeChange={onLikeChange} />
            ))}
          </div>
        )}
      </div>

      <PostComposeModal
        open={composeOpen}
        onClose={() => setComposeOpen(false)}
        onCreated={() => { setComposeOpen(false); loadFeed(); }}
      />
    </main>
  );
}
