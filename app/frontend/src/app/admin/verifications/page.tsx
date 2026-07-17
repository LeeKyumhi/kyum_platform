"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { CheckBadgeIcon } from "@/components/icons";

type PendingItem = {
  id: number;
  guideProfileId: number;
  guideName: string;
  legalName: string;
  licenseNumber: string;
  licenseIssueDate: string | null;
  licenseInnerNumber: string | null;
  licenseFileUrl: string;
  idDocFileUrl: string;
  status: string;
};

export default function AdminVerificationsPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const lv = t.verification;

  const [items, setItems]     = useState<PendingItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied]   = useState(false);
  const [error, setError]     = useState("");
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [busyId, setBusyId]   = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api<PendingItem[]>("/api/admin/verifications", { auth: true });
      setItems(data);
      setDenied(false);
    } catch {
      // 403(비어드민) 또는 401 — 접근 불가로 처리
      setDenied(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load();
  }, [router, load]);

  async function onApprove(id: number) {
    setError(""); setBusyId(id);
    try {
      await api(`/api/admin/verifications/${id}/approve`, { method: "POST", auth: true });
      setItems((prev) => prev.filter((it) => it.id !== id));
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setBusyId(null); }
  }

  async function onReject(id: number) {
    const reason = (reasons[id] ?? "").trim();
    if (!reason) { setError(lv.rejectReasonRequired); return; }
    setError(""); setBusyId(id);
    try {
      await api(`/api/admin/verifications/${id}/reject`, { method: "POST", auth: true, body: { reason } });
      setItems((prev) => prev.filter((it) => it.id !== id));
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setBusyId(null); }
  }

  if (loading) return (
    <main className="page flex items-center justify-center">
      <div className="text-sm text-stone-400">{t.common.loading}</div>
    </main>
  );

  if (denied) return (
    <main className="page flex flex-col items-center justify-center px-4 text-center">
      <div className="mb-3 text-3xl">🔒</div>
      <p className="font-bold text-stone-900">{lv.notAdmin}</p>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="mx-auto max-w-3xl">
        <div className="mb-2 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <CheckBadgeIcon className="h-6 w-6 text-emerald-500" />
            <h1 className="text-xl font-extrabold tracking-tight text-stone-900">{lv.adminTitle}</h1>
          </div>
          <Link href="/admin/reports" className="text-sm font-semibold text-sky-600 hover:underline">
            {t.adminReports.title} →
          </Link>
        </div>
        <p className="mb-5 text-sm text-stone-500">{lv.adminSub}</p>

        {error && <p className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">{error}</p>}

        {items.length === 0 ? (
          <p className="py-12 text-center text-sm text-stone-400">{lv.adminEmpty}</p>
        ) : (
          <ul className="flex flex-col gap-4">
            {items.map((it) => (
              <li key={it.id} className="card p-5">
                <div className="mb-3 flex items-baseline justify-between gap-2">
                  <span className="text-base font-bold text-stone-900">{it.guideName}</span>
                  <span className="badge-amber">{lv.statusPending}</span>
                </div>

                <dl className="mb-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
                  <dt className="text-stone-400">{lv.legalNameLabel}</dt>
                  <dd className="font-medium text-stone-800">{it.legalName}</dd>
                  <dt className="text-stone-400">{lv.licenseNumberLabel}</dt>
                  <dd className="font-medium text-stone-800">{it.licenseNumber}</dd>
                  <dt className="text-stone-400">{lv.issueDateLabel}</dt>
                  <dd className="font-medium text-stone-800">{it.licenseIssueDate ?? "—"}</dd>
                  <dt className="text-stone-400">{lv.innerNumberLabel}</dt>
                  <dd className="font-medium text-stone-800">{it.licenseInnerNumber ?? "—"}</dd>
                </dl>

                <div className="mb-4 flex flex-wrap gap-2">
                  <a href={it.licenseFileUrl} target="_blank" rel="noopener noreferrer"
                    className="rounded-full bg-stone-100 px-4 py-1.5 text-xs font-semibold text-stone-700 hover:bg-stone-200">
                    📄 {lv.viewLicense}
                  </a>
                  <a href={it.idDocFileUrl} target="_blank" rel="noopener noreferrer"
                    className="rounded-full bg-stone-100 px-4 py-1.5 text-xs font-semibold text-stone-700 hover:bg-stone-200">
                    🪪 {lv.viewIdDoc}
                  </a>
                </div>

                <p className="mb-3 rounded-lg bg-sky-50 px-3 py-2 text-xs text-sky-700">{lv.qnetHint}</p>

                <input
                  value={reasons[it.id] ?? ""}
                  onChange={(e) => setReasons((prev) => ({ ...prev, [it.id]: e.target.value }))}
                  placeholder={lv.rejectReasonPlaceholder}
                  className="input mb-3 text-sm"
                />
                <div className="flex gap-2">
                  <button onClick={() => onApprove(it.id)} disabled={busyId === it.id}
                    className="btn-primary px-5 py-2 text-sm disabled:opacity-60">
                    {lv.approveBtn}
                  </button>
                  <button onClick={() => onReject(it.id)} disabled={busyId === it.id}
                    className="rounded-full border border-red-300 bg-white px-5 py-2 text-sm font-bold text-red-600 hover:bg-red-50 disabled:opacity-60">
                    {lv.rejectBtn}
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
