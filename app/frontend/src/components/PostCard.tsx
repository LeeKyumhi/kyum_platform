"use client";

// 인스타그램식 게시글 카드 — 좋아요·댓글·조회수·@아이디.
// /guides 게시글 탭과 여행자/가이드 홈 피드에서 공유한다.
// 가이드 게시글은 프로필 링크, 여행자 게시글은 여행자 배지를 보여준다.

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { PinIcon, HeartIcon, ChatIcon, EyeIcon } from "@/components/icons";

export type FeedPost = {
  id: number;
  guideProfileId: number | null;
  authorUserId: number | null;
  isGuide: boolean;
  guideName: string;
  authorHandle: string | null;
  guideAvatarUrl: string | null;
  guideHeadline: string | null;
  guideRegion: string | null;
  guideLanguages: string[];
  content: string;
  imageUrl: string | null;
  viewCount: number;
  createdAt: string;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
};

type PostComment = { id: number; postId: number; userId: number; userName: string; content: string; createdAt: string };

// 세션 동안 조회수를 중복 카운트하지 않도록 이미 본 게시글 ID를 기억 (모듈 레벨)
const viewedPostIds = new Set<number>();

function AuthorAvatar({ src, name }: { src: string | null; name: string }) {
  if (src) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={src} alt={name} className="h-10 w-10 flex-shrink-0 rounded-full object-cover shadow-md ring-2 ring-white" />;
  }
  return (
    <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-sm font-bold text-white shadow-md ring-2 ring-white">
      {name.slice(0, 1).toUpperCase()}
    </div>
  );
}

