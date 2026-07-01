"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export default function SignupPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.signup;

  const [form, setForm] = useState({ email: "", password: "", fullName: "", nationality: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function onChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setLoading(true);
    try {
      await api("/api/auth/signup", { method: "POST", body: form });
      router.push("/login");
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
          <span className="text-3xl">🌏</span>
          <h1 className="mt-3 text-2xl font-bold text-gray-900">{l.title}</h1>
          <p className="mt-1 text-sm text-gray-500">{l.sub}</p>
        </div>

        <div className="card p-8 shadow-lg">
          <form onSubmit={onSubmit} className="flex flex-col gap-4">
            <div>
              <label className="input-label">{l.namePlaceholder}</label>
              <input name="fullName" type="text" placeholder={l.namePlaceholder}
                value={form.fullName} onChange={onChange} required className="input" />
            </div>
            <div>
              <label className="input-label">{l.emailPlaceholder}</label>
              <input name="email" type="email" placeholder="hello@example.com"
                value={form.email} onChange={onChange} required className="input" />
            </div>
            <div>
              <label className="input-label">{l.passwordPlaceholder}</label>
              <input name="password" type="password" placeholder={l.passwordPlaceholder}
                value={form.password} onChange={onChange} required className="input" />
            </div>
            <div>
              <label className="input-label">
                {l.nationalityLabel} <span className="text-gray-400 normal-case font-normal">{l.nationalityOpt}</span>
              </label>
              <input name="nationality" type="text" placeholder={l.nationalityPlaceholder}
                value={form.nationality} onChange={onChange} className="input" />
            </div>
            {error && (
              <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600 border border-red-100">{error}</p>
            )}
            <button type="submit" disabled={loading} className="btn-primary w-full py-3 mt-2">
              {loading ? l.loading : l.btn}
            </button>
          </form>
        </div>

        <p className="mt-5 text-center text-sm text-gray-500">
          {l.hasAccount}{" "}
          <Link href="/login" className="font-semibold text-indigo-600 hover:underline">{l.loginLink}</Link>
        </p>
      </div>
    </main>
  );
}
