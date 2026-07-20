"use client";

// 저장됨 인스타 그리드 — 코스·장소를 정사각 썸네일로. profile/page.tsx `저장됨` 탭이 렌더한다
// (`/saved`는 프로필 저장됨 탭으로 가는 얇은 리다이렉트일 뿐, 이 컴포넌트를 직접 쓰지 않음).
// guide/page.tsx 게시글 미리보기 그리드(grid-cols-3 gap-1 + aspect-square) 패턴을 그대로 따른다.
// ♡ 해제는 SaveButton이 처리하고, SAVED_CHANGED_EVENT로 재조회한다.

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { SAVED_CHANGED_EVENT } from "@/lib/saved";
import type { CourseCardData } from "@/components/CourseCard";
import SaveButton from "@/components/SaveButton";
import EmptyState from "@/components/EmptyState";
import { categoryIcon } from "@/components/TimetableBuilder";

type SavedPlace = {
  placeRef: string; name: string; category: string | null; address: string | null;
  latitude: number | null; longitude: number | null; image: string | null; createdAt: string;
};
type SavedList = { courses: CourseCardData[]; places: SavedPlace[] };

const EMPTY: SavedList = { courses: [], places: [] };

export default function SavedGrid() {
  const { t } = useLanguage();
  const [list, setList] = useState<SavedList | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getToken()) { setList(EMPTY); setLoading(false); return; }
    const load = () =>
      api<SavedList>("/api/saved", { auth: true })
        .then(setList)
        .catch(() => setList(EMPTY))
        .finally(() => setLoading(false));
    load();
    window.addEventListener(SAVED_CHANGED_EVENT, load);
    return () => window.removeEventListener(SAVED_CHANGED_EVENT, load);
  }, []);

  if (loading) {
    return (
      <div className="grid grid-cols-3 gap-1">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <div key={i} className="aspect-square animate-pulse rounded-lg bg-stone-100" />
        ))}
      </div>
    );
  }

  const isEmpty = !list || (list.courses.length === 0 && list.places.length === 0);
  if (isEmpty) {
    return (
      <EmptyState
        icon="🤍"
        message={t.profilePage.savedEmpty}
        action={<Link href="/explore" className="btn-primary text-sm">{t.saved.browseCta}</Link>}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {list.courses.length > 0 && (
        <div>
          <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-stone-400">{t.saved.tabCourses}</h3>
          <div className="grid grid-cols-3 gap-1 overflow-hidden rounded-xl">
            {list.courses.map((c) => (
              <Link
                key={c.id}
                href={`/guides/${c.guideProfileId}`}
                className="relative block aspect-square overflow-hidden bg-stone-50"
              >
                <SaveButton target={{ itemType: "COURSE", refId: c.id }} className="absolute right-2 top-2 z-10" />
                {c.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={c.imageUrl} alt={c.title} className="h-full w-full object-cover" />
                ) : (
                  <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-sky-400 to-cyan-400 text-3xl">
                    🎫
                  </div>
                )}
                <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent p-2 pt-6">
                  <p className="line-clamp-2 text-xs font-semibold text-white">{c.title}</p>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}

      {list.places.length > 0 && (
        <div>
          <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-stone-400">{t.saved.tabPlaces}</h3>
          <div className="grid grid-cols-3 gap-1 overflow-hidden rounded-xl">
            {list.places.map((p) => (
              <div key={p.placeRef} className="relative aspect-square overflow-hidden bg-stone-50">
                <SaveButton
                  target={{
                    itemType: "PLACE",
                    place: {
                      ref: p.placeRef, name: p.name, category: p.category,
                      address: p.address, lat: p.latitude, lng: p.longitude, image: p.image,
                    },
                  }}
                  className="absolute right-2 top-2 z-10"
                />
                {p.image ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={p.image} alt={p.name} className="h-full w-full object-cover" />
                ) : (
                  <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-sky-400 to-cyan-400 text-3xl">
                    {categoryIcon(p.name, p.category, false)}
                  </div>
                )}
                <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent p-2 pt-6">
                  <p className="truncate text-xs font-semibold text-white">{p.name}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
