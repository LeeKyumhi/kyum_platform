"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

const DISMISS_KEY = "peerup-email-banner-dismissed";

export default function EmailVerifiedBanner({ emailVerified }: { emailVerified: boolean }) {
  const { t } = useLanguage();
  const l = t.emailBanner;

  const [dismissed, setDismissed] = useState(true); // default hidden until we check sessionStorage (avoids SSR flash)
  const [sending, setSending] = useState(false);
  const [feedback, setFeedback] = useState("");

  useEffect(() => {
    setDismissed(sessionStorage.getItem(DISMISS_KEY) === "1");
  }, []);

  if (emailVerified || dismissed) return null;

  function onDismiss() {
    sessionStorage.setItem(DISMISS_KEY, "1");
    setDismissed(true);
  }

  async function onResend() {
    setSending(true); setFeedback("");
    try {
      await api("/api/users/me/resend-verification", { method: "POST", auth: true });
      setFeedback(l.resendSuccess);
    } catch (err) {
      setFeedback(err instanceof Error ? err.message : t.common.error);
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="card mb-5 border border-amber-200 bg-amber-50 p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-amber-800">{l.message}</p>
        <div className="flex flex-shrink-0 items-center gap-2">
          <button
            onClick={onResend}
            disabled={sending}
            className="rounded-lg bg-amber-500 px-3.5 py-1.5 text-xs font-bold text-white transition-colors hover:bg-amber-600 disabled:opacity-60"
          >
            {sending ? l.resendSending : l.resendBtn}
          </button>
          <button
            onClick={onDismiss}
            className="rounded-lg px-2 py-1.5 text-xs font-medium text-amber-700 hover:text-amber-900"
          >
            {l.dismiss}
          </button>
        </div>
      </div>
      {feedback && <p className="mt-2 text-xs text-amber-700">{feedback}</p>}
    </div>
  );
}
