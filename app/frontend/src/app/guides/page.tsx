"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type Language = { language: string; level: string };
type Guide = {
  id: number; guideName: string; headline: string; hourlyRate: number; currency: string;
  region: string; avatarUrl: string | null; avgRating: number; reviewCount: number;
  followerCount: number; mbti: string | null; interests: string[]; languages: Language[];
};
type FeedPost = {
  id: number; guideProfileId: number; guideName: string; guideAvatarUrl: string | null;
  guideHeadline: string; guideRegion: string; content: string; imageUrl: string | null;
  createdAt: string; likeCount: number; commentCount: number; isLiked: boolean;
};
type PostComment = { id: number; postId: number; userId: number; userName: string; content: string; createdAt: string };

function GuideAvatar({ src, name, size = "md" }: { src: string | null; name: string; size?: "sm" | "md" }) {
  const cls = size === "sm"
    ? "w-10 h-10 rounded-full text-sm"
    : "w-12 h-12 rounded-full";
  if (src) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={src} alt={name} className={`${cls} object-cover ring-2 ring-white shadow-sm flex-shrink-0`} />;
  }
  return (
    <div className={`${cls} bg-gradient-to-br from-indigo-400 to-violet-500 flex items-center justify-center text-white font-bold ring-2 ring-white shadow-sm flex-shrink-0`}>
      {name.slice(0, 1).toUpperCase()}
    </div>
  );
}

