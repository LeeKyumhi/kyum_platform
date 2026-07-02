"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, apiUpload, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type Profile = { id: number };
type GuidePost = { id: number; content: string; imageUrl: string | null; createdAt: string };

export default function GuidePostsPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const lp = t.guidePosts;
  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  const [profile, setProfile] = useState<Profile | null>(null);
  const [posts, setPosts] = useState<GuidePost[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [composing, setComposing] = useState(false);
  const [postContent, setPostContent] = useState("");
  const [postImage, setPostImage] = useState<File | null>(null);
  const [postImagePreview, setPostImagePreview] = useState<string | null>(null);
  const [postSubmitting, setPostSubmitting] = useState(false);

  const loadPosts = useCallback(async (profileId: number) => {
    const data = await api<GuidePost[]>(`/api/guides/${profileId}/posts`);
    setPosts(data);
  }, []);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Profile>("/api/guide-profiles/me", { auth: true })
      .then(async (p) => {
        setProfile(p);
        await loadPosts(p.id);
      })
      .catch(() => router.replace("/guide"))
      .finally(() => setLoading(false));
  }, [router, loadPosts]);

  function openCompose() {
    setPostContent("");
    setPostImage(null);
    setPostImagePreview(null);
    setError("");
    setComposing(true);
  }

  function onPostImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setPostImage(file);
    if (file) setPostImagePreview(URL.createObjectURL(file));
    else setPostImagePreview(null);
  }

  async function onPostSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!postContent.trim() || !profile) return;
    setError(""); setPostSubmitting(true);
    try {
      const fd = new FormData();
      fd.append("content", postContent.trim());
      if (postImage) fd.append("image", postImage);
      await apiUpload<GuidePost>("/api/guide-profiles/me/posts", fd, { auth: true });
      setComposing(false);
      await loadPosts(profile.id);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setPostSubmitting(false); }
  }

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
          <div className="flex items-center gap-3 mb-6">
            <Link href="/guide" className="btn-ghost text-sm">{lp.backHome}</Link>
            <h1 className="section-title">{lp.manageTitle}</h1>
          </div>

          {error && !composing && (
            <div className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 border border-red-100">{error}</div>
          )}

          {loading && (
            <div className="max-w-[468px] mx-auto flex flex-col gap-4">
              {[1, 2].map((i) => (
                <div key={i} className="rounded-2xl overflow-hidden animate-pulse bg-white border border-gray-100">
                  <div className="w-full aspect-square bg-gray-100" />
                  <div className="p-4 space-y-2">
                    <div className="h-3 bg-gray-100 rounded w-full" />
                    <div className="h-3 bg-gray-100 rounded w-3/4" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {!loading && posts.length === 0 && (
            <div className="text-center py-24">
              <div className="text-5xl mb-4">📷</div>
              <p className="text-gray-500 text-sm">{lp.manageEmpty}</p>
            </div>
          )}

          {!loading && posts.length > 0 && (
            <div className="max-w-[468px] mx-auto flex flex-col gap-4">
              {posts.map((p) => (
                <article key={p.id} className="bg-white border border-gray-100 rounded-2xl overflow-hidden shadow-sm">
                  {p.imageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={p.imageUrl} alt="" className="w-full aspect-square object-cover" />
                  )}
                  <div className="p-4">
                    <p className="text-sm text-gray-800 whitespace-pre-line leading-relaxed">{p.content}</p>
                    <div className="flex items-center justify-between mt-3">
                      <span className="text-xs text-gray-400">
                        {new Date(p.createdAt).toLocaleDateString(locale, { year: "numeric", month: "short", day: "numeric" })}
                      </span>
                      <button
                        onClick={() => onDeletePost(p.id)}
                        className="text-xs text-red-400 hover:text-red-600 font-medium transition-colors"
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
        onClick={openCompose}
        className="fixed bottom-6 right-6 w-14 h-14 bg-indigo-600 hover:bg-indigo-700 active:scale-95 text-white rounded-full shadow-lg shadow-indigo-200 flex items-center justify-center text-3xl font-light transition-all z-40"
        aria-label={lp.newPost}
      >
        +
      </button>

      {/* Compose modal */}
      {composing && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm"
            onClick={() => !postSubmitting && setComposing(false)}
          />
          <div className="relative z-10 bg-white w-full sm:max-w-lg rounded-t-2xl sm:rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
            {/* Modal header */}
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <button
                type="button"
                onClick={() => !postSubmitting && setComposing(false)}
                className="text-sm text-gray-500 hover:text-gray-800 transition-colors"
              >
                {lp.cancelBtn}
              </button>
              <h2 className="font-semibold text-gray-900 text-sm">{lp.newPost}</h2>
              <button
                type="submit"
                form="compose-form"
                disabled={postSubmitting || !postContent.trim()}
                className="text-sm font-semibold text-indigo-600 hover:text-indigo-800 disabled:opacity-40 transition-colors"
              >
                {postSubmitting ? lp.submitting : lp.submitBtn}
              </button>
            </div>

            <form id="compose-form" onSubmit={onPostSubmit} className="px-5 py-4 flex flex-col gap-4">
              {error && (
                <div className="rounded-xl bg-red-50 px-4 py-2 text-sm text-red-600">{error}</div>
              )}

              <textarea
                placeholder={lp.postPlaceholder}
                value={postContent}
                onChange={(e) => setPostContent(e.target.value)}
                rows={5}
                required
                autoFocus
                className="input resize-none text-base leading-relaxed"
              />

              {postImagePreview ? (
                <div className="relative">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={postImagePreview} alt="preview" className="w-full max-h-72 rounded-xl object-cover" />
                  <label className="absolute bottom-2 right-2 bg-black/50 hover:bg-black/70 text-white text-xs font-medium px-3 py-1.5 rounded-full cursor-pointer transition-colors">
                    {lp.imageBtnChange}
                    <input type="file" accept="image/*" onChange={onPostImageChange} className="hidden" />
                  </label>
                </div>
              ) : (
                <label className="flex items-center gap-3 p-4 rounded-xl border-2 border-dashed border-gray-200 hover:border-indigo-300 cursor-pointer transition-colors group">
                  <span className="text-2xl">🖼️</span>
                  <span className="text-sm text-gray-400 group-hover:text-indigo-500 transition-colors">{lp.imageBtnAdd}</span>
                  <span className="text-xs text-gray-300 ml-auto">{lp.imageHint}</span>
                  <input type="file" accept="image/*" onChange={onPostImageChange} className="hidden" />
                </label>
              )}
            </form>
          </div>
        </div>
      )}
    </>
  );
}
