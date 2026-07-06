"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import GuideSlotManager from "@/components/GuideSlotManager";
import { CalendarIcon } from "@/components/icons";

export default function GuideAvailabilityPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const av = t.availability;

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 flex items-center gap-3">
          <Link href="/guide" className="btn-ghost text-sm">← {t.nav.guideHome}</Link>
          <h1 className="section-title">{av.sectionTitle}</h1>
        </div>

        <div className="card p-6">
          <h2 className="mb-1 flex items-center gap-2 font-bold text-stone-900">
            <CalendarIcon className="h-5 w-5 text-emerald-500" /> {av.sectionTitle}
          </h2>
          <p className="mb-4 text-sm text-stone-500">{av.hint}</p>
          <GuideSlotManager />
        </div>
      </div>
    </main>
  );
}