function TabBar({ active, tabs, onChange }: {
  active: string;
  tabs: { key: string; label: string }[];
  onChange: (k: string) => void;
}) {
  return (
    <div className="flex border-b border-gray-100 mb-6">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          onClick={() => onChange(tab.key)}
          className={`pb-3 px-5 text-sm font-medium transition-colors border-b-2 -mb-px ${
            active === tab.key
              ? "border-indigo-600 text-indigo-600"
              : "border-transparent text-gray-400 hover:text-gray-700"
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

function PostCard({ post, onLikeChange }: { post: FeedPost; onLikeChange: (id: number, liked: boolean, count: number) => void }) {
  const { t, lang } = useLanguage();
  const lp = t.guidePosts;
  const router = useRouter();
  const [showComments, setShowComments] = useState(false);
  const [comments, setComments] = useState<PostComment[]>([]);
  const [commentsLoaded, setCommentsLoaded] = useState(false);
  const [commentText, setCommentText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  async function toggleLike() {
    if (!getToken()) { router.push("/login"); return; }
    try {
      if (post.isLiked) {
        const res = await api<{ likeCount: number }>(`/api/posts/${post.id}/like`, { method: "DELETE", auth: true });
        onLikeChange(post.id, false, res.likeCount);
      } else {
        const res = await api<{ likeCount: number }>(`/api/posts/${post.id}/like`, { method: "POST", auth: true });
        onLikeChange(post.id, true, res.likeCount);
      }
    } catch { /* ignore */ }
  }

  async function loadComments() {
    if (commentsLoaded) return;
    try {
      const data = await api<PostComment[]>(`/api/posts/${post.id}/comments`);
      setComments(data);
      setCommentsLoaded(true);
    } catch { /* ignore */ }
  }

  async function toggleComments() {
    if (!showComments) await loadComments();
    setShowComments(!showComments);
  }

  async function submitComment(e: React.FormEvent) {
    e.preventDefault();
    if (!getToken()) { router.push("/login"); return; }
    if (!commentText.trim()) return;
    setSubmitting(true);
    try {
      const newComment = await api<PostComment>(`/api/posts/${post.id}/comments`, {
        method: "POST", auth: true, body: { content: commentText.trim() },
      });
      setComments((prev) => [...prev, newComment]);
      setCommentText("");
    } catch { /* ignore */ }
    finally { setSubmitting(false); }
  }

  return (
    <article className="bg-white border border-gray-100 rounded-2xl overflow-hidden shadow-sm">
      {/* Guide header */}
      <div className="flex items-center gap-3 p-4">
        <GuideAvatar src={post.guideAvatarUrl} name={post.guideName} size="sm" />
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-gray-900 text-sm leading-tight">{post.guideName}</p>
          <p className="text-xs text-gray-400 mt-0.5 truncate">📍 {post.guideRegion}</p>
        </div>
        <Link
          href={`/guides/${post.guideProfileId}`}
          className="text-xs text-indigo-600 font-medium hover:underline flex-shrink-0"
        >
          {t.guides.profileLink} →
        </Link>
      </div>

      {/* Image — square aspect ratio like Instagram */}
      {post.imageUrl && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={post.imageUrl} alt="" className="w-full aspect-square object-cover" />
      )}

      {/* Actions */}
      <div className="px-4 pt-3 pb-1 flex items-center gap-4">
        <button
          onClick={toggleLike}
          className={`flex items-center gap-1.5 text-sm transition-colors ${
            post.isLiked ? "text-red-500" : "text-gray-400 hover:text-red-400"
          }`}
          aria-label={lp.likeBtn}
        >
          <span className="text-xl leading-none">{post.isLiked ? "❤️" : "🤍"}</span>
          {post.likeCount > 0 && <span className="font-medium text-gray-700">{post.likeCount}</span>}
        </button>
        <button
          onClick={toggleComments}
          className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-indigo-500 transition-colors"
        >
          <span className="text-xl leading-none">💬</span>
          {post.commentCount > 0 && <span className="font-medium text-gray-700">{post.commentCount}</span>}
        </button>
      </div>

      {/* Content */}
      <div className="px-4 pb-3">
        <p className="text-sm text-gray-800 whitespace-pre-line leading-relaxed mt-1">
          <span className="font-semibold mr-1">{post.guideName}</span>
          {post.content}
        </p>
        <p className="mt-2 text-xs text-gray-400">
          {new Date(post.createdAt).toLocaleDateString(locale, { year: "numeric", month: "short", day: "numeric" })}
        </p>
      </div>

      {/* Comments section */}
      {showComments && (
        <div className="border-t border-gray-50 px-4 pb-4">
          {commentsLoaded && comments.length === 0 && (
            <p className="text-xs text-gray-400 py-3 text-center">{lp.noComments}</p>
          )}
          {comments.length > 0 && (
            <ul className="pt-3 flex flex-col gap-2">
              {comments.map((c) => (
                <li key={c.id} className="text-sm">
                  <span className="font-semibold text-gray-900 mr-1">{c.userName}</span>
                  <span className="text-gray-700">{c.content}</span>
                </li>
              ))}
            </ul>
          )}
          {/* Comment input */}
          <form onSubmit={submitComment} className="flex items-center gap-2 mt-3">
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder={lp.commentPlaceholder}
              className="flex-1 text-sm border-none bg-gray-50 rounded-full px-4 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-200"
              disabled={submitting}
            />
            <button
              type="submit"
              disabled={!commentText.trim() || submitting}
              className="text-sm text-indigo-600 font-semibold disabled:opacity-40 hover:text-indigo-800 transition-colors flex-shrink-0"
            >
              {lp.commentSubmit}
            </button>
          </form>
        </div>
      )}
    </article>
  );
}

export default function GuidesPage() {
  const { t, lang } = useLanguage();
  const l = t.guides;

  const [tab, setTab] = useState<"guides" | "posts">("guides");

  // Guides tab state
  const [guides, setGuides]       = useState<Guide[]>([]);
  const [inputRegion, setInputRegion] = useState("");
  const [region, setRegion]       = useState("");
  const [guidesLoading, setGuidesLoading] = useState(true);
  const [guidesError, setGuidesError]     = useState("");

  // Posts tab state
  const [posts, setPosts]         = useState<FeedPost[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);
  const [postsLoaded, setPostsLoaded]   = useState(false);

  async function loadGuides(regionFilter = "") {
    setGuidesLoading(true); setGuidesError("");
    try {
      const q = regionFilter ? `?region=${encodeURIComponent(regionFilter)}` : "";
      setGuides(await api<Guide[]>(`/api/guides${q}`));
    } catch (err) {
      setGuidesError(err instanceof Error ? err.message : t.common.error);
    } finally { setGuidesLoading(false); }
  }

  async function loadPosts() {
    if (postsLoaded) return;
    setPostsLoading(true);
    try {
      setPosts(await api<FeedPost[]>("/api/posts"));
      setPostsLoaded(true);
    } catch { /* silent */ }
    finally { setPostsLoading(false); }
  }

  useEffect(() => { loadGuides(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function onTabChange(k: string) {
    setTab(k as "guides" | "posts");
    if (k === "posts") loadPosts();
  }

  function onSearch(e: React.FormEvent) {
    e.preventDefault(); setRegion(inputRegion); loadGuides(inputRegion);
  }
  function clearSearch() { setInputRegion(""); setRegion(""); loadGuides(""); }

  function handleLikeChange(postId: number, liked: boolean, likeCount: number) {
    setPosts((prev) => prev.map((p) => p.id === postId ? { ...p, isLiked: liked, likeCount } : p));
  }

  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  return (
    <main className="page px-4">
      <div className="container-lg">

        <TabBar
          active={tab}
          tabs={[
            { key: "guides", label: l.tabGuides },
            { key: "posts",  label: l.tabPosts  },
          ]}
          onChange={onTabChange}
        />

        {/* ── 가이드 탭 ── */}
        {tab === "guides" && (
          <>
            <form onSubmit={onSearch} className="mb-6 flex gap-2">
              <div className="relative flex-1">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400 text-sm">📍</span>
                <input placeholder={l.searchPlaceholder} value={inputRegion}
                  onChange={(e) => setInputRegion(e.target.value)} className="input pl-9" />
                {inputRegion && (
                  <button type="button" onClick={clearSearch}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 text-sm">✕</button>
                )}
              </div>
              <button type="submit" className="btn-primary px-6">{l.searchBtn}</button>
            </form>

            {region && (
              <div className="mb-5 flex items-center gap-2">
                <span className="text-sm text-gray-500">{l.filterLabel}</span>
                <span className="badge-indigo">{region}
                  <button onClick={clearSearch} className="ml-1 opacity-60 hover:opacity-100">✕</button>
                </span>
              </div>
            )}

            {guidesLoading && (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {[...Array(6)].map((_, i) => (
                  <div key={i} className="card p-5 animate-pulse">
                    <div className="flex items-center gap-3 mb-3">
                      <div className="w-12 h-12 rounded-full bg-gray-100" />
                      <div className="flex-1">
                        <div className="h-4 bg-gray-100 rounded w-24 mb-1.5" />
                        <div className="h-3 bg-gray-100 rounded w-16" />
                      </div>
                    </div>
                    <div className="h-3 bg-gray-100 rounded w-full mb-1.5" />
                    <div className="h-3 bg-gray-100 rounded w-3/4" />
                  </div>
                ))}
              </div>
            )}
            {guidesError && <p className="text-red-600 text-sm">{guidesError}</p>}
            {!guidesLoading && !guidesError && guides.length === 0 && (
              <div className="text-center py-16">
                <div className="text-4xl mb-3">🔍</div>
                <p className="text-gray-500">{l.empty}</p>
                {region && <button onClick={clearSearch} className="mt-3 text-sm text-indigo-600 hover:underline">{l.showAll}</button>}
              </div>
            )}
            {!guidesLoading && (
              <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {guides.map((g) => (
                  <Link key={g.id} href={`/guides/${g.id}`} className="card-hover p-5 block">
                    <div className="flex items-start gap-3 mb-3">
                      <GuideAvatar src={g.avatarUrl} name={g.guideName} />
                      <div className="flex-1 min-w-0">
                        <h2 className="font-bold text-gray-900 truncate">{g.guideName}</h2>
                        <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">📍 {g.region}</p>
                      </div>
                      <span className="text-indigo-600 font-semibold text-sm whitespace-nowrap">
                        {g.hourlyRate.toLocaleString()}
                        <span className="text-xs text-gray-400 font-normal">/{g.currency}{l.perHour}</span>
                      </span>
                    </div>
                    <p className="text-sm text-gray-600 mb-3 line-clamp-2 leading-relaxed">{g.headline}</p>
                    {/* MBTI + top interests */}
                    {(g.mbti || g.interests.length > 0) && (
                      <div className="flex flex-wrap gap-1 mb-3">
                        {g.mbti && (
                          <span className="rounded-md bg-violet-100 text-violet-700 px-2 py-0.5 text-xs font-bold">{g.mbti}</span>
                        )}
                        {g.interests.slice(0, 3).map((k) => (
                          <span key={k} className="rounded-full bg-gray-100 text-gray-500 px-2 py-0.5 text-xs">
                            {t.interests[k as keyof typeof t.interests] ?? k}
                          </span>
                        ))}
                      </div>
                    )}
                    <div className="flex items-center justify-between">
                      <div className="flex flex-wrap gap-1">
                        {g.languages.slice(0, 2).map((lv, i) => (
                          <span key={i} className="badge-indigo text-xs">
                            {lv.language}
                            <span className="opacity-60"> · {t.level[lv.level as keyof typeof t.level] ?? lv.level}</span>
                          </span>
                        ))}
                        {g.languages.length > 2 && <span className="badge-gray text-xs">+{g.languages.length - 2}</span>}
                      </div>
                      <div className="flex items-center gap-2">
                        {g.followerCount > 0 && (
                          <span className="text-xs text-gray-400">👥 {g.followerCount}</span>
                        )}
                        {g.reviewCount > 0 && (
                          <span className="flex items-center gap-0.5">
                            <span className="text-amber-400 text-sm">★</span>
                            <span className="text-sm font-semibold text-gray-800">{g.avgRating.toFixed(1)}</span>
                          </span>
                        )}
                      </div>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </>
        )}

        {/* ── 게시글 탭 ── */}
        {tab === "posts" && (
          <>
            {postsLoading && (
              <div className="max-w-[468px] mx-auto flex flex-col gap-4">
                {[...Array(3)].map((_, i) => (
                  <div key={i} className="bg-white border border-gray-100 rounded-2xl overflow-hidden animate-pulse">
                    <div className="flex items-center gap-3 p-4">
                      <div className="w-10 h-10 rounded-full bg-gray-100 flex-shrink-0" />
                      <div className="flex-1">
                        <div className="h-3.5 bg-gray-100 rounded w-28 mb-1.5" />
                        <div className="h-3 bg-gray-100 rounded w-20" />
                      </div>
                    </div>
                    <div className="w-full aspect-square bg-gray-100" />
                    <div className="p-4">
                      <div className="h-3 bg-gray-100 rounded w-full mb-1.5" />
                      <div className="h-3 bg-gray-100 rounded w-4/5" />
                    </div>
                  </div>
                ))}
              </div>
            )}

            {!postsLoading && posts.length === 0 && (
              <div className="text-center py-20">
                <div className="text-5xl mb-4">📝</div>
                <p className="text-gray-500 text-sm max-w-xs mx-auto leading-relaxed">{l.feedEmpty}</p>
              </div>
            )}

            {!postsLoading && posts.length > 0 && (
              <div className="max-w-[468px] mx-auto flex flex-col gap-1 sm:gap-4">
                {posts.map((p) => (
                  <PostCard key={p.id} post={p} onLikeChange={handleLikeChange} />
                ))}
              </div>
            )}
          </>
        )}

      </div>
    </main>
  );
}
