"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailContent />
    </Suspense>
  );
}

type Status = "verifying" | "success" | "error";

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const { t } = useLanguage();
  const l = t.verifyEmail;

  const [status, setStatus] = useState<Status>("verifying");
  const [error, setError] = useState("");
  const requestedRef = useRef(false);

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setError(l.missingTokenMsg);
      return;
    }
    if (requestedRef.current) return;
    requestedRef.current = true;

    async function verify() {
      try {
        await api("/api/auth/verify-email", { method: "POST", body: { token } });
        setStatus("success");
      } catch (err) {
        setStatus("error");
        setError(err instanceof Error ? err.message : t.common.error);
      }
    }
    verify();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <main className="page flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-fade-up">
        <div className="card overflow-hidden shadow-lg">
          <div className={`h-1.5 bg-gradient-to-r ${
            status === "error" ? "from-red-400 via-rose-400 to-red-400" : "from-sky-500 via-cyan-400 to-teal-400"
          }`} />
          <div className="flex flex-col items-center gap-4 p-8 text-center">
            {status === "verifying" && (
              <>
                <div className="h-8 w-8 animate-spin rounded-full border-2 border-sky-200 border-t-sky-500" />
                <p className="text-sm text-stone-500">{l.verifying}</p>
              </>
            )}
            {status === "success" && (
              <>
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-2xl">✅</div>
                <h1 className="text-lg font-extrabold tracking-tight text-stone-900">{l.successTitle}</h1>
                <p className="text-sm leading-relaxed text-stone-600">{l.successMsg}</p>
                <Link href="/login" className="btn-primary w-full py-3 text-center">
                  {l.continueBtn}
                </Link>
              </>
            )}
            {status === "error" && (
              <>
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-2xl">⚠️</div>
                <h1 className="text-lg font-extrabold tracking-tight text-stone-900">{l.failTitle}</h1>
                <p className="text-sm leading-relaxed text-stone-600">{error}</p>
                <Link href="/" className="btn-primary w-full py-3 text-center">
                  {l.homeBtn}
                </Link>
              </>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}
