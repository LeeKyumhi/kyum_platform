"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type BookingRow = {
  id: number; travelerId: number; travelerName: string;
  guideProfileId: number; guideName: string; status: string;
  totalPrice: number | null; currency: string | null; startAt: string; createdAt: string;
};
type PageResult = { items: BookingRow[]; page: number; totalPages: number; totalItems: number };

export default function AdminBookingsPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResult | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const qs = new URLSearchParams({ page: String(page), size: "20" });
      if (status) qs.set("status", status);
      setData(await api<PageResult>(`/api/admin/bookings?${qs}`, { auth: true }));
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
  }, [status, page, t.common.error]);

  useEffect(() => { load(); }, [load]);

  async function onCancel(b: BookingRow) {
    if (!confirm(a.confirmCancel)) return;
    setBusy(b.id);
    try { await api(`/api/admin/bookings/${b.id}/cancel`, { method: "POST", auth: true }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }

  const cancellable = (s: string) => s === "REQUESTED" || s === "ACCEPTED";

  return (
    <div className="mx-auto max-w-5xl">
      <select className="input mb-4 max-w-[10rem]" value={status}
        onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
        <option value="">{a.bkAll}</option>
        <option value="REQUESTED">{a.bkRequested}</option>
        <option value="ACCEPTED">{a.bkAccepted}</option>
        <option value="COMPLETED">{a.bkCompleted}</option>
        <option value="CANCELLED">{a.bkCancelled}</option>
        <option value="REJECTED">{a.bkRejected}</option>
      </select>
      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-stone-200 text-left text-stone-500">
            <tr>
              <th className="p-3">{a.colTraveler}</th><th className="p-3">{a.colGuide}</th>
              <th className="p-3">{a.colStatus}</th><th className="p-3">{a.colPrice}</th>
              <th className="p-3">{a.colCancel}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((b) => (
              <tr key={b.id} className="border-b border-stone-100">
                <td className="p-3">{b.travelerName}</td>
                <td className="p-3">{b.guideName}</td>
                <td className="p-3">{b.status}</td>
                <td className="p-3">{b.totalPrice != null ? `${b.totalPrice} ${b.currency ?? ""}` : "—"}</td>
                <td className="p-3">
                  {cancellable(b.status)
                    ? <button disabled={busy === b.id} onClick={() => onCancel(b)} className="rounded-lg bg-red-100 px-3 py-1 font-medium text-red-700">{a.colCancel}</button>
                    : <span className="text-stone-400">—</span>}
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
