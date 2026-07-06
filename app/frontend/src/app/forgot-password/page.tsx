"use client";

import { useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export default function ForgotPasswordPage() {
  const { t } = useLanguage();
  const l = t.forgotPassword;

  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setLoading(true);
    try {
      await api("/api/auth/forgot-password", { method: "POST", body: { email } });
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setLoading(false);
    }
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
          {sent ? (
            <div className="flex flex-col items-center gap-4 p-8 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-2xl">✉️</div>
              <p className="text-sm leading-relaxed text-stone-600">{l.successMsg}</p>
              <Link href="/login" className="btn-primary w-full py-3 text-center">
                {l.backToLogin}
              </Link>
            </div>
          ) : (
            <form onSubmit={onSubmit} className="flex flex-col gap-4 p-8">
              <div>
                <label className="input-label">{l.emailPlaceholder}</label>
                <input name="email" type="email" placeholder="hello@example.com"
                  value={email} onChange={(e) => setEmail(e.target.value)} required className="input" />
              </div>
              {error && (
                <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
              )}
              <button type="submit" disabled={loading} className="btn-primary mt-2 w-full py-3">
                {loading ? l.loading : l.btn}
              </button>
            </form>
          )}
        </div>

        {!sent && (
          <p className="mt-6 text-center text-sm text-stone-500">
            <Link href="/login" className="font-semibold text-sky-500 transition-colors hover:text-sky-600 hover:underline">
              {l.backToLogin}
            </Link>
          </p>
        )}
      </div>
    </main>
  );
}
