"use client";

// 저장됨(위시리스트) — 가이드/코스/장소 3탭. following 페이지의 레이아웃·스켈레톤·빈상태 패턴 재사용.
// SaveButton으로 해제하면 SAVED_CHANGED_EVENT가 오므로 목록을 재조회한다.

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { SAVED_CHANGED_EVENT } from "@/lib/saved";
import CourseCard, { type CourseCardData } from "@/components/CourseCard";
import SaveButton from "@/components/SaveButton";
import PageHeader from "@/components/PageHeader";
import EmptyState from "@/components/EmptyState";
import { PinIcon } from "@/components/icons";

type SavedGuide = {
  guideProfileId: number; guideName: string; guideAvatarUrl: string | null;
  headline: string; region: string; mbti: string | null; interests: string[];
};
type SavedPlace = {
  placeRef: string; name: string; category: string | null; address: string | null;
  latitude: number | null; longitude: number | null; image: string | null; createdAt: string;
};
type SavedList = { guides: SavedGuide[]; courses: CourseCardData[]; places: SavedPlace[] };

type Tab = "guides" | "courses" | "places";

export default function SavedPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const ls = t.saved;

  const [tab, setTab] = useState<Tab>("guides");
  const [list, setList] = useState<SavedList | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    const load = () =>
      api<SavedList>("/api/saved", { auth: true })
        .then(setList)
        .catch(() => setList({ guides: [], courses: [], places: [] }))
        .finally(() => setLoading(false));
    load();
    window.addEventListener(SAVED_CHANGED_EVENT, load);
    return () => window.removeEventListener(SAVED_CHANGED_EVENT, load);
  }, [router]);

  const tabs: { key: Tab; label: string; count: number }[] = [
    { key: "guides",  label: ls.tabGuides,  count: list?.guides.length ?? 0 },
    { key: "courses", label: ls.tabCourses, count: list?.courses.length ?? 0 },
    { key: "places",  label: ls.tabPlaces,  count: list?.places.length ?? 0 },
  ];
  const activeEmpty =
    !loading && list !== null &&
    ((tab === "guides" && list.guides.length === 0) ||
     (tab === "courses" && list.courses.length === 0) ||
     (tab === "places" && list.places.length === 0));

  return (
    <main className="page px-4">
      <div className="container-sm">
        <PageHeader back={{ href: "/", label: t.common.back }} title={ls.title} />

        {/* 탭 */}
        <div className="mb-5 flex gap-2 overflow-x-auto pb-2">
          {tabs.map((tb) => (
            <button key={tb.key} onClick={() => setTab(tb.key)}
              className={tab === tb.key ? "chip-active" : "chip"}>
              {tb.label}{tb.count > 0 ? ` ${tb.count}` : ""}
            </button>
          ))}
        </div>

        {loading && (
          <div className="flex flex-col gap-3">
            {[...Array(4)].map((_, i) => <div key={i} className="card h-24 animate-pulse p-4" />)}
          </div>
        )}

        {activeEmpty && (
          <EmptyState icon="🤍" message={ls.empty}
            action={<Link href={tab === "places" ? "/explore" : "/guides"} className="btn-primary text-sm">{ls.browseCta}</Link>} />
        )}

        {/* 가이드 탭 — following 페이지 행 스타일 */}
        {!loading && tab === "guides" && list && list.guides.length > 0 && (
          <div className="flex flex-col gap-3">
            {list.guides.map((g) => (
              <Link key={g.guideProfileId} href={`/guides/${g.guideProfileId}`}
                className="card-hover relative flex items-center gap-4 p-4">
                {g.guideAvatarUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={g.guideAvatarUrl} alt={g.guideName}
                    className="h-12 w-12 flex-shrink-0 rounded-full object-cover shadow-sm ring-2 ring-white" />
                ) : (
                  <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 font-bold text-white shadow-sm ring-2 ring-white">
                    {g.guideName.slice(0, 1).toUpperCase()}
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="mb-0.5 flex items-center gap-2">
                    <span className="font-semibold text-stone-900">{g.guideName}</span>
                    {g.mbti && <span className="rounded-md bg-violet-100 px-1.5 py-0.5 text-xs font-bold text-violet-700">{g.mbti}</span>}
                  </div>
                  <p className="flex items-center gap-1 truncate text-xs text-stone-500">
                    <PinIcon className="h-3.5 w-3.5 flex-shrink-0" /> {g.region} · {g.headline}
                  </p>
                </div>
                <SaveButton target={{ itemType: "GUIDE", refId: g.guideProfileId }} className="flex-shrink-0" />
              </Link>
            ))}
          </div>
        )}

        {/* 코스 탭 — CourseCard 그대로 (♡는 카드에 이미 부착됨) */}
        {!loading && tab === "courses" && list && list.courses.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2">
            {list.courses.map((c) => <CourseCard key={c.id} course={c} />)}
          </div>
        )}

        {/* 장소 탭 — 스냅샷 렌더 */}
        {!loading && tab === "places" && list && list.places.length > 0 && (
          <div className="flex flex-col gap-3">
            {list.places.map((p) => (
              <div key={p.placeRef} className="card relative flex items-start gap-3 p-4">
                {p.image ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={p.image} alt={p.name} className="h-11 w-11 flex-shrink-0 rounded-xl object-cover shadow-sm" />
                ) : (
                  <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-sky-400 to-cyan-400 text-lg shadow-sm">📍</span>
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold text-stone-900">{p.name}</p>
                  {p.category && <p className="mt-0.5 truncate text-xs text-stone-400">{p.category}</p>}
                  {p.address && (
                    <p className="mt-1 flex items-center gap-1 text-sm text-stone-500">
                      <PinIcon className="h-3.5 w-3.5 flex-shrink-0 text-sky-400" />
                      <span className="truncate">{p.address}</span>
                    </p>
                  )}
                </div>
                <SaveButton
                  target={{ itemType: "PLACE", place: {
                    ref: p.placeRef, name: p.name, category: p.category,
                    address: p.address, lat: p.latitude, lng: p.longitude, image: p.image,
                  } }}
                  className="flex-shrink-0"
                />
              </div>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
