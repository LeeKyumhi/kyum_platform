"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { localeOf } from "@/lib/i18n";
import SlotCalendar from "@/components/SlotCalendar";
import TripMap from "@/components/TripMap";
import ReportBlockMenu from "@/components/ReportBlockMenu";
import {
  StarIcon, PinIcon, HeartIcon, ChatIcon, CheckBadgeIcon,
  CalendarIcon, ChevronLeftIcon,
} from "@/components/icons";

type Language   = { language: string; level: string };
type Credential = { id: number; type: string; title: string; fileUrl: string };
type GuideDetail = {
  id: number; guideUserId: number; guideName: string; headline: string; introduction: string | null;
  hourlyRate: number; currency: string; region: string; avatarUrl: string | null;
  avgRating: number; reviewCount: number; followerCount: number; isFollowing: boolean;
  mbti: string | null; interests: string[]; gender: string | null; instantBooking: boolean;
  languages: Language[]; credentials: Credential[]; verificationStatus: string; serviceCategories: string[];
};
type Review   = { id: number; reviewerName: string; rating: number; comment: string | null; tags: string[]; createdAt: string };
type ReviewStats = {
  average: number; count: number;
  distribution: Record<string, number>;
  tagCounts: Record<string, number>;
};
type GuidePost = {
  id: number; content: string; imageUrl: string | null; createdAt: string;
  likeCount: number; commentCount: number; isLiked: boolean;
};
type AvailableSlot = { id: number; guideProfileId: number; startAt: string; endAt: string };
type CourseWaypoint = {
  id: number; sortOrder: number; placeId: string | null; placeName: string;
  category: string | null; address: string | null; latitude: number | null; longitude: number | null;
};
type TourCourse = {
  id: number; title: string; description: string | null; city: string | null;
  durationHours: number; price: number; currency: string; maxPeople: number; imageUrl: string | null;
  waypoints: CourseWaypoint[];
};
type PostComment = { id: number; postId: number; userId: number; userName: string; content: string; createdAt: string };

// 별점 분포 막대 너비 — Tailwind는 문자열 보간으로 만든 임의값 클래스를 정적으로 못 읽으므로
// 10% 단위로 미리 적어둔 리터럴 클래스 중에서 골라 쓴다 (동적 문자열 조합 금지).
const BAR_WIDTH_CLASSES: Record<number, string> = {
  0: "w-0", 10: "w-[10%]", 20: "w-[20%]", 30: "w-[30%]", 40: "w-[40%]", 50: "w-[50%]",
  60: "w-[60%]", 70: "w-[70%]", 80: "w-[80%]", 90: "w-[90%]", 100: "w-full",
};
function barWidthClass(pct: number) {
  const bucket = Math.min(100, Math.max(0, Math.round(pct / 10) * 10));
  return BAR_WIDTH_CLASSES[bucket];
}

