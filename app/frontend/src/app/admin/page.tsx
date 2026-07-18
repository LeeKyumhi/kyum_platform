"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type Stats = {
  totalUsers: number; totalGuides: number; newUsers7d: number;
  bookingsRequested: number; bookingsAccepted: number; bookingsCompleted: number;
  pendingVerifications: number; openReports: number;
};

export default function AdminDashboardPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Stats>("/api/admin/stats", { auth: true })
      .then(setStats)
      .catch((e) => setError(e instanceof Error ? e.message : t.common.error));
  }, [t.common.error]);

  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!stats) return <p className="text-sm text-stone-500">…</p>;

  const cards = [
    { label: a.statTotalUsers, value: stats.totalUsers },
    { label: a.statGuides, value: stats.totalGuides },
    { label: a.statNew7d, value: stats.newUsers7d },
    { label: a.statRequested, value: stats.bookingsRequested },
    { label: a.statAccepted, value: stats.bookingsAccepted },
    { label: a.statCompleted, value: stats.bookingsCompleted },
  ];

  return (
    <div className="mx-auto max-w-5xl">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {cards.map((c) => (
          <div key={c.label} className="card p-5">
            <div className="text-3xl font-extrabold text-stone-900">{c.value}</div>
            <div className="mt-1 text-sm text-stone-500">{c.label}</div>
          </div>
        ))}
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <Link href="/admin/verifications" className="card flex items-center justify-between p-5 hover:shadow-md">
          <div><div className="text-2xl font-bold">{stats.pendingVerifications}</div>
            <div className="text-sm text-stone-500">{a.statPendingVerif}</div></div>
          <span className="text-sm font-medium text-sky-600">{a.queueGo} →</span>
        </Link>
        <Link href="/admin/reports" className="card flex items-center justify-between p-5 hover:shadow-md">
          <div><div className="text-2xl font-bold">{stats.openReports}</div>
            <div className="text-sm text-stone-500">{a.statOpenReports}</div></div>
          <span className="text-sm font-medium text-sky-600">{a.queueGo} →</span>
        </Link>
      </div>
    </div>
  );
}
