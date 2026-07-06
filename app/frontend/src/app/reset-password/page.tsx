"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordContent />
    </Suspense>
  );
}

function ResetPasswordContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const { t } = useLanguage();
  const l = t.resetPassword;

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    if (newPassword.length < 8) {
      setError(l.tooShortError);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(l.mismatchError);
      return;
    }

    setLoading(true);
    try {
      await api("/api/auth/reset-password", {
        method: "POST",
        body: { token, newPassword },
      });
      setSuccess(true);
      setTimeout(() => router.push("/login"), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setLoading(false);
    }
  }

  if (!token) {
    return (
      <main className="page flex items-center justify-center px-4">
        <div className="w-full max-w-sm animate-fade-up">
          <div className="card overflow-hidden shadow-lg">
            <div className="h-1.5 bg-gradient-to-r from-red-400 via-rose-400 to-red-400" />
            <div className="flex flex-col items-center gap-4 p-8 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-2xl">⚠️</div>
              <h1 className="text-lg font-extrabold tracking-tight text-stone-900">{l.missingTokenTitle}</h1>
              <p className="text-sm leading-relaxed text-stone-600">{l.missingTokenMsg}</p>
              <Link href="/forgot-password" className="btn-primary w-full py-3 text-center">
                {l.requestNewLinkBtn}
              </Link>
            </div>
          </div>
        </div>
      </main>
    );
  }

  if (success) {
    return (
      <main className="page flex items-center justify-center px-4">
        <div className="w-full max-w-sm animate-fade-up">
          <div className="card overflow-hidden shadow-lg">
            <div className="h-1.5 bg-gradient-to-r from-sky-500 via-cyan-400 to-teal-400" />
            <div className="flex flex-col items-center gap-4 p-8 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-2xl">✅</div>
              <h1 className="text-lg font-extrabold tracking-tight text-stone-900">{l.successTitle}</h1>
              <p className="text-sm leading-relaxed text-stone-600">{l.successMsg}</p>
              <Link href="/login" className="btn-primary w-full py-3 text-center">
                {l.goToLoginBtn}
              </Link>
            </div>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="page flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-fade-up">
        <div className="mb-8 text-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo.png" alt="peerup" className="mx-auto h-20 w-auto" />
          <h1 className="mt-4 text-2xl font-extrabold tracking-tight text-stone-900">{l.title}</h1>
          <p className="mt-1.5 text-sm text-stone-500">{l.sub}</p>
        </div>

        <div className="card overflow-hidden shadow-lg">
          <div className="h-1.5 bg-gradient-to-r from-sky-500 via-cyan-400 to-teal-400" />
          <form onSubmit={onSubmit} className="flex flex-col gap-4 p-8">
            <div>
              <label className="input-label">{l.newPasswordPlaceholder}</label>
              <input name="newPassword" type="password" placeholder="••••••••"
                value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required className="input" />
            </div>
            <div>
              <label className="input-label">{l.confirmPasswordPlaceholder}</label>
              <input name="confirmPassword" type="password" placeholder="••••••••"
                value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required className="input" />
            </div>
            {error && (
              <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
            )}
            <button type="submit" disabled={loading} className="btn-primary mt-2 w-full py-3">
              {loading ? l.loading : l.btn}
            </button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-stone-500">
          {l.troubleHint}{" "}
          <Link href="/forgot-password" className="font-semibold text-sky-500 transition-colors hover:text-sky-600 hover:underline">
            {l.requestNewLinkBtn}
          </Link>
        </p>
      </div>
    </main>
  );
}
