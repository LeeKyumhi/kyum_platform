"use client";

// 카드 ♡ 찜 버튼 — Link 카드 위 오버레이용. 낙관적 토글 + 실패 시 롤백.
// 초기 상태는 lib/saved.ts의 공유 캐시(loadSavedIds)에서 읽고,
// 어디서든 저장/해제되면 SAVED_CHANGED_EVENT로 전부 재동기화된다.

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { HeartIcon } from "@/components/icons";
import {
  loadSavedIds, saveItem, unsaveItem, SAVED_CHANGED_EVENT, type SaveTarget,
} from "@/lib/saved";

export default function SaveButton({ target, className = "" }: { target: SaveTarget; className?: string }) {
  const router = useRouter();
  const { t } = useLanguage();
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const sync = () =>
      loadSavedIds().then((ids) => {
        if (cancelled) return;
        // PLACE를 먼저 체크해야 TS가 나머지 분기에서 GUIDE|COURSE로 좁혀준다
        // (itemType이 "GUIDE"|"COURSE" 유니언인 멤버는 부정 비교로는 좁혀지지 않음)
        if (target.itemType === "PLACE") setSaved(ids.placeRefs.includes(target.place.ref));
        else if (target.itemType === "GUIDE") setSaved(ids.guideIds.includes(target.refId));
        else setSaved(ids.courseIds.includes(target.refId));
      });
    sync();
    window.addEventListener(SAVED_CHANGED_EVENT, sync);
    return () => {
      cancelled = true;
      window.removeEventListener(SAVED_CHANGED_EVENT, sync);
    };
    // target은 카드별로 고정 — 재구독 불필요
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function toggle(e: React.MouseEvent) {
    // 카드 전체가 Link인 곳에 얹히므로 내비게이션 차단 (following 페이지 onUnfollow 패턴)
    e.preventDefault();
    e.stopPropagation();
    if (!getToken()) { router.push("/login"); return; }
    if (busy) return;
    const next = !saved;
    setSaved(next);          // 낙관적 토글
    setBusy(true);
    try {
      if (next) await saveItem(target);
      else await unsaveItem(target);
    } catch {
      setSaved(!next);       // 실패 시 롤백
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      onClick={toggle}
      aria-pressed={saved}
      aria-label={saved ? t.saved.savedBtn : t.saved.saveBtn}
      className={`flex h-8 w-8 items-center justify-center rounded-full bg-white/90 shadow-sm ring-1 ring-stone-200/60 backdrop-blur transition-transform hover:scale-110 active:scale-95 ${className}`}
    >
      <HeartIcon className={`h-4 w-4 ${saved ? "text-rose-500" : "text-stone-400"}`} filled={saved} />
    </button>
  );
}
