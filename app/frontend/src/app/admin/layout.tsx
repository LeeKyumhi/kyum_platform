"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { api, getToken, saveToken, saveRole, clearToken, isAdmin } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type TokenResponse = { accessToken: string; tokenType: string; role: string };

const TABS = [
  { href: "/admin",              key: "tabDashboard" as const },
  { href: "/admin/users",        key: "tabUsers" as const },
  { href: "/admin/posts",        key: "tabModeration" as const },
  { href: "/admin/bookings",     key: "tabBookings" as const },
  { href: "/admin/verifications",key: "tabVerifications" as const },
  { href: "/admin/reports",      key: "tabReports" as const },
  { href: "/admin/settlements",  key: "tabSettlements" as const },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { t } = useLanguage();
  const a = t.admin;
  const pathname = usePathname();

  const [ready, setReady]   = useState(false);
  const [authed, setAuthed] = useState(false);
  const [form, setForm]     = useState({ email: "", password: "" });
  const [error, setError]   = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setAuthed(!!getToken() && isAdmin());
    setReady(true);
  }, []);

  async function onLogin(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setLoading(true);
    try {
      const res = await api<TokenResponse>("/api/auth/login", { method: "POST", body: form });
      if (res.role !== "ADMIN") { setError(a.notAdmin); return; }  // 토큰 저장 안 함
      saveToken(res.accessToken);
      saveRole(res.role);
      setAuthed(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setLoading(false);
    }
  }

  function onLogout() {
    clearToken();
    setAuthed(false);
  }

  if (!ready) return null;

  // ── 게이트: 관리자 아님 → 전용 로그인 화면 ──
  if (!authed) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-stone-50 px-4">
        <div className="w-full max-w-sm">
          <div className="mb-6 text-center">
            <h1 className="text-2xl font-extrabold text-stone-900">{a.loginTitle}</h1>
            <p className="mt-1.5 text-sm text-stone-500">{a.loginSub}</p>
          </div>
          <form onSubmit={onLogin} className="card flex flex-col gap-4 p-8 shadow-lg">
            <div>
              <label className="input-label">{a.email}</label>
              <input type="email" required className="input" value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div>
              <label className="input-label">{a.password}</label>
              <input type="password" required className="input" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </div>
            {error && <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>}
            <button type="submit" disabled={loading} className="btn-primary mt-2 w-full py-3">
              {loading ? a.loggingIn : a.loginBtn}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // ── 포털 셸: 상단 탭 내비 + children ──
  return (
    <div className="fixed inset-0 z-40 flex flex-col bg-stone-50">
      <header className="flex items-center justify-between border-b border-stone-200 bg-white px-6 py-3">
        <div className="flex items-center gap-6">
          <span className="font-extrabold text-stone-900">{a.portalTitle}</span>
          <nav className="flex gap-1">
            {TABS.map((tab) => {
              const active = tab.href === "/admin" ? pathname === "/admin" : pathname.startsWith(tab.href);
              return (
                <Link key={tab.href} href={tab.href}
                  className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                    active ? "bg-sky-100 text-sky-700" : "text-stone-500 hover:bg-stone-100"
                  }`}>
                  {a[tab.key]}
                </Link>
              );
            })}
          </nav>
        </div>
        <button onClick={onLogout} className="text-sm font-medium text-stone-500 hover:text-stone-800">
          {a.logout}
        </button>
      </header>
      <main className="flex-1 overflow-y-auto p-6">{children}</main>
    </div>
  );
}
