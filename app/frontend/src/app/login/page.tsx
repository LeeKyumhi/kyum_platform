"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, saveToken, saveUserName, saveRole } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type TokenResponse = { accessToken: string; tokenType: string; role: string };

export default function LoginPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.login;

  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function onChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setLoading(true);
    try {
      const res = await api<TokenResponse>("/api/auth/login", { method: "POST", body: form });
      saveToken(res.accessToken);
      saveRole(res.role);
      const me = await api<{ fullName: string }>("/api/users/me", { auth: true });
      saveUserName(me.fullName);
      router.push("/select-mode");
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-fade-up">
        {/* Brand header */}
        <div className="mb-8 text-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo.png" alt="peerup" className="mx-auto h-20 w-auto" />
          <h1 className="mt-4 text-2xl font-extrabold tracking-tight text-stone-900">{l.title}</h1>
          <p className="mt-1.5 text-sm text-stone-500">{l.sub}</p>
        </div>

        {/* Form card with warm gradient accent */}
        <div className="card overflow-hidden shadow-lg">
          <div className="h-1.5 bg-gradient-to-r from-sky-500 via-cyan-400 to-teal-400" />
          <form onSubmit={onSubmit} className="flex flex-col gap-4 p-8">
            <div>
              <label className="input-label">{l.emailPlaceholder}</label>
              <input name="email" type="email" placeholder="hello@example.com"
                value={form.email} onChange={onChange} required className="input" />
            </div>
            <div>
              <label className="input-label">{l.passwordPlaceholder}</label>
              <input name="password" type="password" placeholder="••••••••"
                value={form.password} onChange={onChange} required className="input" />
              <div className="mt-1.5 text-right">
                <Link href="/forgot-password" className="text-xs font-medium text-sky-500 transition-colors hover:text-sky-600 hover:underline">
                  {l.forgotLink}
                </Link>
              </div>
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
          {l.noAccount}{" "}
          <Link href="/signup" className="font-semibold text-sky-500 transition-colors hover:text-sky-600 hover:underline">
            {l.signupLink}
          </Link>
        </p>
      </div>
    </main>
  );
}
