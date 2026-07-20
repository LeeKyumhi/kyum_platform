"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";
import { PinIcon } from "@/components/icons";
import SaveButton from "@/components/SaveButton";
import { getToken } from "@/lib/api";
import { followCourse } from "@/lib/followCourse";

export type CourseCardData = {
  id: number; guideProfileId: number; title: string; description: string | null;
  city: string | null; durationHours: number; price: number; currency: string;
  maxPeople: number; imageUrl: string | null; guideName: string | null; guideAvatarUrl: string | null;
  waypoints?: { latitude: number | null; longitude: number | null }[];
};

/**
 * 투어 코스 카드 — guides/page.tsx "투어 코스" 탭 원본 마크업 그대로 추출.
 * 명소 상세(spots/[slug])의 지역 코스 추천에서도 재사용한다.
 */
export default function CourseCard({ course: c, saveCount }: { course: CourseCardData; saveCount?: number }) {
  const { t } = useLanguage();
  const router = useRouter();
  const [following, setFollowing] = useState(false);

  async function onFollowCourse(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (!getToken()) { router.push("/login"); return; }
    if (following) return;
    setFollowing(true);
    try {
      const tripId = await followCourse(c);
      router.push(`/trips/${tripId}`);
    } catch {
      alert(t.saved.followCourseFail);
      setFollowing(false);
    }
  }

  return (
    <Link href={`/guides/${c.guideProfileId}`} className="card-hover relative flex flex-col overflow-hidden">
      <SaveButton target={{ itemType: "COURSE", refId: c.id }} className="absolute right-3 top-3 z-10" />
      {c.imageUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={c.imageUrl} alt={c.title} className="aspect-video w-full object-cover" />
      ) : (
        <div className="flex aspect-video w-full items-center justify-center bg-gradient-to-br from-sky-400 to-cyan-400 text-4xl">
          🎫
        </div>
      )}
      <div className="flex flex-1 flex-col p-4">
        <h3 className="font-bold leading-snug text-stone-900">{c.title}</h3>
        {c.description && (
          <p className="mt-1 text-sm leading-relaxed text-stone-500 line-clamp-2">{c.description}</p>
        )}
        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-stone-400">
          {c.city && <span className="flex items-center gap-1"><PinIcon className="h-3.5 w-3.5 text-sky-400" /> {c.city}</span>}
          <span>⏱ {c.durationHours}{t.courses.hoursUnit}</span>
          <span>👥 {t.courses.upTo} {c.maxPeople}{t.courses.peopleUnit}</span>
          {saveCount != null && saveCount > 0 && (
            <span className="font-semibold text-rose-400">
              ♥ {t.saved.savedCount.replace("{n}", String(saveCount))}
            </span>
          )}
        </div>
        <div className="mt-3 flex items-center justify-between border-t border-stone-100 pt-3">
          <span className="flex items-center gap-2 text-xs text-stone-500">
            {c.guideAvatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={c.guideAvatarUrl} alt="" className="h-6 w-6 rounded-full object-cover" />
            ) : (
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-[10px] font-bold text-white">
                {(c.guideName ?? "?").slice(0, 1)}
              </span>
            )}
            {c.guideName}
          </span>
          <span className="text-sm">
            <span className="font-extrabold text-stone-900">{c.price.toLocaleString()}</span>
            <span className="text-xs font-medium text-stone-400"> {c.currency}/{t.courses.perPerson}</span>
          </span>
        </div>
        <button onClick={onFollowCourse} disabled={following}
          className="mt-3 w-full rounded-xl bg-stone-900 py-2 text-xs font-bold text-white transition-all hover:bg-stone-700 disabled:opacity-60">
          🗺️ {t.saved.followCourse}
        </button>
      </div>
    </Link>
  );
}