export default function PostCard({ post, onLikeChange }: {
  post: FeedPost;
  onLikeChange: (id: number, liked: boolean, count: number) => void;
}) {
  const { t, lang } = useLanguage();
  const lp = t.guidePosts;
  const lc = t.chat;
  const router = useRouter();
  const [showComments, setShowComments] = useState(false);
  const [comments, setComments] = useState<PostComment[]>([]);
  const [commentsLoaded, setCommentsLoaded] = useState(false);
  const [commentText, setCommentText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const articleRef = useRef<HTMLElement | null>(null);
  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  // 게시글 번역: post.id → 번역문 캐시. 재토글 시 재요청하지 않는다.
  const [translation, setTranslation] = useState<string | null>(null);
  const [showTranslation, setShowTranslation] = useState(false);
  const [translating, setTranslating] = useState(false);

  async function toggleTranslate() {
    if (translation !== null) {
      setShowTranslation((v) => !v);
      return;
    }
    setTranslating(true);
    try {
      const res = await api<{ translated: string }>(`/api/posts/${post.id}/translate?lang=${lang}`);
      setTranslation(res.translated);
      setShowTranslation(true);
    } catch {
      setTranslation("");
    } finally {
      setTranslating(false);
    }
  }

  // 조회수: 카드가 화면에 들어오면(임프레션) 1회 카운트 (세션 내 중복 방지)
  useEffect(() => {
    const el = articleRef.current;
    if (!el || viewedPostIds.has(post.id)) return;
    const observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && !viewedPostIds.has(post.id)) {
          viewedPostIds.add(post.id);
          api(`/api/posts/${post.id}/view`, { method: "POST" }).catch(() => viewedPostIds.delete(post.id));
          observer.disconnect();
        }
      }
    }, { threshold: 0.5 });
    observer.observe(el);
    return () => observer.disconnect();
  }, [post.id]);

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
    <article ref={articleRef} className="card overflow-hidden">
      {/* Author header */}
      <div className="flex items-center gap-3 p-4">
        <AuthorAvatar src={post.guideAvatarUrl} name={post.guideName} />
        <div className="min-w-0 flex-1">
          <p className="flex items-baseline gap-1.5 text-sm font-bold leading-tight text-stone-900">
            <span className="truncate">{post.guideName}</span>
            {post.authorHandle && (
              <span className="flex-shrink-0 text-xs font-medium text-stone-400">@{post.authorHandle}</span>
            )}
          </p>
          {post.isGuide && post.guideRegion ? (
            <p className="mt-0.5 flex items-center gap-1 truncate text-xs text-stone-400">
              <PinIcon className="h-3 w-3 flex-shrink-0" /> {post.guideRegion}
              {post.guideLanguages.length > 0 && (
                <span className="truncate text-stone-300"> · {post.guideLanguages.join(", ")}</span>
              )}
            </p>
          ) : !post.isGuide ? (
            <p className="mt-0.5 text-xs text-stone-400">🧳 {t.profilePage.travelerBadge}</p>
          ) : null}
        </div>
        {post.isGuide && post.guideProfileId != null && (
          <Link
            href={`/guides/${post.guideProfileId}`}
            className="flex-shrink-0 rounded-full border border-stone-200 px-3 py-1.5 text-xs font-semibold text-stone-600 transition-colors hover:border-sky-300 hover:text-sky-500"
          >
            {t.guides.profileLink}
          </Link>
        )}
      </div>

      {/* Image — square aspect ratio like Instagram */}
      {post.imageUrl && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={post.imageUrl} alt="" className="aspect-square w-full object-cover" />
      )}

      {/* Actions */}
      <div className="flex items-center gap-4 px-4 pb-1 pt-3">
        <button
          onClick={toggleLike}
          className={`flex items-center gap-1.5 text-sm transition-colors ${
            post.isLiked ? "text-sky-500" : "text-stone-400 hover:text-sky-400"
          }`}
          aria-label={lp.likeBtn}
        >
          <HeartIcon className="h-6 w-6" filled={post.isLiked} />
          {post.likeCount > 0 && <span className="font-semibold text-stone-700">{post.likeCount}</span>}
        </button>
        <button
          onClick={toggleComments}
          className="flex items-center gap-1.5 text-sm text-stone-400 transition-colors hover:text-stone-700"
        >
          <ChatIcon className="h-6 w-6" />
          {post.commentCount > 0 && <span className="font-semibold text-stone-700">{post.commentCount}</span>}
        </button>
        {post.viewCount > 0 && (
          <span className="ml-auto flex items-center gap-1.5 text-sm text-stone-400">
            <EyeIcon className="h-5 w-5" />
            <span className="font-medium text-stone-500">{post.viewCount.toLocaleString()}</span>
          </span>
        )}
      </div>

      {/* Content */}
      <div className="px-4 pb-3">
        <p className="mt-1 whitespace-pre-line text-sm leading-relaxed text-stone-800">
          <span className="mr-1 font-bold">{post.guideName}</span>
          {post.content}
        </p>
        {showTranslation && translation && (
          <p className="mt-1.5 border-t border-stone-100 pt-1.5 text-[13px] leading-relaxed text-sky-600">
            🌐 {translation}
          </p>
        )}
        <button
          onClick={toggleTranslate}
          className="mt-1 text-[10px] font-medium text-stone-400 transition-colors hover:text-sky-500"
        >
          {translating
            ? lc.translating
            : showTranslation && translation
              ? lc.hideTranslation
              : lc.translateBtn}
        </button>
        <p className="mt-2 text-xs text-stone-400">
          {new Date(post.createdAt).toLocaleDateString(locale, { year: "numeric", month: "short", day: "numeric" })}
        </p>
      </div>

      {/* Comments section */}
      {showComments && (
        <div className="border-t border-stone-50 px-4 pb-4">
          {commentsLoaded && comments.length === 0 && (
            <p className="py-3 text-center text-xs text-stone-400">{lp.noComments}</p>
          )}
          {comments.length > 0 && (
            <ul className="flex flex-col gap-2 pt-3">
              {comments.map((c) => (
                <li key={c.id} className="text-sm">
                  <span className="mr-1 font-bold text-stone-900">{c.userName}</span>
                  <span className="text-stone-700">{c.content}</span>
                </li>
              ))}
            </ul>
          )}
          {/* Comment input */}
          <form onSubmit={submitComment} className="mt-3 flex items-center gap-2">
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder={lp.commentPlaceholder}
              className="flex-1 rounded-full border-none bg-stone-100 px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-sky-200"
              disabled={submitting}
            />
            <button
              type="submit"
              disabled={!commentText.trim() || submitting}
              className="flex-shrink-0 text-sm font-semibold text-sky-500 transition-colors hover:text-sky-700 disabled:opacity-40"
            >
              {lp.commentSubmit}
            </button>
          </form>
        </div>
      )}
    </article>
  );
}
