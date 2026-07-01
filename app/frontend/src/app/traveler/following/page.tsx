"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type FollowingGuide = {
  guideProfileId: number; guideName: string; guideAvatarUrl: string | null;
  headline: string; region: string; mbti: string | null; interests: string[];
};

function Avatar({ src, name }: { src: string | null; name: string }) {
  if (src) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={src} alt={name} className="w-12 h-12 rounded-full object-cover ring-2 ring-white shadow-sm flex-shrink-0" />;
  }
  return (
    <div className="w-12 h-12 rounded-full bg-gradient-to-br from-indigo-400 to-violet-500 flex items-center justify-center text-white font-bold ring-2 ring-white shadow-sm flex-shrink-0">
      {name.slice(0, 1).toUpperCase()}
    </div>
  );
}

export default function FollowingPage() {
  const router    = useRouter();
  const { t }     = useLanguage();
  const lper      = t.personality;

  const [guides, setGuides] = useState<FollowingGuide[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<FollowingGuide[]>("/api/users/me/following", { auth: true })
      .then(setGuides)
      .finally(() => setLoading(false));
  }, [router]);

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center gap-3 mb-6">
          <Link href="/traveler" className="btn-ghost text-sm">{t.common.back}</Link>
          <h1 className="section-title">{lper.followingTitle}</h1>
        </div>

        {loading && (
          <div className="flex flex-col gap-3">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="card p-4 animate-pulse flex items-center gap-4">
                <div className="w-12 h-12 rounded-full bg-gray-100 flex-shrink-0" />
                <div className="flex-1">
                  <div className="h-4 bg-gray-100 rounded w-32 mb-2" />
                  <div className="h-3 bg-gray-100 rounded w-48" />
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && guides.length === 0 && (
          <div className="text-center py-20">
            <div className="text-5xl mb-4">🔍</div>
            <p className="text-gray-500 text-sm">{lper.followingEmpty}</p>
            <Link href="/guides" className="mt-4 inline-block btn-primary text-sm">
              {t.guides.tabGuides} →
            </Link>
          </div>
        )}

        {!loading && guides.length > 0 && (
          <div className="flex flex-col gap-3">
            {guides.map((g) => (
              <Link key={g.guideProfileId} href={`/guides/${g.guideProfileId}`}
                className="card-hover p-4 flex items-center gap-4">
                <Avatar src={g.guideAvatarUrl} name={g.guideName} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <span className="font-semibold text-gray-900">{g.guideName}</span>
                    {g.mbti && (
                      <span className="rounded-md bg-violet-100 text-violet-700 px-1.5 py-0.5 text-xs font-bold">{g.mbti}</span>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 truncate">📍 {g.region} · {g.headline}</p>
                  {g.interests.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-1.5">
                      {g.interests.slice(0, 4).map((k) => (
                        <span key={k} className="rounded-full bg-gray-100 text-gray-500 px-2 py-0.5 text-xs">
                          {t.interests[k as keyof typeof t.interests] ?? k}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
                <span className="text-indigo-400 text-sm flex-shrink-0">→</span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
