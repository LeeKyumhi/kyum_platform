"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type PostRow = {
  id: number; authorUserId: number | null; authorName: string;
  content: string; imageUrl: string | null; hidden: boolean; createdAt: string;
};
type PageResult = { items: PostRow[]; page: number; totalPages: number; totalItems: number };

export default function AdminPostsPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [onlyHidden, setOnlyHidden] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResult | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const qs = new URLSearchParams({ hidden: String(onlyHidden), page: String(page), size: "20" });
      setData(await api<PageResult>(`/api/admin/posts?${qs}`, { auth: true }));
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
  }, [onlyHidden, page, t.common.error]);

  useEffect(() => { load(); }, [load]);

  async function act(p: PostRow, kind: "hide" | "unhide" | "delete") {
    if (kind === "hide" && !confirm(a.confirmHide)) return;
    if (kind === "delete" && !confirm(a.confirmDelete)) return;
    setBusy(p.id);
    try {
      if (kind === "delete") await api(`/api/admin/posts/${p.id}`, { method: "DELETE", auth: true });
      else await api(`/api/admin/posts/${p.id}/${kind}`, { method: "POST", auth: true });
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <label className="mb-4 flex items-center gap-2 text-sm">
        <input type="checkbox" checked={onlyHidden} onChange={(e) => { setPage(0); setOnlyHidden(e.target.checked); }} />
        {a.postsHidden}
      </label>
      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <div className="flex flex-col gap-3">
        {data?.items.map((p) => (
          <div key={p.id} className="card flex items-start justify-between gap-4 p-4">
            <div className="min-w-0">
              <div className="text-xs text-stone-400">{p.authorName}{p.hidden && <span className="ml-2 text-red-500">· {a.hidden}</span>}</div>
              <p className="mt-1 line-clamp-2 text-sm text-stone-800">{p.content}</p>
            </div>
            <div className="flex shrink-0 gap-2">
              {p.hidden
                ? <button disabled={busy === p.id} onClick={() => act(p, "unhide")} className="rounded-lg bg-stone-100 px-3 py-1 text-sm">{a.unhide}</button>
                : <button disabled={busy === p.id} onClick={() => act(p, "hide")} className="rounded-lg bg-amber-100 px-3 py-1 text-sm text-amber-700">{a.hide}</button>}
              <button disabled={busy === p.id} onClick={() => act(p, "delete")} className="rounded-lg bg-red-100 px-3 py-1 text-sm text-red-700">{a.del}</button>
            </div>
          </div>
        ))}
        {data && data.items.length === 0 && <p className="p-6 text-center text-stone-400">{a.empty}</p>}
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <button disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.prev}</button>
          <span>{page + 1} / {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.next}</button>
        </div>
      )}
    </div>
  );
}
