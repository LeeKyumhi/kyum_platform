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

  const [form, setForm] = useState({ email: "", password: "", fullName: "", nationality: "", nickname: "" });
  const [gender, setGender] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const GENDER_OPTIONS = [
    { value: "male",   label: l.genderMale },
    { value: "female", label: l.genderFemale },
    { value: "other",  label: l.genderOther },
  ];

  function onChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setLoading(true);
    try {
      await api("/api/auth/signup", { method: "POST", body: { ...form, nickname: form.nickname || null, gender: gender || null } });
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
                {l.nicknameLabel} <span className="font-normal normal-case text-stone-400">{l.nicknameOpt}</span>
              </label>
              <div className="flex items-center gap-1.5">
                <span className="text-sm font-semibold text-stone-400">@</span>
                <input name="nickname" type="text" placeholder={l.nicknamePlaceholder} maxLength={20}
                  value={form.nickname} onChange={onChange} className="input flex-1" />
              </div>
              <p className="mt-1 text-[11px] text-stone-400">{l.nicknameHint}</p>
            </div>
            <div>
              <label className="input-label">
                {l.nationalityLabel} <span className="font-normal normal-case text-stone-400">{l.nationalityOpt}</span>
              </label>
              <input name="nationality" type="text" placeholder={l.nationalityPlaceholder}
                value={form.nationality} onChange={onChange} className="input" />
            </div>
            <div>
              <label className="input-label">
                {l.genderLabel} <span className="font-normal normal-case text-stone-400">{l.genderOptional}</span>
              </label>
              <div className="grid grid-cols-3 gap-2">
                {GENDER_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => setGender(gender === opt.value ? "" : opt.value)}
                    className={`justify-center ${gender === opt.value ? "chip-active" : "chip"}`}
                  >
                    {opt.label}
                  </button>
                ))}
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
          {l.hasAccount}{" "}
          <Link href="/login" className="font-semibold text-sky-500 transition-colors hover:text-sky-600 hover:underline">
            {l.loginLink}
          </Link>
        </p>
      </div>
    </main>
  );
}
