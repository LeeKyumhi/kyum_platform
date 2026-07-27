"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";

type SettlementRow = {
  id: number;
  bookingId: number;
  guideProfileId: number;
  grossAmount: number;
  commissionAmount: number;
  netAmount: number;
  status: string; // "PENDING" | "PAID_OUT"
  createdAt: string;
  paidOutAt: string | null;
  adminMemo: string | null;
};

export default function AdminSettlementsPage() {
  const [rows, setRows] = useState<SettlementRow[] | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await api<SettlementRow[]>("/api/admin/settlements", { auth: true }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "요청에 실패했습니다.");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function onPayout(row: SettlementRow) {
    const memo = prompt("이체 참조 메모(선택)") ?? "";
    setBusy(row.id);
    try {
      await api(`/api/admin/settlements/${row.id}/payout`, {
        method: "PATCH",
        body: { adminMemo: memo },
        auth: true,
      });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "요청에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="mb-4 text-xl font-extrabold text-stone-900">정산 관리</h1>

      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-stone-200 text-left text-stone-500">
            <tr>
              <th className="p-3">예약</th>
              <th className="p-3">가이드</th>
              <th className="p-3">총액</th>
              <th className="p-3">수수료</th>
              <th className="p-3">정산액</th>
              <th className="p-3">상태</th>
              <th className="p-3">액션</th>
            </tr>
          </thead>
          <tbody>
            {rows?.map((row) => (
              <tr key={row.id} className="border-b border-stone-100">
                <td className="p-3">#{row.bookingId}</td>
                <td className="p-3">{row.guideProfileId}</td>
                <td className="p-3">₩{row.grossAmount.toLocaleString()}</td>
                <td className="p-3">₩{row.commissionAmount.toLocaleString()}</td>
                <td className="p-3 font-semibold text-stone-900">
                  ₩{row.netAmount.toLocaleString()}
                </td>
                <td className="p-3">
                  {row.status === "PAID_OUT" ? (
                    <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
                      지급완료
                    </span>
                  ) : (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                      지급대기
                    </span>
                  )}
                </td>
                <td className="p-3">
                  {row.status === "PENDING" ? (
                    <button
                      disabled={busy === row.id}
                      onClick={() => onPayout(row)}
                      className="rounded-lg bg-sky-100 px-3 py-1 font-medium text-sky-700 disabled:opacity-40"
                    >
                      지급완료
                    </button>
                  ) : (
                    <span className="text-stone-400">—</span>
                  )}
                </td>
              </tr>
            ))}
            {rows && rows.length === 0 && (
              <tr>
                <td colSpan={7} className="p-6 text-center text-stone-400">
                  정산 내역이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
