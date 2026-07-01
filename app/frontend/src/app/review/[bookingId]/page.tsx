"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export default function ReviewPage() {
  const params    = useParams();
  const router    = useRouter();
  const { t }     = useLanguage();
  const l         = t.review;
  const bookingId = params.bookingId as string;

  const [rating, setRating]   = useState(5);
  const [hover, setHover]     = useState(0);
  const [comment, setComment] = useState("");
  const [error, setError]     = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => { if (!getToken()) router.replace("/login"); }, [router]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault(); setError(""); setSubmitting(true);
    try {
      await api(`/api/bookings/${bookingId}/review`, { method: "POST", auth: true, body: { rating, comment } });
      router.push("/traveler/bookings");
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setSubmitting(false); }
  }

  const displayed = hover || rating;

  return (
    <main className="page flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-fade-up">
        <div className="text-center mb-8">
          <span className="text-3xl">⭐</span>
          <h1 className="mt-3 text-2xl font-bold text-gray-900">{l.title}</h1>
          <p className="mt-1 text-sm text-gray-500">{l.sub}</p>
        </div>
        <div className="card p-8 shadow-lg">
          <form onSubmit={onSubmit} className="flex flex-col gap-5">
            <div className="flex flex-col items-center gap-2">
              <div className="flex gap-1">
                {[1,2,3,4,5].map((n) => (
                  <button key={n} type="button" onClick={() => setRating(n)}
                    onMouseEnter={() => setHover(n)} onMouseLeave={() => setHover(0)}
                    className="text-4xl transition-transform hover:scale-110 active:scale-95" aria-label={`${n}`}>
                    <span className={n <= displayed ? "text-amber-400" : "text-gray-200"}>★</span>
                  </button>
                ))}
              </div>
              <p className="text-sm font-medium text-gray-600 h-5">{l.labels[displayed]}</p>
            </div>
            <div className="divider my-1" />
            <div>
              <label className="input-label">
                {l.commentLabel} <span className="text-gray-400 normal-case font-normal">{l.commentOpt}</span>
              </label>
              <textarea placeholder={l.commentPlaceholder} value={comment}
                onChange={(e) => setComment(e.target.value)} rows={4} className="input resize-none" />
            </div>
            {error && (
              <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600 border border-red-100">{error}</p>
            )}
            <button type="submit" disabled={submitting} className="btn-primary w-full py-3">
              {submitting ? l.loading : l.btn}
            </button>
          </form>
        </div>
      </div>
    </main>
  );
}
