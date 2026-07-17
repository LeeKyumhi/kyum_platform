"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";

/** 동행 트랙 법적 안내 — 동행 목록·상세·예약 폼 공용. */
export default function TrackNotice() {
  const { t } = useLanguage();
  return (
    <div className="mb-5 rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      {t.tracks.companionNotice}{" "}
      <Link href="/guides" className="font-semibold underline">{t.tracks.companionNoticeLink}</Link>
    </div>
  );
}
