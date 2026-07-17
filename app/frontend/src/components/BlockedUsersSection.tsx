"use client";

// 차단한 사용자 관리 — /profile 에서 사용. 목록 조회 + 차단 해제.
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type BlockedUser = { userId: number; name: string; handle: string };

export default function BlockedUsersSection() {
  const { t } = useLanguage();
  const s = t.safety;

  const [blocks, setBlocks] = useState<BlockedUser[] | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  useEffect(() => {
    api<BlockedUser[]>("/api/blocks", { auth: true })
      .then(setBlocks)
      .catch(() => setBlocks([]));
  }, []);

  async function unblock(userId: number) {
    setBusy(userId);
    try {
      await api(`/api/blocks/${userId}`, { method: "DELETE", auth: true });
      setBlocks((prev) => (prev ?? []).filter((b) => b.userId !== userId));
    } catch {
      /* ignore — 다음 로드에서 정정 */
    } finally {
      setBusy(null);
    }
  }

  // 로딩 중엔 아무것도 그리지 않는다 (섹션 깜빡임 방지)
  if (blocks === null) return null;

  return (
    <div className="card mb-5 p-5">
      <h2 className="text-sm font-bold text-stone-900">{s.blockedListTitle}</h2>
      <p className="mt-0.5 text-xs text-stone-500">{s.blockedListSub}</p>

      {blocks.length === 0 ? (
        <p className="mt-4 text-sm text-stone-400">{s.blockedListEmpty}</p>
      ) : (
        <ul className="mt-3 divide-y divide-stone-100">
          {blocks.map((b) => (
            <li key={b.userId} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-stone-800">{b.name}</p>
                {b.handle && <p className="truncate text-xs text-stone-400">@{b.handle}</p>}
              </div>
              <button
                type="button"
                onClick={() => unblock(b.userId)}
                disabled={busy === b.userId}
                className="flex-shrink-0 rounded-full border border-stone-300 px-3.5 py-1.5 text-xs font-semibold text-stone-700 transition-colors hover:border-stone-900 disabled:opacity-60"
              >
                {s.unblock}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