function Stars({ n, total = 5 }: { n: number; total?: number }) {
  return (
    <span className="inline-flex items-center gap-0.5">
      {Array.from({ length: total }).map((_, i) => (
        <StarIcon key={i} className={`h-4 w-4 ${i < n ? "text-amber-400" : "text-stone-200"}`} />
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
  const lc = t.chat;
  const router = useRouter();
  const [showComments, setShowComments] = useState(false);
  const [comments, setComments] = useState<PostComment[]>([]);
  const [commentsLoaded, setCommentsLoaded] = useState(false);
  const [commentText, setCommentText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const locale = localeOf(lang);

  // 게시글 번역: 이 카드는 한 게시글만 다루므로 단일 상태로 캐시
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
    <article className="card overflow-hidden">
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
            post.isLiked ? "text-sky-500" : "text-stone-400 hover:text-sky-400"
          }`}
        >
          <HeartIcon className="h-6 w-6" filled={post.isLiked} />
          {post.likeCount > 0 && <span className="font-semibold text-stone-700">{post.likeCount}</span>}
        </button>
        <button
          onClick={toggleComments}
          className="flex items-center gap-1.5 text-sm text-stone-400 hover:text-stone-700 transition-colors"
        >
          <ChatIcon className="h-6 w-6" />
          {post.commentCount > 0 && <span className="font-semibold text-stone-700">{post.commentCount}</span>}
        </button>
      </div>

      {/* Content */}
      <div className="px-4 pb-3">
        <p className="text-sm text-stone-800 whitespace-pre-line leading-relaxed mt-1">
          <span className="font-bold mr-1">{guideName}</span>
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

      {/* Comments */}
      {showComments && (
        <div className="border-t border-stone-50 px-4 pb-4">
          {commentsLoaded && comments.length === 0 && (
            <p className="text-xs text-stone-400 py-3 text-center">{lp.noComments}</p>
          )}
          {comments.length > 0 && (
            <ul className="pt-3 flex flex-col gap-2">
              {comments.map((c) => (
                <li key={c.id} className="text-sm">
                  <span className="font-bold text-stone-900 mr-1">{c.userName}</span>
                  <span className="text-stone-700">{c.content}</span>
                </li>
              ))}
            </ul>
          )}
          <form onSubmit={submitComment} className="flex items-center gap-2 mt-3">
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder={lp.commentPlaceholder}
              className="flex-1 text-sm border-none bg-stone-100 rounded-full px-4 py-2 focus:outline-none focus:ring-2 focus:ring-sky-200"
              disabled={submitting}
            />
            <button
              type="submit"
              disabled={!commentText.trim() || submitting}
              className="text-sm text-sky-500 font-semibold disabled:opacity-40 hover:text-sky-700 transition-colors flex-shrink-0"
            >
              {lp.commentSubmit}
            </button>
          </form>
        </div>
      )}
    </article>
  );
}

export default function ProfileDetailView({ track }: { track: "tour" | "companion" }) {
  const params  = useParams();
  const router  = useRouter();
  const { t, lang } = useLanguage();
  const l       = t.guideDetail;
  const id      = params.id as string;

  const [tab, setTab] = useState<"profile" | "posts">("profile");

  const [guide, setGuide]     = useState<GuideDetail | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [reviewStats, setReviewStats] = useState<ReviewStats | null>(null);
  const [posts, setPosts]     = useState<GuidePost[]>([]);
  const [slots, setSlots]     = useState<AvailableSlot[]>([]);
  const [courses, setCourses] = useState<TourCourse[]>([]);
  const [expandedCourseId, setExpandedCourseId] = useState<number | null>(null);
  const [error, setError]     = useState("");
  const [followLoading, setFollowLoading] = useState(false);
  const [dmLoading, setDmLoading] = useState(false);
  // 로그인 여부 — 하이드레이션 불일치 방지를 위해 마운트 후에만 설정
  const [loggedIn, setLoggedIn] = useState(false);
  useEffect(() => { setLoggedIn(!!getToken()); }, []);

  // 리뷰 번역: reviewId → 번역문 / 표시 여부 캐시
  const [reviewTranslations, setReviewTranslations] = useState<Record<number, string>>({});
  const [reviewShown, setReviewShown]               = useState<Record<number, boolean>>({});
  const [reviewTranslating, setReviewTranslating]   = useState<Record<number, boolean>>({});

  async function toggleReviewTranslate(reviewId: number) {
    if (reviewTranslations[reviewId] !== undefined) {
      setReviewShown((prev) => ({ ...prev, [reviewId]: !prev[reviewId] }));
      return;
    }
    setReviewTranslating((prev) => ({ ...prev, [reviewId]: true }));
    try {
      const res = await api<{ translated: string }>(`/api/reviews/${reviewId}/translate?lang=${lang}`);
      setReviewTranslations((prev) => ({ ...prev, [reviewId]: res.translated }));
      setReviewShown((prev) => ({ ...prev, [reviewId]: true }));
    } catch {
      setReviewTranslations((prev) => ({ ...prev, [reviewId]: "" }));
    } finally {
      setReviewTranslating((prev) => ({ ...prev, [reviewId]: false }));
    }
  }

  const [selectedSlots, setSelectedSlots] = useState<AvailableSlot[]>([]); // 다중 선택(#3)
  const [useManual, setUseManual]       = useState(false);

  const [showForm, setShowForm]       = useState(false);
  // 직접 입력 다중일 예약(#1): 각 행 = 하루의 날짜 + 시작~종료 시간
  const [dayRows, setDayRows] = useState<{ date: string; from: string; to: string }[]>([{ date: "", from: "10:00", to: "12:00" }]);
  const [bookingMsg, setBookingMsg]   = useState("");
  const [bookingCategory, setBookingCategory] = useState("");
  const [bookingError, setBookingError] = useState("");
  const [submitting, setSubmitting]   = useState(false);
  // 예약 결과 요약(#3): 성공 건수 / 즉시확정 건수 / 실패 건수
  const [bookingSummary, setBookingSummary] = useState<{ sent: number; accepted: number; failed: number } | null>(null);

  useEffect(() => {
    api<GuideDetail>(`/api/guides/${id}`).then((g) => {
      setGuide(g);
      // 제공 서비스가 하나뿐이면 자동 선택(마찰 감소), 여러 개면 여행자가 고른다.
      if (g.serviceCategories?.length === 1) setBookingCategory(g.serviceCategories[0]);
    }).catch((e) => setError(e.message));
    api<Review[]>(`/api/guides/${id}/reviews`).then(setReviews).catch(() => {});
    api<ReviewStats>(`/api/guides/${id}/review-stats`).then(setReviewStats).catch(() => {});
    api<GuidePost[]>(`/api/guides/${id}/posts`).then(setPosts).catch(() => {});
    api<AvailableSlot[]>(`/api/guides/${id}/slots`).then(setSlots).catch(() => {});
    api<TourCourse[]>(`/api/guides/${id}/courses`).then(setCourses).catch(() => {});
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

  // 예약 전 문의 — 대화방을 얻어(없으면 생성) 메시지 화면으로 이동
  async function onMessage() {
    if (!getToken()) { router.push("/login"); return; }
    setDmLoading(true);
    try {
      const c = await api<{ id: number }>("/api/conversations", {
        method: "POST", body: { guideProfileId: Number(id) }, auth: true,
      });
      router.push(`/messages/${c.id}`);
    } catch { setDmLoading(false); /* 본인 프로필 등 — 이동하지 않고 버튼만 되돌린다 */ }
  }

  function handleLikeChange(postId: number, liked: boolean, likeCount: number) {
    setPosts((prev) => prev.map((p) => p.id === postId ? { ...p, isLiked: liked, likeCount } : p));
  }

  const slotHours = (s: AvailableSlot) =>
    Math.max(1, Math.round((new Date(s.endAt).getTime() - new Date(s.startAt).getTime()) / 3600000));

  function toggleSlot(slot: AvailableSlot) {
    setSelectedSlots((prev) =>
      prev.some((x) => x.id === slot.id) ? prev.filter((x) => x.id !== slot.id) : [...prev, slot]);
  }

  /**
   * 날짜별 예약 요청을 순차 생성 후 요약 보고 — 슬롯 다중선택·직접입력 다일 폼 공용.
   * 하나 실패(시간겹침 등)해도 나머지는 진행. 즉시예약 가이드의 겹침 이중확정을 막기 위해
   * 의도적으로 순차 실행한다 (병렬이면 겹침 검사가 서로를 못 본다).
   */
  async function postBookings(entries: { startAt: string; hours: number }[]) {
    if (!bookingCategory) { setBookingError(t.serviceCategories.pickPrompt); return; }
    setSubmitting(true); setBookingError("");
    let sent = 0, accepted = 0, failed = 0;
    for (const e of entries) {
      try {
        const res = await api<{ status: string }>("/api/bookings", {
          method: "POST", auth: true,
          body: { guideId: Number(id), startAt: e.startAt, hours: e.hours, serviceCategory: bookingCategory, message: bookingMsg },
        });
        if (res.status === "ACCEPTED") accepted++; else sent++;
      } catch { failed++; }
    }
    setBookingSummary({ sent, accepted, failed });
    setSubmitting(false);
  }

  // 선택한 슬롯들을 날짜별 예약으로 각각 생성(#3).
  async function confirmSlots() {
    if (!getToken()) { router.push("/login"); return; }
    if (selectedSlots.length === 0) return;
    await postBookings(selectedSlots.map((s) => ({
      startAt: new Date(s.startAt).toISOString(), hours: slotHours(s),
    })));
  }

  // 시간 입력은 시간 단위(step=3600)로 제약 → 정수 시간 보장
  function rowHours(r: { from: string; to: string }): number {
    const [fh, fm] = r.from.split(":").map(Number);
    const [th, tm] = r.to.split(":").map(Number);
    return Math.round((th * 60 + tm - (fh * 60 + fm)) / 60);
  }
  function addDayRow() {
    setDayRows((rows) => [...rows, { date: "", from: "10:00", to: "12:00" }]);
  }
  function removeDayRow(i: number) {
    setDayRows((rows) => (rows.length <= 1 ? rows : rows.filter((_, idx) => idx !== i)));
  }
  function updateRow(i: number, patch: Partial<{ date: string; from: string; to: string }>) {
    setDayRows((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  }

  // 직접 입력한 날짜별 행을 각각 예약으로 생성. 부분 실패 요약(#1, #3과 동일).
  async function submitManualDays(e: React.FormEvent) {
    e.preventDefault();
    setBookingError("");
    const rows = dayRows.filter((r) => r.date && r.from && r.to);
    if (rows.length === 0) { setBookingError(l.futureDateError); return; }
    for (const r of rows) {
      const start = new Date(`${r.date}T${r.from}`);
      if (isNaN(start.getTime()) || start.getTime() <= Date.now()) { setBookingError(l.futureDateError); return; }
      if (rowHours(r) < 1) { setBookingError(l.hoursError); return; }
    }
    await postBookings(rows.map((r) => ({
      startAt: new Date(`${r.date}T${r.from}`).toISOString(), hours: rowHours(r),
    })));
  }

  if (error) return (
    <main className="page px-4"><div className="container-sm">
      <p className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
      <Link href="/guides" className="inline-flex items-center gap-1 text-sm font-semibold text-sky-500 hover:underline">
        <ChevronLeftIcon className="h-4 w-4" /> {l.back}
      </Link>
    </div></main>
  );
  if (!guide) return (
    <main className="page flex items-center justify-center">
      <div className="text-stone-400 text-sm">{t.common.loading}</div>
    </main>
  );

  const totalManualPrice = dayRows.reduce((sum, r) => sum + guide!.hourlyRate * Math.max(0, rowHours(r)), 0);
  const totalSelectedPrice = selectedSlots.reduce((sum, s) => sum + guide!.hourlyRate * slotHours(s), 0);
  const locale = localeOf(lang);
  // 관광통역안내사 자격 인증 배지는 관리자 승인(VERIFIED)일 때만. 파일 업로드만으로는 인증되지 않는다.
  const isVerified = guide.verificationStatus === "VERIFIED";

  // 예약할 서비스 선택 (관광/비관광). 가이드가 제공하는 서비스 중에서만 고른다 — 두 예약 플로우 공용.
  const svcLabels = t.serviceCategories as Record<string, string>;
  const bookingCategorySelect = guide.serviceCategories.length > 0 ? (
    <div>
      <label className="input-label">{t.serviceCategories.pickPrompt}</label>
      <select value={bookingCategory} onChange={(e) => setBookingCategory(e.target.value)} className="input">
        <option value="">—</option>
        {guide.serviceCategories.map((k) => (
          <option key={k} value={k}>{svcLabels[k] ?? k}</option>
        ))}
      </select>
    </div>
  ) : null;

  return (
    <main className="page px-4">
      <div className="mx-auto max-w-4xl">
        <Link
          href="/guides"
          className="mb-5 inline-flex items-center gap-1.5 rounded-full py-1 pr-3 text-sm font-semibold text-stone-500 transition-colors hover:text-stone-900"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-full border border-stone-200 bg-white shadow-sm">
            <ChevronLeftIcon className="h-4 w-4" />
          </span>
          {l.back}
        </Link>

        <div className="lg:grid lg:grid-cols-[1fr_340px] lg:gap-8">
          {/* Left column */}
          <div>
            {/* Guide header — Airbnb-style banner + overlapping avatar */}
            <div className="card mb-5 overflow-hidden">
              <div className="h-28 bg-gradient-to-r from-sky-200 via-cyan-100 to-teal-100 md:h-32" />
              <div className="px-5 pb-6 md:px-7">
                <div className="-mt-10 flex items-end justify-between gap-3 md:-mt-12">
                  {guide.avatarUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={guide.avatarUrl} alt={guide.guideName}
                      className="h-20 w-20 flex-shrink-0 rounded-3xl object-cover shadow-md ring-4 ring-white md:h-24 md:w-24" />
                  ) : (
                    <div className="flex h-20 w-20 flex-shrink-0 items-center justify-center rounded-3xl bg-gradient-to-br from-sky-400 to-cyan-400 text-3xl font-bold text-white shadow-md ring-4 ring-white md:h-24 md:w-24">
                      {guide.guideName.slice(0, 1)}
                    </div>
                  )}
                  <div className="mb-1 flex flex-shrink-0 items-center gap-2">
                    {/* 예약 전에도 물어볼 수 있는 1:1 메시지 */}
                    <button
                      onClick={onMessage}
                      disabled={dmLoading}
                      className="flex items-center gap-1.5 rounded-full bg-sky-500 px-4 py-2 text-xs font-bold text-white transition-all hover:bg-sky-600 active:scale-95 disabled:opacity-60"
                    >
                      <ChatIcon className="h-3.5 w-3.5" /> {t.dm.messageBtn}
                    </button>
                    <button
                      onClick={onFollow}
                      disabled={followLoading}
                      className={`rounded-full px-5 py-2 text-xs font-bold transition-all ${
                        guide.isFollowing
                          ? "bg-stone-900 text-white hover:bg-stone-700"
                          : "border border-stone-300 bg-white text-stone-800 hover:border-stone-900"
                      } disabled:opacity-60`}
                    >
                      {guide.isFollowing ? t.personality.unfollowBtn : t.personality.followBtn}
                    </button>
                    {loggedIn && (
                      <ReportBlockMenu
                        targetUserId={guide.guideUserId}
                        onBlocked={() => router.push("/guides")}
                      />
                    )}
                  </div>
                </div>

                <div className="mt-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <h1 className="text-2xl font-extrabold tracking-tight text-stone-900">{guide.guideName}</h1>
                    {isVerified && (
                      <span className="badge-emerald py-1">
                        <CheckBadgeIcon className="h-4 w-4" /> {l.verified}
                      </span>
                    )}
                    {guide.instantBooking && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-700">
                        ⚡ {l.instantBadge}
                      </span>
                    )}
                    {guide.gender && (
                      <span className="badge-gray py-1 text-[11px]">
                        {guide.gender === "male" ? `♂ ${t.personality.genderMale}`
                          : guide.gender === "female" ? `♀ ${t.personality.genderFemale}`
                          : t.personality.genderOther}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-sm text-stone-600">{guide.headline}</p>

                  <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm">
                    <span className="flex items-center gap-1 text-stone-500">
                      <PinIcon className="h-4 w-4" /> {guide.region}
                    </span>
                    <span className="text-stone-500">
                      <span className="font-bold text-stone-900">{guide.followerCount}</span>
                      {" "}{t.personality.followers}
                    </span>
                    {guide.reviewCount > 0 && (
                      <span className="flex items-center gap-1.5">
                        <Stars n={Math.round(guide.avgRating)} />
                        <span className="font-bold text-stone-900">{guide.avgRating.toFixed(1)}</span>
                        <span className="text-stone-400">({guide.reviewCount} {l.reviewCount})</span>
                      </span>
                    )}
                  </div>

                  {/* MBTI + Interests */}
                  <div className="mt-3 flex flex-wrap items-center gap-1.5">
                    {guide.mbti && (
                      <span className="rounded-lg bg-violet-100 px-2.5 py-0.5 text-xs font-bold text-violet-700">{guide.mbti}</span>
                    )}
                    {guide.interests.map((k) => (
                      <span key={k} className="badge-gray py-1">
                        {t.interests[k as keyof typeof t.interests] ?? k}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            {/* Tab bar */}
            <div className="seg-wrap mb-5">
              {[
                { key: "profile", label: l.tabProfile },
                { key: "posts",   label: `${l.tabPosts}${posts.length > 0 ? ` (${posts.length})` : ""}` },
              ].map((tb) => (
                <button
                  key={tb.key}
                  onClick={() => setTab(tb.key as "profile" | "posts")}
                  className={tab === tb.key ? "seg-active" : "seg"}
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
                    <h2 className="mb-3 font-bold text-stone-900">{l.intro}</h2>
                    <p className="whitespace-pre-line text-sm leading-relaxed text-stone-600">{guide.introduction}</p>
                  </div>
                )}

                {/* 투어 코스 상품 */}
                {courses.length > 0 && (
                  <div className="card p-6">
                    <h2 className="mb-4 font-bold text-stone-900">🎫 {t.courses.tabTitle}</h2>
                    <div className="flex flex-col gap-3">
                      {courses.map((c) => {
                        const expanded = expandedCourseId === c.id;
                        return (
                          <div key={c.id} className="rounded-xl border border-stone-100 p-3">
                            <div className="flex gap-3">
                              {c.imageUrl ? (
                                // eslint-disable-next-line @next/next/no-img-element
                                <img src={c.imageUrl} alt={c.title} className="h-20 w-20 flex-shrink-0 rounded-lg object-cover" />
                              ) : (
                                <div className="flex h-20 w-20 flex-shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-sky-400 to-cyan-400 text-xl">
                                  🎫
                                </div>
                              )}
                              <div className="min-w-0 flex-1">
                                <h3 className="truncate text-sm font-bold text-stone-900">{c.title}</h3>
                                {c.description && (
                                  <p className="mt-0.5 text-xs leading-relaxed text-stone-500 line-clamp-2">{c.description}</p>
                                )}
                                <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-stone-400">
                                  <span>⏱ {c.durationHours}{t.courses.hoursUnit}</span>
                                  <span>👥 {t.courses.upTo} {c.maxPeople}{t.courses.peopleUnit}</span>
                                  <span className="font-bold text-stone-700">{c.price.toLocaleString()} {c.currency}/{t.courses.perPerson}</span>
                                </div>
                                {c.waypoints.length > 0 && (
                                  <button
                                    onClick={() => setExpandedCourseId(expanded ? null : c.id)}
                                    className="mt-1.5 text-xs font-semibold text-sky-500 hover:text-sky-700"
                                  >
                                    {expanded ? `▲ ${t.courses.hideRoute}` : `▼ ${t.courses.viewRoute} (${c.waypoints.length}${t.courses.stopUnit})`}
                                  </button>
                                )}
                              </div>
                            </div>

                            {expanded && c.waypoints.length > 0 && (
                              <div className="mt-3 border-t border-stone-100 pt-3">
                                <TripMap
                                  points={c.waypoints.map((w) => ({
                                    key: String(w.sortOrder), name: w.placeName, latitude: w.latitude, longitude: w.longitude,
                                  }))}
                                  className="mb-3"
                                />
                                <ol className="flex flex-col gap-1.5">
                                  {c.waypoints.map((w, i) => (
                                    <li key={w.id} className="flex items-start gap-2.5 rounded-xl bg-stone-50 px-3 py-2">
                                      <span className="mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-sky-500 text-[11px] font-bold text-white">
                                        {i + 1}
                                      </span>
                                      <div className="min-w-0 flex-1">
                                        <div className="flex flex-wrap items-baseline gap-x-2">
                                          <span className="text-sm font-semibold text-stone-800">{w.placeName}</span>
                                          {w.category && <span className="text-xs text-stone-400">{w.category}</span>}
                                        </div>
                                        {w.address && <p className="truncate text-xs text-stone-400">{w.address}</p>}
                                      </div>
                                    </li>
                                  ))}
                                </ol>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                <div className="card p-6">
                  <h2 className="mb-3 font-bold text-stone-900">{l.languages}</h2>
                  <div className="flex flex-wrap gap-2">
                    {guide.languages.map((lv, i) => (
                      <span key={i} className="badge-indigo px-3 py-1 text-sm">
                        {lv.language}
                        <span className="ml-1 opacity-60">· {t.level[lv.level as keyof typeof t.level] ?? lv.level}</span>
                      </span>
                    ))}
                  </div>
                </div>

                {guide.credentials.length > 0 && (
                  <div className="card p-6">
                    <h2 className="mb-3 flex items-center gap-1.5 font-bold text-stone-900">
                      <CheckBadgeIcon className="h-5 w-5 text-emerald-500" /> {l.credentials}
                    </h2>
                    <ul className="flex flex-col gap-2">
                      {guide.credentials.map((c) => (
                        <li key={c.id} className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-3">
                          <span className="flex items-center gap-2 text-sm">
                            <span className="badge-gray">{t.credType[c.type as keyof typeof t.credType] ?? c.type}</span>
                            <span className="text-stone-700">{c.title}</span>
                          </span>
                          <a href={c.fileUrl} target="_blank" rel="noopener noreferrer"
                            className="text-xs font-semibold text-sky-500 hover:underline">{l.view}</a>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                <div className="card p-6">
                  <h2 className="mb-4 flex items-center gap-2 font-bold text-stone-900">
                    <StarIcon className="h-5 w-5 text-amber-400" />
                    {l.reviews}{guide.reviewCount > 0 ? ` (${guide.reviewCount})` : ""}
                    {guide.reviewCount > 0 && (
                      <span className="text-sm font-extrabold text-stone-900">· {guide.avgRating.toFixed(1)}</span>
                    )}
                  </h2>

                  {/* 별점 분포 + 키워드 태그 (review-stats) */}
                  {reviewStats && reviewStats.count > 0 && (
                    <div className="mb-5 flex flex-col gap-4">
                      <div className="flex flex-col gap-1">
                        {[5, 4, 3, 2, 1].map((star) => {
                          const cnt = reviewStats.distribution[String(star)] ?? 0;
                          const pct = reviewStats.count > 0 ? Math.round((cnt / reviewStats.count) * 100) : 0;
                          return (
                            <div key={star} className="flex items-center gap-2 text-xs text-stone-500">
                              <span className="w-8 flex-shrink-0">{star}★</span>
                              <div className="h-2 flex-1 overflow-hidden rounded-full bg-stone-100">
                                <div className={`h-full rounded-full bg-amber-400 ${barWidthClass(pct)}`} />
                              </div>
                              <span className="w-6 flex-shrink-0 text-right font-medium text-stone-700">{cnt}</span>
                            </div>
                          );
                        })}
                      </div>

                      {Object.entries(reviewStats.tagCounts).filter(([, cnt]) => cnt > 0).length > 0 && (
                        <div>
                          <p className="mb-2 text-xs font-semibold text-stone-400">{l.reviewKeywords}</p>
                          <div className="flex flex-wrap gap-1.5">
                            {Object.entries(reviewStats.tagCounts)
                              .filter(([, cnt]) => cnt > 0)
                              .sort(([, a], [, b]) => b - a)
                              .map(([key, cnt]) => (
                                <span key={key} className="rounded-full bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-600 ring-1 ring-sky-100">
                                  {t.reviewTags[key as keyof typeof t.reviewTags] ?? key} {cnt}
                                </span>
                              ))}
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {reviews.length === 0 ? (
                    <p className="py-4 text-center text-sm text-stone-400">{l.noReview}</p>
                  ) : (
                    <ul className="flex flex-col gap-3">
                      {reviews.map((r) => (
                        <li key={r.id} className="rounded-xl bg-stone-50 p-4">
                          <div className="mb-2 flex items-center justify-between">
                            <span className="flex items-center gap-2.5">
                              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-stone-200 text-xs font-bold text-stone-600">
                                {r.reviewerName.slice(0, 1).toUpperCase()}
                              </span>
                              <span className="text-sm font-semibold text-stone-900">{r.reviewerName}</span>
                            </span>
                            <Stars n={r.rating} />
                          </div>
                          {r.tags.length > 0 && (
                            <div className="mb-2 flex flex-wrap gap-1.5">
                              {r.tags.map((key) => (
                                <span key={key} className="rounded-full bg-sky-50 px-2.5 py-0.5 text-[11px] font-semibold text-sky-600 ring-1 ring-sky-100">
                                  {t.reviewTags[key as keyof typeof t.reviewTags] ?? key}
                                </span>
                              ))}
                            </div>
                          )}
                          {r.comment && (
                            <>
                              <p className="text-sm leading-relaxed text-stone-600">{r.comment}</p>
                              {reviewShown[r.id] && reviewTranslations[r.id] && (
                                <p className="mt-1.5 border-t border-stone-200 pt-1.5 text-[13px] leading-relaxed text-sky-600">
                                  🌐 {reviewTranslations[r.id]}
                                </p>
                              )}
                              <button
                                onClick={() => toggleReviewTranslate(r.id)}
                                className="mt-1 text-[10px] font-medium text-stone-400 transition-colors hover:text-sky-500"
                              >
                                {reviewTranslating[r.id]
                                  ? t.chat.translating
                                  : reviewShown[r.id] && reviewTranslations[r.id]
                                    ? t.chat.hideTranslation
                                    : t.chat.translateBtn}
                              </button>
                            </>
                          )}
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
                  <div className="py-16 text-center">
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-stone-100 text-stone-400">
                      <ChatIcon className="h-7 w-7" />
                    </div>
                    <p className="text-sm text-stone-400">{l.noPosts}</p>
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

          {/* Right — sticky booking summary card */}
          <div className="mt-5 lg:mt-0">
            <div className="sticky top-24">
              <div className="card overflow-hidden shadow-lg">
                {/* Price header */}
                <div className="border-b border-stone-100 bg-stone-50/60 p-5">
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-3xl font-extrabold tracking-tight text-stone-900">
                      {guide.hourlyRate.toLocaleString()}
                    </span>
                    <span className="text-sm font-medium text-stone-500">{guide.currency} {l.perHour}</span>
                  </div>
                  {guide.reviewCount > 0 && (
                    <div className="mt-1.5 flex items-center gap-1.5 text-sm text-stone-500">
                      <StarIcon className="h-4 w-4 text-amber-400" />
                      <span className="font-bold text-stone-800">{guide.avgRating.toFixed(1)}</span>
                      <span>· {guide.reviewCount} {l.reviewCount}</span>
                    </div>
                  )}
                </div>

                {/* Booking confirmation panel — replaces slot picker / form after submit */}
                {bookingSummary ? (
                  <div className="p-5 text-center">
                    <div className={`mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl text-2xl shadow-md ${
                      bookingSummary.accepted > 0 ? "bg-gradient-to-br from-amber-400 to-orange-400" : "bg-gradient-to-br from-sky-400 to-cyan-400"
                    }`}>
                      {bookingSummary.accepted > 0 ? "⚡" : "📩"}
                    </div>
                    <p className="mb-1 font-bold text-stone-900">
                      {bookingSummary.accepted > 0 && bookingSummary.sent === 0 ? l.instantConfirmedTitle : l.requestedTitle}
                    </p>
                    <p className="mb-1 text-sm text-stone-500">
                      {l.bookingSentSummary.replace("{n}", String(bookingSummary.sent + bookingSummary.accepted))}
                    </p>
                    {bookingSummary.failed > 0 && (
                      <p className="mb-4 text-sm font-medium text-red-500">
                        {l.bookingFailSummary.replace("{n}", String(bookingSummary.failed))}
                      </p>
                    )}
                    <Link href="/traveler/bookings" className="btn-primary mt-3 block w-full py-3">
                      {l.viewBookingsBtn}
                    </Link>
                  </div>
                ) : !showForm ? (
                  <div className="p-5">
                    {slots.length === 0 ? (
                      /* No slots — direct booking */
                      <div>
                        <p className="mb-4 text-sm text-stone-400">{t.availability.guideEmpty}</p>
                        <button
                          onClick={() => { if (!getToken()) { router.push("/login"); return; } setShowForm(true); }}
                          className="btn-primary w-full py-3"
                        >
                          {guide.instantBooking ? l.instantBookBtn : l.bookBtn}
                        </button>
                        <p className="mt-2 text-center text-xs text-stone-400">{guide.instantBooking ? l.instantBookNote : l.bookNote}</p>
                      </div>
                    ) : (
                      /* Slot calendar */
                      <div>
                        <h3 className="mb-1 flex items-center gap-2 font-bold text-stone-900">
                          <CalendarIcon className="h-4 w-4 text-sky-500" /> {t.availability.sectionTitle}
                        </h3>
                        <p className="mb-3 text-xs text-stone-400">{t.availability.hint}</p>

                        {bookingCategorySelect && <div className="mb-3">{bookingCategorySelect}</div>}

                        <SlotCalendar
                          mode="traveler"
                          slots={slots}
                          selectedSlots={selectedSlots}
                          onToggleSlot={toggleSlot}
                          hourlyRate={guide.hourlyRate}
                          currency={guide.currency}
                        />

                        {selectedSlots.length > 0 && (
                          <div className="mt-3 rounded-2xl border border-sky-100 bg-sky-50/70 p-3">
                            <div className="mb-1 flex items-center justify-between">
                              <span className="text-xs font-bold text-sky-700">
                                {l.selectedCount.replace("{n}", String(selectedSlots.length))}
                              </span>
                              <span className="text-sm font-extrabold text-stone-900">
                                {totalSelectedPrice.toLocaleString()} {guide.currency}
                              </span>
                            </div>
                            <textarea
                              value={bookingMsg} onChange={(e) => setBookingMsg(e.target.value)}
                              placeholder={l.messagePlaceholder} rows={2}
                              className="input mt-1 resize-none text-sm"
                            />
                            <button
                              onClick={confirmSlots} disabled={submitting}
                              className="btn-primary mt-2 w-full py-3 disabled:opacity-60"
                            >
                              {submitting ? l.sending : l.confirmBookBtn.replace("{n}", String(selectedSlots.length))}
                            </button>
                          </div>
                        )}

                        <button
                          onClick={() => { if (!getToken()) { router.push("/login"); return; } setUseManual(true); setShowForm(true); }}
                          className="mt-2 w-full py-1 text-xs text-stone-400 transition-colors hover:text-sky-500"
                        >
                          {t.availability.orManual} →
                        </button>
                      </div>
                    )}
                  </div>
                ) : (
                  /* Booking form */
                  <form onSubmit={submitManualDays} className="flex flex-col gap-4 p-5">
                    <div className="flex items-center justify-between">
                      <h2 className="font-bold text-stone-900">{l.bookTitle}</h2>
                      <button type="button" onClick={() => { setShowForm(false); setUseManual(false); }}
                        className="flex h-8 w-8 items-center justify-center rounded-full text-stone-400 transition-colors hover:bg-stone-100 hover:text-stone-600">✕</button>
                    </div>

                    {/* 날짜별 시간 행 (일수 추가) */}
                    <div className="flex flex-col gap-2">
                      <label className="input-label">{l.daysLabel}</label>
                      {dayRows.map((r, i) => (
                        <div key={i} className="flex items-end gap-1.5 rounded-xl border border-stone-100 bg-stone-50/60 p-2">
                          <span className="pb-2 text-xs font-bold text-sky-500">{l.dayNo.replace("{n}", String(i + 1))}</span>
                          <div className="min-w-0 flex-1">
                            <input type="date" value={r.date} min={new Date().toISOString().slice(0, 10)}
                              onChange={(e) => updateRow(i, { date: e.target.value })} required className="input py-1.5 text-xs" />
                            <div className="mt-1 flex items-center gap-1">
                              <input type="time" step={3600} value={r.from} onChange={(e) => updateRow(i, { from: e.target.value })}
                                required className="input py-1.5 text-xs" />
                              <span className="text-xs text-stone-400">–</span>
                              <input type="time" step={3600} value={r.to} onChange={(e) => updateRow(i, { to: e.target.value })}
                                required className="input py-1.5 text-xs" />
                            </div>
                          </div>
                          {dayRows.length > 1 && (
                            <button type="button" onClick={() => removeDayRow(i)} aria-label={l.removeDay}
                              className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-stone-300 hover:bg-red-50 hover:text-red-500">✕</button>
                          )}
                        </div>
                      ))}
                      <button type="button" onClick={addDayRow}
                        className="w-full rounded-xl border border-dashed border-stone-300 py-2 text-xs font-medium text-stone-400 transition-colors hover:border-sky-300 hover:text-sky-500">
                        + {l.addDayBtn}
                      </button>
                    </div>

                    <div className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-3">
                      <span className="text-sm text-stone-600">{l.estPrice}</span>
                      <span className="font-extrabold text-stone-900">{totalManualPrice.toLocaleString()} {guide.currency}</span>
                    </div>
                    {bookingCategorySelect}
                    <div>
                      <label className="input-label">
                        {l.message} <span className="text-stone-400 normal-case font-normal">{l.messageOpt}</span>
                      </label>
                      <textarea placeholder={l.messagePlaceholder} value={bookingMsg}
                        onChange={(e) => setBookingMsg(e.target.value)} rows={3} className="input resize-none" />
                    </div>

                    {/* 취소 정책 안내 — 예약 전 신뢰 확보용 고정 문구 */}
                    <div className="rounded-xl border border-sky-100 bg-sky-50/60 px-4 py-3 text-xs leading-relaxed text-sky-700">
                      <p className="mb-0.5 font-bold">ℹ️ {l.cancellationPolicyTitle}</p>
                      <p>{l.cancellationPolicyBody}</p>
                    </div>

                    {bookingError && (
                      <p className="rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">{bookingError}</p>
                    )}
                    <button type="submit" disabled={submitting} className="btn-primary w-full py-3">
                      {submitting ? l.sending : (guide.instantBooking ? l.instantSendBtn : l.sendBtn)}
                    </button>
                  </form>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
