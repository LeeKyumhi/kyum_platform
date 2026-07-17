"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type ReportItem = {
  id: number;
  reporterName: string;
  reason: string;
  targetType: string;
  targetId: number;
  targetSummary: string | null;
  detail: string | null;
  createdAt: string;
};

export default function AdminReportsPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const lr = t.adminReports;
  const reasons = lr.reasons as Record<string, string>;

  const [items, setItems]     = useState<ReportItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied]   = useState(false);
  const [error, setError]     = useState("");
  const [busyId, setBusyId]   = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await api<ReportItem[]>("/api/admin/reports", { auth: true }));
      setDenied(false);
    } catch {
      setDenied(true);   // 403(비어드민) 또는 401
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load();
  }, [router, load]);

  async function act(id: number, action: "review" | "dismiss") {
    setError(""); setBusyId(id);
    try {
      await api(`/api/admin/reports/${id}/${action}`, { method: "POST", auth: true });
      setItems((prev) => prev.filter((it) => it.id !== id));
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setBusyId(null); }
  }

  function targetLabel(it: ReportItem) {
    if (it.targetType === "BOOKING") {
      return `${lr.booking} #${it.targetId}${it.targetSummary ? ` · ${it.targetSummary}` : ""}`;
    }
    return `${it.targetType} #${it.targetId}`;
  }

  if (loading) return (
    <main className="page flex items-center justify-center">
      <div className="text-sm text-stone-400">{t.common.loading}</div>
    </main>
  );

  if (denied) return (
    <main className="page flex flex-col items-center justify-center px-4 text-center">
      <div className="mb-3 text-3xl">🔒</div>
      <p className="font-bold text-stone-900">{lr.notAdmin}</p>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="mx-auto max-w-3xl">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h1 className="text-xl font-extrabold tracking-tight text-stone-900">🚩 {lr.title}</h1>
          <Link href="/admin/verifications" className="text-sm font-semibold text-sky-600 hover:underline">
            {t.verification.adminTitle} →
          </Link>
        </div>
        <p className="mb-5 text-sm text-stone-500">{lr.sub}</p>

        {error && <p className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">{error}</p>}

        {items.length === 0 ? (
          <p className="py-12 text-center text-sm text-stone-400">{lr.empty}</p>
        ) : (
          <ul className="flex flex-col gap-4">
            {items.map((it) => (
              <li key={it.id} className="card p-5">
                <div className="mb-3 flex items-baseline justify-between gap-2">
                  <span className="badge-amber">{reasons[it.reason] ?? it.reason}</span>
                  <span className="text-xs text-stone-400">
                    {new Date(it.createdAt).toLocaleString(lang === "ko" ? "ko-KR" : lang === "zh" ? "zh-CN" : "en-US")}
                  </span>
                </div>

                <dl className="mb-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
                  <dt className="text-stone-400">{lr.reporter}</dt>
                  <dd className="font-medium text-stone-800">{it.reporterName}</dd>
                  <dt className="text-stone-400">{lr.target}</dt>
                  <dd className="font-medium text-stone-800">{targetLabel(it)}</dd>
                  {it.detail && <>
                    <dt className="text-stone-400">{t.safety.detailLabel}</dt>
                    <dd className="text-stone-700">{it.detail}</dd>
                  </>}
                </dl>

                <div className="flex gap-2">
                  <button onClick={() => act(it.id, "review")} disabled={busyId === it.id}
                    className="btn-primary px-5 py-2 text-sm disabled:opacity-60">
                    {lr.reviewBtn}
                  </button>
                  <button onClick={() => act(it.id, "dismiss")} disabled={busyId === it.id}
                    className="rounded-full border border-stone-300 bg-white px-5 py-2 text-sm font-bold text-stone-600 hover:bg-stone-50 disabled:opacity-60">
                    {lr.dismissBtn}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
