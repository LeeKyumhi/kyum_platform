"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { localeOf } from "@/lib/i18n";
import CitySelect from "@/components/CitySelect";
import { useModalDismiss } from "@/lib/useModalDismiss";
import PostCard, { type FeedPost } from "@/components/PostCard";
import GuideCard, { type GuideCardData } from "@/components/GuideCard";
import CourseCard, { type CourseCardData } from "@/components/CourseCard";
import PageHeader from "@/components/PageHeader";
import EmptyState from "@/components/EmptyState";
import { fetchSaveCounts, SAVED_CHANGED_EVENT } from "@/lib/saved";
import {
  SearchIcon, ChatIcon,
  StarIcon, GlobeIcon,
} from "@/components/icons";

type Guide = GuideCardData & {
  matchScore: number | null; // 로그인한 여행자와의 궁합 (비로그인/근거 없음이면 null)
};
type SortKey = "default" | "popular" | "bookings" | "rating" | "match";
type PostSortKey = "recent" | "recommended" | "popular" | "views";
type TourCourse = CourseCardData;

function TabBar({ active, tabs, onChange }: {
  active: string;
  tabs: { key: string; label: string }[];
  onChange: (k: string) => void;
}) {
  return (
    <div className="seg-wrap mb-6">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          onClick={() => onChange(tab.key)}
          className={active === tab.key ? "seg-active" : "seg"}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

function BadgeExplainSheet({ onClose }: { onClose: () => void }) {
  const { t } = useLanguage();
  useModalDismiss(onClose);
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center"
         onClick={onClose}>
      <div className="w-full max-w-md rounded-t-2xl bg-white p-6 sm:rounded-2xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="mb-2 text-lg font-bold text-stone-900">✓ {t.tracks.badgeExplainTitle}</h3>
        <p className="text-sm leading-relaxed text-stone-600">{t.tracks.badgeExplainBody}</p>
        <button onClick={onClose} className="btn-secondary mt-4 w-full">{t.placeDetail.close}</button>
      </div>
    </div>
  );
}

export default function GuidesPage() {
  const { t, lang } = useLanguage();
  const l = t.guides;

  const [tab, setTab] = useState<"guides" | "posts" | "courses">("guides");

  // Guides tab state
  const [guides, setGuides]       = useState<Guide[]>([]);
  const [city, setCity]           = useState("");   // 선택된 도시 필터 (key)
  const [dateRange, setDateRange] = useState<{ from: string; to: string } | null>(null); // 검색바 체크인·체크아웃
  const [guidesLoading, setGuidesLoading] = useState(true);
  const [guidesError, setGuidesError]     = useState("");
  const [sortKey, setSortKey]     = useState<SortKey>("default");
  const [langFilter, setLangFilter] = useState("");
  const [badgeOpen, setBadgeOpen] = useState(false);   // 인증 배지 설명 시트

  // Posts tab state
  const [posts, setPosts]         = useState<FeedPost[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);
  const [postsLoaded, setPostsLoaded]   = useState(false);
  const [postLangFilter, setPostLangFilter] = useState("");
  const [postSortKey, setPostSortKey]       = useState<PostSortKey>("recent");

  // Courses tab state
  const [courses, setCourses]           = useState<TourCourse[]>([]);
  const [coursesLoading, setCoursesLoading] = useState(false);
  const [coursesLoaded, setCoursesLoaded]   = useState(false);
  const [courseSaveCounts, setCourseSaveCounts] = useState<Record<string, number>>({}); // 코스별 찜수 (공개 배치 조회)

  async function loadGuides(cityFilter = "", range: { from: string; to: string } | null = null) {
    setGuidesLoading(true); setGuidesError("");
    try {
      const q = new URLSearchParams();
      if (cityFilter) q.set("city", cityFilter);
      if (range) { q.set("availableFrom", range.from); q.set("availableTo", range.to); }
      q.set("category", "TOUR_GUIDE");
      q.set("lang", lang);
      // auth를 붙여야 내 관심사·MBTI 기준 궁합 점수(matchScore)가 내려온다
      const loaded = await api<Guide[]>(`/api/guides?${q.toString()}`, { auth: true });
      setGuides(loaded);
    } catch (err) {
      setGuidesError(err instanceof Error ? err.message : t.common.error);
    } finally { setGuidesLoading(false); }
  }

  async function loadPosts() {
    if (postsLoaded) return;
    setPostsLoading(true);
    try {
      // auth를 붙여야 로그인한 사용자의 isLiked(내가 좋아요 누른 상태)가 반영된다
      setPosts(await api<FeedPost[]>("/api/posts", { auth: true }));
      setPostsLoaded(true);
    } catch { /* silent */ }
    finally { setPostsLoading(false); }
  }

  async function loadCourses() {
    if (coursesLoaded) return;
    setCoursesLoading(true);
    try {
      const loaded = await api<TourCourse[]>("/api/courses");
      setCourses(loaded);
      setCoursesLoaded(true);
      // 찜수 뱃지 — 공개 배치 조회라 실패해도 목록 렌더링에는 영향 없음
      fetchSaveCounts("COURSE", loaded.map((c) => c.id)).then(setCourseSaveCounts);
    } catch { /* silent */ }
    finally { setCoursesLoading(false); }
  }

  // 마운트 시 검색바에서 넘어온 쿼리 파라미터(도시·체크인·체크아웃)를 초기 필터로 적용.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const cityParam = params.get("city") ?? "";
    const fromParam = params.get("from") ?? "";
    const toParam = params.get("to") ?? "";
    const range = fromParam && toParam ? { from: fromParam, to: toParam } : null;
    if (cityParam) setCity(cityParam);
    if (range) setDateRange(range);
    loadGuides(cityParam, range);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ♡ 토글 시 카드 찜수 뱃지도 즉석 갱신 — SaveButton이 쏘는 이벤트를 받아 현재 목록만 재집계
  useEffect(() => {
    const refresh = () => {
      if (courses.length) fetchSaveCounts("COURSE", courses.map((c) => c.id)).then(setCourseSaveCounts);
    };
    window.addEventListener(SAVED_CHANGED_EVENT, refresh);
    return () => window.removeEventListener(SAVED_CHANGED_EVENT, refresh);
  }, [courses]);

  function onTabChange(k: string) {
    setTab(k as "guides" | "posts" | "courses");
    if (k === "posts") loadPosts();
    if (k === "courses") loadCourses();
  }

  function onCityChange(key: string) { setCity(key); loadGuides(key, dateRange); }
  function clearSearch() { setCity(""); loadGuides("", dateRange); }
  function clearDateRange() { setDateRange(null); loadGuides(city, null); }

  function handleLikeChange(postId: number, liked: boolean, likeCount: number) {
    setPosts((prev) => prev.map((p) => p.id === postId ? { ...p, isLiked: liked, likeCount } : p));
  }

  const locale = localeOf(lang);

  // 로드된 가이드에서 사용 언어 목록 도출 (하드코딩 X).
  // 자유 입력 값이라 "English"/"english" 같은 대소문자 중복은 하나로 합친다 (첫 등장 표기 사용).
  const availableLanguages = useMemo(() => {
    const byKey = new Map<string, string>();
    guides.forEach((g) => g.languages.forEach((lv) => {
      const key = lv.language.trim().toLowerCase();
      if (key && !byKey.has(key)) byKey.set(key, lv.language.trim());
    }));
    return Array.from(byKey.values()).sort();
  }, [guides]);

  // 언어 필터 + 정렬 적용 (클라이언트)
  const visibleGuides = useMemo(() => {
    let list = guides;
    if (langFilter) {
      const key = langFilter.trim().toLowerCase();
      list = list.filter((g) => g.languages.some((lv) => lv.language.trim().toLowerCase() === key));
    }
    if (sortKey !== "default") {
      list = [...list].sort((a, b) => {
        if (sortKey === "popular")  return b.followerCount - a.followerCount;
        if (sortKey === "bookings") return b.bookingCount - a.bookingCount;
        if (sortKey === "rating")   return b.avgRating - a.avgRating || b.reviewCount - a.reviewCount;
        if (sortKey === "match")    return (b.matchScore ?? -1) - (a.matchScore ?? -1);
        return 0;
      });
    }
    return list;
  }, [guides, langFilter, sortKey]);

  // 게시글에 등장하는 가이드 언어 목록 도출
  const availablePostLanguages = useMemo(() => {
    const set = new Set<string>();
    posts.forEach((p) => p.guideLanguages.forEach((lng) => set.add(lng)));
    return Array.from(set).sort();
  }, [posts]);

  // 언어 필터 + 정렬 적용 (클라이언트)
  //  · 추천순 = 좋아요*2 + 댓글*2 + 조회수 (참여도 블렌드)
  //  · 인기순 = 좋아요 · 조회순 = 조회수 · 최신순 = 작성일
  const visiblePosts = useMemo(() => {
    let list = posts;
    if (postLangFilter) {
      list = list.filter((p) => p.guideLanguages.includes(postLangFilter));
    }
    if (postSortKey !== "recent") {
      const score = (p: FeedPost) =>
        postSortKey === "popular" ? p.likeCount
        : postSortKey === "views" ? p.viewCount
        : p.likeCount * 2 + p.commentCount * 2 + p.viewCount; // recommended
      list = [...list].sort((a, b) => score(b) - score(a) || +new Date(b.createdAt) - +new Date(a.createdAt));
    }
    return list;
  }, [posts, postLangFilter, postSortKey]);

  // 궁합 점수가 하나라도 내려온 경우에만 궁합순 정렬을 노출한다 (비로그인/미입력이면 의미 없음)
  const hasMatchScores = guides.some((g) => g.matchScore != null);
  const sortOptions: { key: SortKey; label: string }[] = [
    { key: "default",  label: l.sortDefault },
    ...(hasMatchScores ? [{ key: "match" as SortKey, label: l.sortMatch }] : []),
    { key: "popular",  label: l.sortPopular },
    { key: "bookings", label: l.sortBookings },
    { key: "rating",   label: l.sortRating },
  ];

  return (
    <main className="page px-4">
      <div className="container-lg">

        {/* Page heading */}
        <PageHeader
          title={
            <>
              {l.title}{" "}
              <button onClick={() => setBadgeOpen(true)}
                className="badge-emerald align-middle text-xs">✓ {t.tracks.badgeExplainTitle}</button>
            </>
          }
          subtitle={l.tourTrackSub}
        />

        <TabBar
          active={tab}
          tabs={[
            { key: "guides",  label: l.tabGuides },
            { key: "courses", label: t.courses.tabTitle },
            { key: "posts",   label: l.tabPosts  },
          ]}
          onChange={onTabChange}
        />

        {/* ── 가이드 탭 ── */}
        {tab === "guides" && (
          <>
            {/* City search card */}
            <div className="card mb-5 p-4">
              <CitySelect value={city} onChange={(key) => onCityChange(key)} />
              {(city || dateRange) && (
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <span className="text-sm text-stone-500">{l.filterLabel}</span>
                  {city && (
                    <span className="badge-indigo py-1 pl-3 pr-2 text-sm">
                      {city}
                      <button onClick={clearSearch} className="ml-1 rounded-full p-0.5 opacity-60 transition-opacity hover:opacity-100">✕</button>
                    </span>
                  )}
                  {dateRange && (
                    <span className="badge-indigo py-1 pl-3 pr-2 text-sm">
                      📅 {t.courses.dateChip}: {dateRange.from} ~ {dateRange.to}
                      <button onClick={clearDateRange} className="ml-1 rounded-full p-0.5 opacity-60 transition-opacity hover:opacity-100">✕</button>
                    </span>
                  )}
                </div>
              )}
            </div>

            {/* Sort chips */}
            <div className="shelf -mx-4 mb-1 px-4 !pb-3">
              {sortOptions.map((o) => (
                <button
                  key={o.key}
                  onClick={() => setSortKey(o.key)}
                  className={sortKey === o.key ? "chip-active" : "chip"}
                >
                  {o.key === "rating" && <StarIcon className="h-3.5 w-3.5 text-amber-400" />}
                  {o.label}
                </button>
              ))}
            </div>

            {/* Language chips */}
            {availableLanguages.length > 0 && (
              <div className="shelf -mx-4 mb-4 px-4 !pb-3">
                <button
                  onClick={() => setLangFilter("")}
                  className={langFilter === "" ? "chip-active" : "chip"}
                >
                  <GlobeIcon className="h-3.5 w-3.5" /> {l.langAll}
                </button>
                {availableLanguages.map((lng) => (
                  <button
                    key={lng}
                    onClick={() => setLangFilter(lng)}
                    className={langFilter === lng ? "chip-active" : "chip"}
                  >
                    {lng}
                  </button>
                ))}
              </div>
            )}

            {guidesLoading && (
              <div className="grid gap-4 sm:grid-cols-2">
                {[...Array(6)].map((_, i) => (
                  <div key={i} className="card animate-pulse p-5">
                    <div className="flex items-start gap-4">
                      <div className="h-16 w-16 flex-shrink-0 rounded-2xl bg-stone-100" />
                      <div className="flex-1 pt-1">
                        <div className="mb-2 h-4 w-32 rounded bg-stone-100" />
                        <div className="h-3 w-20 rounded bg-stone-100" />
                      </div>
                    </div>
                    <div className="mt-4 h-3 w-full rounded bg-stone-100" />
                    <div className="mt-2 h-3 w-2/3 rounded bg-stone-100" />
                    <div className="mt-4 flex items-center justify-between border-t border-stone-50 pt-3">
                      <div className="h-4 w-16 rounded bg-stone-100" />
                      <div className="h-5 w-24 rounded bg-stone-100" />
                    </div>
                  </div>
                ))}
              </div>
            )}
            {guidesError && <p className="text-red-600 text-sm">{guidesError}</p>}
            {!guidesLoading && !guidesError && visibleGuides.length === 0 && (
              <EmptyState
                icon={<SearchIcon className="h-6 w-6 text-white" />}
                message={l.empty}
                action={(city || langFilter) ? (
                  <button
                    onClick={() => { setLangFilter(""); clearSearch(); }}
                    className="text-sm font-semibold text-sky-500 hover:underline"
                  >
                    {l.showAll}
                  </button>
                ) : undefined}
              />
            )}
            {!guidesLoading && (
              <div className="animate-fade-up grid gap-4 sm:grid-cols-2">
                {visibleGuides.map((g) => (
                  <GuideCard key={g.id} guide={g} />
                ))}
              </div>
            )}
          </>
        )}

        {/* ── 게시글 탭 ── */}
        {tab === "posts" && (
          <>
            {/* 가이드 언어 필터 + 정렬 (드롭다운) */}
            {!postsLoading && posts.length > 0 && (
              <div className="max-w-[468px] mx-auto mb-4 flex flex-wrap items-center gap-2">
                <select
                  value={postLangFilter}
                  onChange={(e) => setPostLangFilter(e.target.value)}
                  className="rounded-full border border-stone-200 bg-white px-4 py-2 text-sm font-medium text-stone-700 focus:outline-none focus:ring-2 focus:ring-sky-100"
                >
                  <option value="">{l.langAll}</option>
                  {availablePostLanguages.map((lng) => (
                    <option key={lng} value={lng}>{lng}</option>
                  ))}
                </select>
                <select
                  value={postSortKey}
                  onChange={(e) => setPostSortKey(e.target.value as PostSortKey)}
                  className="rounded-full border border-stone-200 bg-white px-4 py-2 text-sm font-medium text-stone-700 focus:outline-none focus:ring-2 focus:ring-sky-100"
                >
                  <option value="recent">{l.postSortRecent}</option>
                  <option value="recommended">{l.postSortRecommended}</option>
                  <option value="popular">{l.postSortPopular}</option>
                  <option value="views">{l.postSortViews}</option>
                </select>
              </div>
            )}

            {postsLoading && (
              <div className="max-w-[468px] mx-auto flex flex-col gap-4">
                {[...Array(3)].map((_, i) => (
                  <div key={i} className="card animate-pulse overflow-hidden">
                    <div className="flex items-center gap-3 p-4">
                      <div className="w-10 h-10 rounded-full bg-stone-100 flex-shrink-0" />
                      <div className="flex-1">
                        <div className="h-3.5 bg-stone-100 rounded w-28 mb-1.5" />
                        <div className="h-3 bg-stone-100 rounded w-20" />
                      </div>
                    </div>
                    <div className="w-full aspect-square bg-stone-100" />
                    <div className="p-4">
                      <div className="h-3 bg-stone-100 rounded w-full mb-1.5" />
                      <div className="h-3 bg-stone-100 rounded w-4/5" />
                    </div>
                  </div>
                ))}
              </div>
            )}

            {!postsLoading && posts.length === 0 && (
              <EmptyState
                icon={<ChatIcon className="h-6 w-6 text-white" />}
                message={l.feedEmpty}
              />
            )}

            {!postsLoading && posts.length > 0 && visiblePosts.length === 0 && (
              <EmptyState
                icon={<GlobeIcon className="h-6 w-6 text-white" />}
                message={l.feedEmpty}
                action={
                  <button onClick={() => setPostLangFilter("")} className="text-sm font-semibold text-sky-500 hover:underline">{l.langAll}</button>
                }
              />
            )}

            {!postsLoading && visiblePosts.length > 0 && (
              <div className="animate-fade-up max-w-[468px] mx-auto flex flex-col gap-3 sm:gap-5">
                {visiblePosts.map((p) => (
                  <PostCard key={p.id} post={p} onLikeChange={handleLikeChange} />
                ))}
              </div>
            )}
          </>
        )}

        {/* ── 투어 코스 탭 ── */}
        {tab === "courses" && (
          <>
            {coursesLoading && (
              <div className="grid gap-4 sm:grid-cols-2">
                {[1, 2, 3, 4].map((i) => (
                  <div key={i} className="card animate-pulse overflow-hidden">
                    <div className="aspect-video w-full bg-stone-100" />
                    <div className="space-y-2 p-4">
                      <div className="h-4 w-3/4 rounded bg-stone-100" />
                      <div className="h-3 w-1/2 rounded bg-stone-100" />
                    </div>
                  </div>
                ))}
              </div>
            )}

            {!coursesLoading && coursesLoaded && courses.length === 0 && (
              <EmptyState icon="🎫" message={t.courses.empty} />
            )}

            {!coursesLoading && courses.length > 0 && (
              <div className="animate-fade-up grid gap-4 sm:grid-cols-2">
                {courses.map((c) => (
                  <CourseCard key={c.id} course={c} saveCount={courseSaveCounts[String(c.id)] ?? 0} />
                ))}
              </div>
            )}
          </>
        )}

      </div>

      {badgeOpen && <BadgeExplainSheet onClose={() => setBadgeOpen(false)} />}
    </main>
  );
}
