"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken } from "@/lib/api";
import { clearMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";
import SlotCalendar from "@/components/SlotCalendar";

type Me = { id: number; fullName: string };
type Profile = { id: number; headline: string };
type AvailableSlot = { id: number; guideProfileId: number; startAt: string; endAt: string };

export default function GuideHome() {
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.guideHome;

  const [me, setMe] = useState<Me | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [hasProfile, setHasProfile] = useState<boolean | null>(null);
  const [slots, setSlots] = useState<AvailableSlot[]>([]);
  const [error, setError] = useState("");

  const loadSlots = useCallback(async (profileId: number) => {
    const data = await api<AvailableSlot[]>(`/api/guides/${profileId}/slots`);
    setSlots(data);
  }, []);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Me>("/api/users/me", { auth: true }).then(setMe).catch(() => router.replace("/login"));
    api<Profile>("/api/guide-profiles/me", { auth: true })
      .then((p) => {
        setProfile(p);
        setHasProfile(true);
        loadSlots(p.id);
      })
      .catch(() => setHasProfile(false));
  }, [router, loadSlots]);

  function onLogout() { clearToken(); clearMode(); router.push("/"); }

  async function onAddSlot(startAt: string, endAt: string) {
    if (!profile) return;
    setError("");
    await api("/api/guide-profiles/me/slots", {
      method: "POST", auth: true,
      body: { startAt, endAt },
    });
    await loadSlots(profile.id);
  }

  async function onDeleteSlot(slotId: number) {
    if (!profile) return;
    try {
      await api(`/api/guide-profiles/me/slots/${slotId}`, { method: "DELETE", auth: true });
      setSlots((prev) => prev.filter((s) => s.id !== slotId));
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center justify-between mb-6">
          <span className="badge-indigo py-1 px-3 text-sm">{l.badge}</span>
          <Link href="/select-mode" className="text-sm text-indigo-600 hover:underline font-medium">{l.switchMode}</Link>
        </div>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">
            {l.greeting}{me ? `, ${me.fullName}` : ""}! 👋
          </h1>
          <p className="mt-1 text-gray-500 text-sm">{l.sub}</p>
        </div>

        {error && (
          <div className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 border border-red-100">{error}</div>
        )}

        {hasProfile === false && (
          <div className="card p-6 mb-5 border border-indigo-100 bg-indigo-50/50 text-center">
            <div className="text-3xl mb-3">🚀</div>
            <p className="font-semibold text-gray-900 mb-1">{l.noProfileTitle}</p>
            <p className="text-sm text-gray-500 mb-4">{l.noProfileDesc}</p>
            <Link href="/become-guide" className="btn-primary">{l.noProfileBtn}</Link>
          </div>
        )}

        {hasProfile === null && (
          <div className="flex flex-col gap-3 mb-5">
            {[1, 2, 3].map((i) => <div key={i} className="card p-5 animate-pulse h-20" />)}
          </div>
        )}

        {hasProfile && (
          <>
            <div className="grid grid-cols-2 gap-3 mb-3">
              <Link href="/guide/manage" className="card-hover p-4 flex items-center gap-3">
                <span className="text-xl">✏️</span>
                <div>
                  <p className="font-semibold text-gray-900 text-sm">{l.link1title}</p>
                  <p className="text-xs text-gray-400 mt-0.5">{l.link1desc}</p>
                </div>
              </Link>
              <Link href="/guide/requests" className="card-hover p-4 flex items-center gap-3">
                <span className="text-xl">📬</span>
                <div>
                  <p className="font-semibold text-gray-900 text-sm">{l.link2title}</p>
                  <p className="text-xs text-gray-400 mt-0.5">{l.link2desc}</p>
                </div>
              </Link>
            </div>
            <Link href="/guide/posts" className="card-hover p-4 flex items-center gap-4 mb-6">
              <span className="text-xl">📷</span>
              <div className="flex-1">
                <p className="font-semibold text-gray-900 text-sm">{l.link3title}</p>
                <p className="text-xs text-gray-400 mt-0.5">{l.link3desc}</p>
              </div>
              <span className="w-8 h-8 rounded-full bg-indigo-600 text-white flex items-center justify-center text-lg font-light flex-shrink-0">+</span>
            </Link>
            <div className="grid grid-cols-2 gap-3 mb-6">
              <Link href="/explore" className="card-hover p-4 flex items-center gap-3">
                <span className="text-xl">🗺️</span>
                <div>
                  <p className="font-semibold text-gray-900 text-sm">{t.explore.title}</p>
                  <p className="text-xs text-gray-400 mt-0.5">{t.explore.subtitle}</p>
                </div>
              </Link>
              <Link href="/trips" className="card-hover p-4 flex items-center gap-3">
                <span className="text-xl">🧳</span>
                <div>
                  <p className="font-semibold text-gray-900 text-sm">{t.itinerary.title}</p>
                  <p className="text-xs text-gray-400 mt-0.5">{t.itinerary.subtitle}</p>
                </div>
              </Link>
            </div>

            <div className="card p-6 mb-5">
              <h2 className="font-semibold text-gray-900 mb-4">📅 {t.availability.sectionTitle}</h2>
              <SlotCalendar
                mode="guide"
                slots={slots}
                onAddSlot={onAddSlot}
                onDeleteSlot={onDeleteSlot}
              />
            </div>

          </>
        )}

        <div className="divider" />
        <button onClick={onLogout} className="w-full text-sm text-gray-400 hover:text-red-500 transition-colors py-2">
          {l.logout}
        </button>
      </div>
    </main>
  );
}
