"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { SearchIcon } from "@/components/icons";

type Following = {
  userId: number;
  handle: string;
  name: string;
  avatarUrl: string | null;
  isGuide: boolean;
  guideProfileId: number | null;
  headline: string;
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

  const [list, setList] = useState<Following[]>([]);
  const [loading, setLoading] = useState(true);
  const [unfollowingId, setUnfollowingId] = useState<number | null>(null);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Following[]>("/api/users/me/following", { auth: true })
      .then(setList)
      .finally(() => setLoading(false));
  }, [router]);

  async function onUnfollow(e: React.MouseEvent, userId: number) {
    e.preventDefault();
    e.stopPropagation();
    setUnfollowingId(userId);
    try {
      await api(`/api/users/${userId}/follow`, { method: "DELETE", auth: true });
      setList((prev) => prev.filter((f) => f.userId !== userId));
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

        {!loading && list.length === 0 && (
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

        {!loading && list.length > 0 && (
          <div className="flex flex-col gap-3">
            {list.map((f) => (
              <Link
                key={f.userId}
                href={f.isGuide && f.guideProfileId != null ? `/guides/${f.guideProfileId}` : `/users/${f.handle}`}
                className="card-hover flex items-center gap-4 p-4"
              >
                <Avatar src={f.avatarUrl} name={f.name} />
                <div className="min-w-0 flex-1">
                  <div className="mb-0.5 flex items-center gap-2">
                    <span className="font-semibold text-stone-900">{f.name}</span>
                    <span className="flex-shrink-0 text-xs font-medium text-stone-400">@{f.handle}</span>
                    {f.isGuide && (
                      <span className="rounded-md bg-sky-100 px-1.5 py-0.5 text-xs font-bold text-sky-700">
                        {t.guides.tabGuides}
                      </span>
                    )}
                  </div>
                  {f.headline && (
                    <p className="truncate text-xs text-stone-500">{f.headline}</p>
                  )}
                </div>
                <button
                  onClick={(e) => onUnfollow(e, f.userId)}
                  disabled={unfollowingId === f.userId}
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
