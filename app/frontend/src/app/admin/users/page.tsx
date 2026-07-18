"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type UserRow = {
  id: number; email: string; fullName: string; nickname: string;
  role: string; status: string; createdAt: string; suspendedReason: string | null;
};
type PageResult = { items: UserRow[]; page: number; totalPages: number; totalItems: number };

export default function AdminUsersPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [query, setQuery]   = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage]     = useState(0);
  const [data, setData]     = useState<PageResult | null>(null);
  const [error, setError]   = useState("");
  const [busy, setBusy]     = useState<number | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const qs = new URLSearchParams({ page: String(page), size: "20" });
      if (query) qs.set("query", query);
      if (status) qs.set("status", status);
      setData(await api<PageResult>(`/api/admin/users?${qs}`, { auth: true }));
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
  }, [page, query, status, t.common.error]);

  useEffect(() => { load(); }, [load]);

  async function onSuspend(u: UserRow) {
    if (!confirm(a.confirmSuspend)) return;
    const reason = prompt(a.suspendPrompt) ?? "";
    setBusy(u.id);
    try { await api(`/api/admin/users/${u.id}/suspend`, { method: "POST", body: { reason }, auth: true }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }
  async function onReactivate(u: UserRow) {
    setBusy(u.id);
    try { await api(`/api/admin/users/${u.id}/reactivate`, { method: "POST", auth: true }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }

  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-4 flex flex-wrap gap-2">
        <input className="input max-w-xs" placeholder={a.usersSearch} value={query}
          onChange={(e) => { setPage(0); setQuery(e.target.value); }} />
        <select className="input max-w-[8rem]" value={status}
          onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
          <option value="">{a.usersAll}</option>
          <option value="ACTIVE">{a.usersActive}</option>
          <option value="SUSPENDED">{a.usersSuspended}</option>
        </select>
      </div>
      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-stone-200 text-left text-stone-500">
            <tr>
              <th className="p-3">{a.colEmail}</th><th className="p-3">{a.colName}</th>
              <th className="p-3">{a.colRole}</th><th className="p-3">{a.colStatus}</th>
              <th className="p-3">{a.colAction}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((u) => (
              <tr key={u.id} className="border-b border-stone-100">
                <td className="p-3">{u.email}</td>
                <td className="p-3">{u.fullName}</td>
                <td className="p-3">{u.role}</td>
                <td className="p-3">
                  <span className={u.status === "SUSPENDED" ? "text-red-600" : "text-emerald-600"}>
                    {u.status === "SUSPENDED" ? a.usersSuspended : a.usersActive}
                  </span>
                </td>
                <td className="p-3">
                  {u.role === "ADMIN" ? <span className="text-stone-400">—</span>
                    : u.status === "SUSPENDED"
                      ? <button disabled={busy === u.id} onClick={() => onReactivate(u)}
                          className="rounded-lg bg-emerald-100 px-3 py-1 font-medium text-emerald-700">{a.reactivate}</button>
                      : <button disabled={busy === u.id} onClick={() => onSuspend(u)}
                          className="rounded-lg bg-red-100 px-3 py-1 font-medium text-red-700">{a.suspend}</button>}
                </td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr><td colSpan={5} className="p-6 text-center text-stone-400">{a.empty}</td></tr>
            )}
          </tbody>
        </table>
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
