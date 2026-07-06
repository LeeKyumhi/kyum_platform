"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { PinIcon, SearchIcon } from "@/components/icons";

type FollowingGuide = {
  guideProfileId: number; guideName: string; guideAvatarUrl: string | null;
  headline: string; region: string; mbti: string | null; interests: string[];
};

function Avatar({ src, name }: { src: string | null; name: string }) {
  if (src) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={src} alt={name} className="h-12 w-12 flex-shrink-0 rounded-full object-cover shadow-sm ring-2 ring-white" />;
  }
  return (
    <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 font-bold text-white shadow-sm ring-2 ring-white">
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
  const [unfollowingId, setUnfollowingId] = useState<number | null>(null);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<FollowingGuide[]>("/api/users/me/following", { auth: true })
      .then(setGuides)
      .finally(() => setLoading(false));
  }, [router]);

  async function onUnfollow(e: React.MouseEvent, guideProfileId: number) {
    e.preventDefault();
    e.stopPropagation();
    setUnfollowingId(guideProfileId);
    try {
      await api(`/api/guides/${guideProfileId}/follow`, { method: "DELETE", auth: true });
      setGuides((prev) => prev.filter((g) => g.guideProfileId !== guideProfileId));
    } catch { /* keep list on failure */ }
    finally { setUnfollowingId(null); }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 flex items-center gap-3">
          <Link href="/traveler" className="btn-ghost text-sm">{t.common.back}</Link>
          <h1 className="section-title">{lper.followingTitle}</h1>
        </div>

        {loading && (
          <div className="flex flex-col gap-3">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="card flex animate-pulse items-center gap-4 p-4">
                <div className="h-12 w-12 flex-shrink-0 rounded-full bg-stone-100" />
                <div className="flex-1">
                  <div className="mb-2 h-4 w-32 rounded bg-stone-100" />
                  <div className="h-3 w-48 rounded bg-stone-100" />
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && guides.length === 0 && (
          <div className="py-20 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-white shadow-md">
              <SearchIcon className="h-6 w-6" />
            </div>
            <p className="mb-5 text-sm text-stone-500">{lper.followingEmpty}</p>
            <Link href="/guides" className="btn-primary text-sm">
              {t.guides.tabGuides} →
            </Link>
          </div>
        )}

        {!loading && guides.length > 0 && (
          <div className="flex flex-col gap-3">
            {guides.map((g) => (
              <Link
                key={g.guideProfileId}
                href={`/guides/${g.guideProfileId}`}
                className="card-hover flex items-center gap-4 p-4"
              >
                <Avatar src={g.guideAvatarUrl} name={g.guideName} />
                <div className="min-w-0 flex-1">
                  <div className="mb-0.5 flex items-center gap-2">
                    <span className="font-semibold text-stone-900">{g.guideName}</span>
                    {g.mbti && (
                      <span className="rounded-md bg-violet-100 px-1.5 py-0.5 text-xs font-bold text-violet-700">{g.mbti}</span>
                    )}
                  </div>
                  <p className="flex items-center gap-1 truncate text-xs text-stone-500">
                    <PinIcon className="h-3.5 w-3.5 flex-shrink-0" /> {g.region} · {g.headline}
                  </p>
                  {g.interests.length > 0 && (
                    <div className="mt-1.5 flex flex-wrap gap-1">
                      {g.interests.slice(0, 4).map((k) => (
                        <span key={k} className="badge-gray">
                          {t.interests[k as keyof typeof t.interests] ?? k}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
                <button
                  onClick={(e) => onUnfollow(e, g.guideProfileId)}
                  disabled={unfollowingId === g.guideProfileId}
                  className="flex-shrink-0 rounded-full bg-stone-900 px-4 py-1.5 text-xs font-bold text-white transition-all hover:bg-stone-700 disabled:opacity-60"
                >
                  {lper.unfollowBtn}
                </button>
              </Link>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
