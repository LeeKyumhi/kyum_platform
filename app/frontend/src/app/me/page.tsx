"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type Me = {
  id: number;
  email: string;
  fullName: string;
  nationality: string | null;
};

export default function MePage() {
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.mePage;

  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    // 토큰이 없으면 로그인 페이지로
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    // 보호된 API 호출 (auth: true → Authorization 헤더에 토큰 첨부)
    api<Me>("/api/users/me", { auth: true })
      .then(setMe)
      .catch((err) => setError(err instanceof Error ? err.message : t.common.error));
  }, [router, t.common.error]);

  function onLogout() {
    clearToken();
    router.push("/login");
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <h1 className="section-title mb-6 text-center">{l.title}</h1>

        {error && (
          <p className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
        )}

        {me ? (
          <div className="card mb-6 p-6">
            <div className="mb-5 flex items-center gap-4">
              <div className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-xl font-bold text-white shadow-sm">
                {me.fullName.slice(0, 1).toUpperCase()}
              </div>
              <div className="min-w-0">
                <p className="font-bold text-stone-900">{me.fullName}</p>
                <p className="truncate text-sm text-stone-400">{me.email}</p>
              </div>
            </div>
            <dl className="flex flex-col gap-2">
              <div className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5 text-sm">
                <dt className="text-stone-500">{l.nameLabel}</dt>
                <dd className="font-medium text-stone-900">{me.fullName}</dd>
              </div>
              <div className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5 text-sm">
                <dt className="text-stone-500">{l.emailLabel}</dt>
                <dd className="font-medium text-stone-900">{me.email}</dd>
              </div>
              <div className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5 text-sm">
                <dt className="text-stone-500">{l.nationalityLabel}</dt>
                <dd className="font-medium text-stone-900">{me.nationality || "—"}</dd>
              </div>
            </dl>
          </div>
        ) : (
          !error && <p className="py-8 text-center text-sm text-stone-400">{t.common.loading}</p>
        )}

        <div className="flex flex-col gap-3">
          <Link href="/guides" className="btn-primary w-full py-3">
            {l.findGuides}
          </Link>
          <Link href="/become-guide" className="btn-secondary w-full py-3">
            {l.becomeGuide}
          </Link>
          <Link href="/guide/manage" className="btn-secondary w-full py-3">
            {l.manageGuide}
          </Link>
        </div>

        <div className="divider" />
        <button
          onClick={onLogout}
          className="w-full py-2 text-sm text-stone-400 transition-colors hover:text-red-500"
        >
          {l.logout}
        </button>
      </div>
    </main>
  );
}
