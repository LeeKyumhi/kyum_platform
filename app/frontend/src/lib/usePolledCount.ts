"use client";

// 사이드바 배지용 카운트 폴링 훅 — 30초 간격 + 경로 변경 시 즉시 refetch.
// enabled=false면 0으로 리셋하고 폴링하지 않는다 (역할별 배지 가드).

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { api } from "@/lib/api";

export function usePolledCount(url: string, enabled: boolean): number {
  const pathname = usePathname();
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!enabled) { setCount(0); return; }
    let cancelled = false;
    const load = () =>
      api<{ count: number }>(url, { auth: true })
        .then((r) => { if (!cancelled) setCount(r.count); })
        .catch(() => {});
    load();
    const timer = setInterval(load, 30000);
    return () => { cancelled = true; clearInterval(timer); };
  }, [url, enabled, pathname]);

  return count;
}
