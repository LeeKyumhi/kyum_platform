"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

const TAG_KEYS = [
  "kind", "punctual", "knowledgeable", "flexible",
  "goodPhotos", "goodFood", "languageGood", "funny",
] as const;

export default function ReviewPage() {
  const params    = useParams();
  const router    = useRouter();
  const { t }     = useLanguage();
  const l         = t.review;
  const bookingId = params.bookingId as string;

  const [rating, setRating]   = useState(5);
  const [hover, setHover]     = useState(0);
  const [comment, setComment] = useState("");
  const [tags, setTags]       = useState<string[]>([]);
  const [error, setError]     = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => { if (!getToken()) router.replace("/login"); }, [router]);

  function toggleTag(key: string) {
    setTags((prev) => prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault(); setError(""); setSubmitting(true);
    try {
      await api(`/api/bookings/${bookingId}/review`, { method: "POST", auth: true, body: { rating, comment, tags } });
      router.push("/traveler/bookings");
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setSubmitting(false); }
  }

  const displayed = hover || rating;

  return (
    <main className="page flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-fade-up">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl shadow-md">
            ⭐
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight text-stone-900">{l.title}</h1>
          <p className="mt-1.5 text-sm text-stone-500">{l.sub}</p>
        </div>

        <div className="card overflow-hidden shadow-lg">
          <div className="h-1.5 bg-gradient-to-r from-sky-500 via-cyan-400 to-teal-400" />
          <form onSubmit={onSubmit} className="flex flex-col gap-5 p-8">
            <div className="flex flex-col items-center gap-2">
              <div className="flex gap-1">
                {[1,2,3,4,5].map((n) => (
                  <button key={n} type="button" onClick={() => setRating(n)}
                    onMouseEnter={() => setHover(n)} onMouseLeave={() => setHover(0)}
                    className="text-4xl transition-transform hover:scale-110 active:scale-95" aria-label={`${n}`}>
                    <span className={n <= displayed ? "text-amber-400" : "text-stone-200"}>★</span>
                  </button>
                ))}
              </div>
              <p className="h-5 text-sm font-medium text-stone-600">{l.labels[displayed]}</p>
            </div>
            <div className="divider my-1" />
            <div>
              <label className="input-label">
                {l.commentLabel} <span className="font-normal normal-case text-stone-400">{l.commentOpt}</span>
              </label>
              <textarea placeholder={l.commentPlaceholder} value={comment}
                onChange={(e) => setComment(e.target.value)} rows={4} className="input resize-none" />
            </div>
            <div>
              <label className="input-label">
                {l.tagsLabel} <span className="font-normal normal-case text-stone-400">{l.tagsSub}</span>
              </label>
              <div className="flex flex-wrap gap-2">
                {TAG_KEYS.map((key) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => toggleTag(key)}
                    className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
                      tags.includes(key)
                        ? "bg-gradient-to-r from-sky-500 to-cyan-500 text-white shadow-sm"
                        : "bg-stone-100 text-stone-500 hover:bg-stone-200"
                    }`}
                  >
                    {t.reviewTags[key]}
                  </button>
                ))}
              </div>
            </div>
            {error && (
              <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
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
