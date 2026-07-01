"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import SlotCalendar from "@/components/SlotCalendar";

type Language   = { language: string; level: string };
type Credential = { id: number; type: string; title: string; fileUrl: string };
type GuideDetail = {
  id: number; guideName: string; headline: string; introduction: string | null;
  hourlyRate: number; currency: string; region: string; avatarUrl: string | null;
  avgRating: number; reviewCount: number; followerCount: number; isFollowing: boolean;
  mbti: string | null; interests: string[];
  languages: Language[]; credentials: Credential[];
};
type Review   = { id: number; reviewerName: string; rating: number; comment: string | null; createdAt: string };
type GuidePost = {
  id: number; content: string; imageUrl: string | null; createdAt: string;
  likeCount: number; commentCount: number; isLiked: boolean;
};
type AvailableSlot = { id: number; guideProfileId: number; startAt: string; endAt: string };
type PostComment = { id: number; postId: number; userId: number; userName: string; content: string; createdAt: string };

function Stars({ n, total = 5 }: { n: number; total?: number }) {
  return (
    <span>
      {Array.from({ length: total }).map((_, i) => (
        <span key={i} className={i < n ? "text-amber-400" : "text-gray-200"}>★</span>
      ))}
    </span>
  );
}

function PostCard({
  post, guideName, avatarUrl,
  onLikeChange,
}: {
  post: GuidePost; guideName: string; avatarUrl: string | null;
  onLikeChange: (id: number, liked: boolean, count: number) => void;
}) {
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
      setComments(await api<PostComment[]>(`/api/posts/${post.id}/comments`));
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
      {/* Image */}
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
          <span className="font-semibold mr-1">{guideName}</span>
          {post.content}
        </p>
        <p className="mt-2 text-xs text-gray-400">
          {new Date(post.createdAt).toLocaleDateString(locale, { year: "numeric", month: "short", day: "numeric" })}
        </p>
      </div>

      {/* Comments */}
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

export default function GuideDetailPage() {
  const params  = useParams();
  const router  = useRouter();
  const { t, lang } = useLanguage();
  const l       = t.guideDetail;
  const id      = params.id as string;

  const [tab, setTab] = useState<"profile" | "posts">("profile");

  const [guide, setGuide]     = useState<GuideDetail | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [posts, setPosts]     = useState<GuidePost[]>([]);
  const [slots, setSlots]     = useState<AvailableSlot[]>([]);
  const [error, setError]     = useState("");
  const [followLoading, setFollowLoading] = useState(false);

  const [selectedSlot, setSelectedSlot] = useState<AvailableSlot | null>(null);
  const [useManual, setUseManual]       = useState(false);

  const [showForm, setShowForm]       = useState(false);
  const [startAt, setStartAt]         = useState("");
  const [hours, setHours]             = useState("2");
  const [bookingMsg, setBookingMsg]   = useState("");
  const [bookingError, setBookingError] = useState("");
  const [submitting, setSubmitting]   = useState(false);

  useEffect(() => {
    api<GuideDetail>(`/api/guides/${id}`).then(setGuide).catch((e) => setError(e.message));
    api<Review[]>(`/api/guides/${id}/reviews`).then(setReviews).catch(() => {});
    api<GuidePost[]>(`/api/guides/${id}/posts`).then(setPosts).catch(() => {});
    api<AvailableSlot[]>(`/api/guides/${id}/slots`).then(setSlots).catch(() => {});
  }, [id]);

  async function onFollow() {
    if (!getToken()) { router.push("/login"); return; }
    if (!guide) return;
    setFollowLoading(true);
    try {
      if (guide.isFollowing) {
        await api(`/api/guides/${guide.id}/follow`, { method: "DELETE", auth: true });
        setGuide({ ...guide, isFollowing: false, followerCount: guide.followerCount - 1 });
      } else {
        await api(`/api/guides/${guide.id}/follow`, { method: "POST", auth: true });
        setGuide({ ...guide, isFollowing: true, followerCount: guide.followerCount + 1 });
      }
    } catch { /* ignore */ }
    finally { setFollowLoading(false); }
  }

  function handleLikeChange(postId: number, liked: boolean, likeCount: number) {
    setPosts((prev) => prev.map((p) => p.id === postId ? { ...p, isLiked: liked, likeCount } : p));
  }

  function onSlotSelect(slot: AvailableSlot) {
    setSelectedSlot(slot);
    // Pre-fill booking form
    const start = new Date(slot.startAt);
    const end   = new Date(slot.endAt);
    const pad = (n: number) => String(n).padStart(2, "0");
    const fmt = (d: Date) =>
      `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    setStartAt(fmt(start));
    const durationHours = Math.round((end.getTime() - start.getTime()) / 3600000);
    setHours(String(durationHours || 1));
    setShowForm(true);
  }

  function onBookClick() {
    if (!getToken()) { router.push("/login"); return; }
    if (slots.length > 0 && !useManual) {
      // Show slot picker (handled by panel render)
      setShowForm(false);
    } else {
      setShowForm(true);
    }
  }

  async function onBookingSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBookingError(""); setSubmitting(true);
    try {
      await api("/api/bookings", {
        method: "POST", auth: true,
        body: { guideId: Number(id), startAt: new Date(startAt).toISOString(), hours: Number(hours), message: bookingMsg },
      });
      router.push("/traveler/bookings");
    } catch (err) {
      setBookingError(err instanceof Error ? err.message : t.common.error);
    } finally { setSubmitting(false); }
  }

  if (error) return (
    <main className="page px-4"><div className="container-sm">
      <p className="text-red-600 mb-4">{error}</p>
      <Link href="/guides" className="text-indigo-600 hover:underline text-sm">{l.back}</Link>
    </div></main>
  );
  if (!guide) return (
    <main className="page flex items-center justify-center">
      <div className="text-gray-400 text-sm">{t.common.loading}</div>
    </main>
  );

  const totalPrice = guide.hourlyRate * (Number(hours) || 0);
  const locale = lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US";

  return (
    <main className="page px-4">
      <div className="mx-auto max-w-4xl">
        <Link href="/guides" className="inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline mb-6">
          {l.back}
        </Link>

        <div className="lg:grid lg:grid-cols-[1fr_320px] lg:gap-8">
          {/* Left column */}
          <div>
            {/* Guide header — always visible */}
            <div className="card p-6 mb-0 rounded-b-none border-b-0">
              <div className="flex gap-5 items-start">
                {guide.avatarUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={guide.avatarUrl} alt={guide.guideName}
                    className="w-20 h-20 rounded-2xl object-cover ring-2 ring-indigo-100 shadow-sm flex-shrink-0" />
                ) : (
                  <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-indigo-400 to-violet-500 flex items-center justify-center text-white text-3xl font-bold flex-shrink-0">
                    {guide.guideName.slice(0, 1)}
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h1 className="text-xl font-bold text-gray-900">{guide.guideName}</h1>
                      <p className="text-gray-600 text-sm mt-0.5">{guide.headline}</p>
                    </div>
                    <button
                      onClick={onFollow}
                      disabled={followLoading}
                      className={`flex-shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold border transition-colors ${
                        guide.isFollowing
                          ? "bg-indigo-600 text-white border-indigo-600"
                          : "bg-white text-indigo-600 border-indigo-300 hover:bg-indigo-50"
                      } disabled:opacity-60`}
                    >
                      {guide.isFollowing ? t.personality.unfollowBtn : t.personality.followBtn}
                    </button>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
                    <span className="text-gray-500 flex items-center gap-1">📍 {guide.region}</span>
                    <span className="text-gray-500">
                      <span className="font-semibold text-gray-800">{guide.followerCount}</span>
                      {" "}{t.personality.followers}
                    </span>
                    {guide.reviewCount > 0 && (
                      <span className="flex items-center gap-1.5">
                        <Stars n={Math.round(guide.avgRating)} />
                        <span className="font-semibold text-gray-800">{guide.avgRating.toFixed(1)}</span>
                        <span className="text-gray-400">({guide.reviewCount} {l.reviewCount})</span>
                      </span>
                    )}
                  </div>
                  {/* MBTI + Interests */}
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    {guide.mbti && (
                      <span className="rounded-lg bg-violet-100 text-violet-700 px-2.5 py-0.5 text-xs font-bold">{guide.mbti}</span>
                    )}
                    {guide.interests.map((k) => (
                      <span key={k} className="rounded-full bg-gray-100 text-gray-600 px-2.5 py-0.5 text-xs">
                        {t.interests[k as keyof typeof t.interests] ?? k}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            {/* Tab bar */}
            <div className="flex border-b border-gray-100 bg-white rounded-none px-2 shadow-sm shadow-gray-100 mb-5"
              style={{ borderLeft: "1px solid #f3f4f6", borderRight: "1px solid #f3f4f6" }}>
              {[
                { key: "profile", label: l.tabProfile },
                { key: "posts",   label: `${l.tabPosts}${posts.length > 0 ? ` (${posts.length})` : ""}` },
              ].map((tb) => (
                <button
                  key={tb.key}
                  onClick={() => setTab(tb.key as "profile" | "posts")}
                  className={`pb-3 pt-3 px-5 text-sm font-medium transition-colors border-b-2 -mb-px ${
                    tab === tb.key
                      ? "border-indigo-600 text-indigo-600"
                      : "border-transparent text-gray-400 hover:text-gray-700"
                  }`}
                >
                  {tb.label}
                </button>
              ))}
            </div>

            {/* ── 프로필 탭 ── */}
            {tab === "profile" && (
              <div className="flex flex-col gap-5">
                {guide.introduction && (
                  <div className="card p-6">
                    <h2 className="font-semibold text-gray-900 mb-3">{l.intro}</h2>
                    <p className="text-sm text-gray-600 whitespace-pre-line leading-relaxed">{guide.introduction}</p>
                  </div>
                )}

                <div className="card p-6">
                  <h2 className="font-semibold text-gray-900 mb-3">{l.languages}</h2>
                  <div className="flex flex-wrap gap-2">
                    {guide.languages.map((lv, i) => (
                      <span key={i} className="badge-indigo py-1 px-3 text-sm">
                        {lv.language}
                        <span className="opacity-60 ml-1">· {t.level[lv.level as keyof typeof t.level] ?? lv.level}</span>
                      </span>
                    ))}
                  </div>
                </div>

                {guide.credentials.length > 0 && (
                  <div className="card p-6">
                    <h2 className="font-semibold text-gray-900 mb-3">{l.credentials}</h2>
                    <ul className="flex flex-col gap-2">
                      {guide.credentials.map((c) => (
                        <li key={c.id} className="flex items-center justify-between rounded-xl bg-gray-50 px-4 py-3">
                          <span className="flex items-center gap-2 text-sm">
                            <span className="badge-gray">{t.credType[c.type as keyof typeof t.credType] ?? c.type}</span>
                            <span className="text-gray-700">{c.title}</span>
                          </span>
                          <a href={c.fileUrl} target="_blank" rel="noopener noreferrer"
                            className="text-xs text-indigo-600 hover:underline font-medium">{l.view}</a>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                <div className="card p-6">
                  <h2 className="font-semibold text-gray-900 mb-4">
                    {l.reviews}{guide.reviewCount > 0 ? ` (${guide.reviewCount})` : ""}
                  </h2>
                  {reviews.length === 0 ? (
                    <p className="text-sm text-gray-400 text-center py-4">{l.noReview}</p>
                  ) : (
                    <ul className="flex flex-col gap-3">
                      {reviews.map((r) => (
                        <li key={r.id} className="rounded-xl bg-gray-50 p-4">
                          <div className="flex justify-between items-center mb-2">
                            <span className="font-medium text-sm text-gray-900">{r.reviewerName}</span>
                            <Stars n={r.rating} />
                          </div>
                          {r.comment && <p className="text-sm text-gray-600 leading-relaxed">{r.comment}</p>}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            )}

            {/* ── 게시글 탭 ── */}
            {tab === "posts" && (
              <div>
                {posts.length === 0 ? (
                  <div className="text-center py-16">
                    <div className="text-4xl mb-3">📝</div>
                    <p className="text-sm text-gray-400">{l.noPosts}</p>
                  </div>
                ) : (
                  <div className="flex flex-col gap-4">
                    {posts.map((p) => (
                      <PostCard
                        key={p.id}
                        post={p}
                        guideName={guide.guideName}
                        avatarUrl={guide.avatarUrl}
                        onLikeChange={handleLikeChange}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Right — sticky booking panel */}
          <div className="mt-5 lg:mt-0">
            <div className="sticky top-24 flex flex-col gap-4">

              {/* Price card */}
              <div className="card p-5 shadow-lg">
                <div className="flex items-baseline justify-between mb-1">
                  <span className="text-2xl font-bold text-gray-900">{guide.hourlyRate.toLocaleString()}</span>
                  <span className="text-sm text-gray-500">{guide.currency} {l.perHour}</span>
                </div>
                {guide.reviewCount > 0 && (
                  <div className="flex items-center gap-1.5 text-sm text-gray-500">
                    <span className="text-amber-400">★</span>
                    <span className="font-semibold text-gray-700">{guide.avgRating.toFixed(1)}</span>
                    <span>· {guide.reviewCount} {l.reviewCount}</span>
                  </div>
                )}
              </div>

              {/* Slot picker or booking form */}
              {!showForm ? (
                <div className="card p-5 shadow-lg">
                  {slots.length === 0 ? (
                    /* No slots — direct booking */
                    <div>
                      <p className="text-sm text-gray-400 mb-4">{t.availability.guideEmpty}</p>
                      <button
                        onClick={() => { if (!getToken()) { router.push("/login"); return; } setShowForm(true); }}
                        className="btn-primary w-full py-3"
                      >
                        {l.bookBtn}
                      </button>
                      <p className="mt-2 text-center text-xs text-gray-400">{l.bookNote}</p>
                    </div>
                  ) : (
                    /* Slot calendar */
                    <div>
                      <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                        📅 {t.availability.sectionTitle}
                      </h3>
                      <p className="text-xs text-gray-400 mb-3">{t.availability.hint}</p>

                      <SlotCalendar
                        mode="traveler"
                        slots={slots}
                        selectedSlot={selectedSlot}
                        onSlotSelect={(s) => setSelectedSlot(s)}
                        hourlyRate={guide.hourlyRate}
                        currency={guide.currency}
                      />

                      {selectedSlot && (
                        <button
                          onClick={() => { if (!getToken()) { router.push("/login"); return; } onSlotSelect(selectedSlot); }}
                          className="btn-primary w-full py-3 mt-3 mb-2"
                        >
                          {t.availability.slotBtn}
                        </button>
                      )}

                      <button
                        onClick={() => { if (!getToken()) { router.push("/login"); return; } setUseManual(true); setShowForm(true); }}
                        className="w-full text-xs text-gray-400 hover:text-indigo-600 transition-colors py-1 mt-1"
                      >
                        {t.availability.orManual} →
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                /* Booking form */
                <form onSubmit={onBookingSubmit} className="card p-5 shadow-lg flex flex-col gap-4">
                  <div className="flex items-center justify-between">
                    <h2 className="font-semibold text-gray-900">{l.bookTitle}</h2>
                    <button type="button" onClick={() => { setShowForm(false); setUseManual(false); setSelectedSlot(null); }}
                      className="text-gray-400 hover:text-gray-600 text-lg">✕</button>
                  </div>

                  {/* Selected slot badge */}
                  {selectedSlot && (
                    <div className="rounded-xl bg-indigo-50 border border-indigo-100 px-4 py-2.5 flex items-center gap-2">
                      <span className="text-indigo-500">📅</span>
                      <div>
                        <p className="text-xs font-semibold text-indigo-700">
                          {new Date(selectedSlot.startAt).toLocaleDateString(locale, { month: "short", day: "numeric", weekday: "short" })}
                          {" · "}
                          {new Date(selectedSlot.startAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                          {" – "}
                          {new Date(selectedSlot.endAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                        </p>
                        <p className="text-xs text-indigo-400">{t.availability.sectionTitle}</p>
                      </div>
                    </div>
                  )}

                  <div>
                    <label className="input-label">{l.startAt}</label>
                    <input type="datetime-local" value={startAt} onChange={(e) => setStartAt(e.target.value)} required className="input" />
                  </div>
                  <div>
                    <label className="input-label">{l.hours}</label>
                    <input type="number" min={1} value={hours} onChange={(e) => setHours(e.target.value)} required className="input" />
                  </div>
                  <div className="rounded-xl bg-indigo-50 px-4 py-3 flex items-center justify-between">
                    <span className="text-sm text-gray-600">{l.estPrice}</span>
                    <span className="font-bold text-indigo-700">{totalPrice.toLocaleString()} {guide.currency}</span>
                  </div>
                  <div>
                    <label className="input-label">
                      {l.message} <span className="text-gray-400 normal-case font-normal">{l.messageOpt}</span>
                    </label>
                    <textarea placeholder={l.messagePlaceholder} value={bookingMsg}
                      onChange={(e) => setBookingMsg(e.target.value)} rows={3} className="input resize-none" />
                  </div>
                  {bookingError && (
                    <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600 border border-red-100">{bookingError}</p>
                  )}
                  <button type="submit" disabled={submitting} className="btn-primary w-full py-3">
                    {submitting ? l.sending : l.sendBtn}
                  </button>
                </form>
              )}
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
