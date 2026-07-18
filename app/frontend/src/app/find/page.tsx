"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { clearTrack } from "@/lib/track";

/**
 * /find — 예전 투트랙 허브. 이제 트랙을 지우고 홈으로 보내
 * TrackGate의 전체화면 세계 선택이 뜨게 한다 (세계 전환 딥링크로 유지).
 */
export default function FindPage() {
  const router = useRouter();
  useEffect(() => {
    clearTrack();
    router.replace("/");
  }, [router]);
  return null;
}
