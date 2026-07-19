"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";
import { StarIcon, PinIcon, CalendarIcon, UsersIcon, HeartIcon } from "@/components/icons";
import SaveButton from "@/components/SaveButton";

type Language = { language: string; level: string };

export type GuideCardData = {
  id: number; guideName: string; headline: string; hourlyRate: number; currency: string;
  region: string; city: string | null; avatarUrl: string | null; avgRating: number; reviewCount: number;
  followerCount: number; bookingCount: number; mbti: string | null; interests: string[]; languages: Language[];
  gender: string | null;
  instantBooking?: boolean;
  matchScore?: number | null;
  verificationStatus?: string;
  serviceCategories?: string[];
};

function GuideAvatar({ src, name }: { src: string | null; name: string }) {
  if (src) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={src} alt={name} className="w-16 h-16 rounded-2xl text-xl object-cover ring-2 ring-white shadow-md flex-shrink-0" />;
  }
  return (
    <div className="w-16 h-16 rounded-2xl text-xl bg-gradient-to-br from-sky-400 to-cyan-400 flex items-center justify-center text-white font-bold ring-2 ring-white shadow-md flex-shrink-0">
      {name.slice(0, 1).toUpperCase()}
    </div>
  );
}

/**
 * 가이드 목록/유사가이드 추천에서 공용으로 쓰는 카드.
 * guides/page.tsx 원본 마크업 그대로 추출 — 궁합 배지·⚡즉시예약 배지 포함.
 */
export default function GuideCard({ guide: g, href, track = "tour" }:
  { guide: GuideCardData; href?: string; track?: "tour" | "companion" }) {
  const { t } = useLanguage();
  const l = t.guides;
  const link = href ?? (track === "companion" ? `/companions/${g.id}` : `/guides/${g.id}`);
  const visibleCategories = (g.serviceCategories ?? []).filter(
    (k) => track === "tour" || k !== "TOUR_GUIDE");

  return (
    <Link href={link} className="card-hover relative flex flex-col p-5">
      <SaveButton target={{ itemType: "GUIDE", refId: g.id }} className="absolute right-4 top-4 z-10" />
      {/* Header: avatar + name + region */}
      <div className="flex items-start gap-4">
        <GuideAvatar src={g.avatarUrl} name={g.guideName} />
        <div className="min-w-0 flex-1 pt-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-base font-bold text-stone-900">{g.guideName}</h2>
            {g.verificationStatus === "VERIFIED" && (
              <span className="badge-emerald text-[11px]">✓ {t.guideDetail.verified}</span>
            )}
            {track === "companion" && (
              <span className="rounded-md bg-sky-100 px-2 py-0.5 text-[11px] font-bold text-sky-700">
                🤝 {t.companions.partnerBadge}
              </span>
            )}
            {g.instantBooking && (
              <span className="rounded-md bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-700">
                ⚡ {t.guideDetail.instantBadge}
              </span>
            )}
            {g.mbti && (
              <span className="rounded-md bg-violet-100 px-2 py-0.5 text-xs font-bold text-violet-700">{g.mbti}</span>
            )}
            {g.gender && (
              <span className="badge-gray text-[11px]">
                {g.gender === "male" ? `♂ ${t.personality.genderMale}`
                  : g.gender === "female" ? `♀ ${t.personality.genderFemale}`
                  : t.personality.genderOther}
              </span>
            )}
          </div>
          <p className="mt-1 flex items-center gap-1 text-xs text-stone-500">
            <PinIcon className="h-3.5 w-3.5 flex-shrink-0" /> {g.city ?? g.region}
          </p>
          <div className="mt-1.5 flex items-center gap-3 text-xs text-stone-400">
            {g.bookingCount > 0 && (
              <span className="flex items-center gap-1"><CalendarIcon className="h-3.5 w-3.5" /> {g.bookingCount}</span>
            )}
            {g.followerCount > 0 && (
              <span className="flex items-center gap-1"><UsersIcon className="h-3.5 w-3.5" /> {g.followerCount}</span>
            )}
          </div>
        </div>
      </div>

      {/* 궁합 배지 — 내 관심사·MBTI 기준 (로그인 + 프로필 입력 시에만 표시) */}
      {g.matchScore != null && (
        <span className="mt-3 inline-flex w-fit items-center gap-1.5 rounded-full bg-rose-50 px-2.5 py-1 text-xs font-bold text-rose-500 ring-1 ring-rose-100">
          <HeartIcon className="h-3.5 w-3.5" filled />
          {l.matchBadge.replace("{n}", String(g.matchScore))}
        </span>
      )}

      {/* 제공 서비스 태그 (관광 = emerald, 비관광 = gray) */}
      {visibleCategories.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {visibleCategories.map((k) => (
            <span key={k} className={`text-[11px] ${k === "TOUR_GUIDE" ? "badge-emerald" : "badge-gray"}`}>
              {(t.serviceCategories as Record<string, string>)[k] ?? k}
            </span>
          ))}
        </div>
      )}

      {/* Headline */}
      <p className="mt-3 text-sm leading-relaxed text-stone-600 line-clamp-2">{g.headline}</p>

      {/* Language + interest badges */}
      <div className="mt-3 flex flex-wrap gap-1.5">
        {g.languages.slice(0, 2).map((lv, i) => (
          <span key={i} className="badge-indigo">
            {lv.language}
            <span className="opacity-60"> · {t.level[lv.level as keyof typeof t.level] ?? lv.level}</span>
          </span>
        ))}
        {g.interests.slice(0, 2).map((k) => (
          <span key={k} className="badge-gray">
            {t.interests[k as keyof typeof t.interests] ?? k}
          </span>
        ))}
      </div>

      {/* Footer: rating + price */}
      <div className="mt-auto pt-4">
        <div className="flex items-end justify-between border-t border-stone-100 pt-3.5">
          {g.reviewCount > 0 ? (
            <span className="flex items-center gap-1 text-sm">
              <StarIcon className="h-4 w-4 text-amber-400" />
              <span className="font-bold text-stone-900">{g.avgRating.toFixed(1)}</span>
              <span className="text-stone-400">({g.reviewCount})</span>
            </span>
          ) : <span />}
          <span className="whitespace-nowrap text-right">
            <span className="text-lg font-extrabold text-stone-900">{g.hourlyRate.toLocaleString()}</span>
            <span className="text-xs font-medium text-stone-400"> {g.currency}/{l.perHour}</span>
          </span>
        </div>
      </div>
    </Link>
  );
}
