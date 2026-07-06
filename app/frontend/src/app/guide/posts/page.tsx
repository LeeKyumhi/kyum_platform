"use client";

// 내 게시글 관리 — 가이드/여행자 공용.
// (경로가 /guide/posts인 것은 과거 가이드 전용이던 시절의 흔적으로, 기존 링크 호환을 위해 유지)

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { getMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import { CameraIcon } from "@/components/icons";
import PostComposeModal from "@/components/PostComposeModal";

type MyPost = { id: number; content: string; imageUrl: string | null; createdAt: string };

export default function MyPostsPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const lp = t.guidePosts;
  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  const [posts, setPosts] = useState<MyPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [composing, setComposing] = useState(false);
  const [homeHref, setHomeHref] = useState("/traveler");

  const loadPosts = useCallback(async () => {
    const data = await api<MyPost[]>("/api/users/me/posts", { auth: true });
    setPosts(data);
  }, []);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    setHomeHref(getMode() === "guide" ? "/guide" : "/traveler");
    loadPosts()
      .catch(() => setError(t.common.error))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router, loadPosts]);

  async function onDeletePost(postId: number) {
    if (!confirm(lp.deleteConfirm)) return;
    try {
      await api(`/api/guide-profiles/me/posts/${postId}`, { method: "DELETE", auth: true });
      setPosts((prev) => prev.filter((p) => p.id !== postId));
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
  }

  return (
    <>
      <main className="page px-4 pb-28">
        <div className="container-sm">
          <div className="mb-6 flex items-center gap-3">
            <Link href={homeHref} className="btn-ghost text-sm">{lp.backHome}</Link>
            <h1 className="section-title">{lp.manageTitle}</h1>
          </div>

          {error && !composing && (
            <div className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>
          )}

          {loading && (
            <div className="mx-auto flex max-w-[468px] flex-col gap-4">
              {[1, 2].map((i) => (
                <div key={i} className="card animate-pulse overflow-hidden">
                  <div className="aspect-square w-full bg-stone-100" />
                  <div className="space-y-2 p-4">
                    <div className="h-3 w-full rounded bg-stone-100" />
                    <div className="h-3 w-3/4 rounded bg-stone-100" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {!loading && posts.length === 0 && (
            <div className="py-20 text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-stone-100 text-stone-400">
                <CameraIcon className="h-7 w-7" />
              </div>
              <p className="mx-auto max-w-xs text-sm leading-relaxed text-stone-500">{lp.manageEmpty}</p>
              <button onClick={() => setComposing(true)} className="btn-primary mt-5">{lp.newPost}</button>
            </div>
          )}

          {!loading && posts.length > 0 && (
            <div className="mx-auto flex max-w-[468px] flex-col gap-4">
              {posts.map((p) => (
                <article key={p.id} className="card overflow-hidden">
                  {p.imageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={p.imageUrl} alt="" className="aspect-square w-full object-cover" />
                  )}
                  <div className="p-4">
                    <p className="whitespace-pre-line text-sm leading-relaxed text-stone-800">{p.content}</p>
                    <div className="mt-3 flex items-center justify-between">
                      <span className="text-xs text-stone-400">
                        {new Date(p.createdAt).toLocaleDateString(locale, { year: "numeric", month: "short", day: "numeric" })}
                      </span>
                      <button
                        onClick={() => onDeletePost(p.id)}
                        className="btn-danger px-3 py-1.5 text-xs"
                      >
                        {lp.deleteBtn}
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      </main>

      {/* Floating + button */}
      <button
        onClick={() => setComposing(true)}
        className="fixed bottom-24 right-5 z-40 flex h-14 w-14 items-center justify-center rounded-full bg-sky-500 text-3xl font-light text-white shadow-lg shadow-sky-200 transition-all hover:bg-sky-600 active:scale-95 md:bottom-8 md:right-8"
        aria-label={lp.newPost}
      >
        +
      </button>

      {/* Compose modal (shared component) */}
      <PostComposeModal
        open={composing}
        onClose={() => setComposing(false)}
        onCreated={async () => {
          setComposing(false);
          await loadPosts().catch(() => setError(t.common.error));
        }}
      />
    </>
  );
}
