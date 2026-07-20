"use client";

// 저장됨(위시리스트) — 프로필 `저장됨` 탭으로 흡수됨. 기존 링크·북마크 호환을 위해
// 얇은 리다이렉트만 유지한다. 실제 그리드는 profile/page.tsx + SavedGrid가 렌더.

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function SavedPage() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/profile?tab=saved");
  }, [router]);

  return null;
}
