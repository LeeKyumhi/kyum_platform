"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import SlotCalendar from "@/components/SlotCalendar";

type AvailableSlot = { id: number; guideProfileId: number; startAt: string; endAt: string };
type Profile = { id: number };

/**
 * Self-contained slot management panel for the logged-in guide.
 * Fetches the guide's own profile id + slots on mount, and wires
 * add/delete handlers into <SlotCalendar mode="guide" />.
 * Renders nothing if the user has no guide profile yet.
 */
export default function GuideSlotManager() {
  const { t } = useLanguage();

  const [profileId, setProfileId] = useState<number | null>(null);
  const [slots, setSlots] = useState<AvailableSlot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadSlots = useCallback(async (id: number) => {
    const data = await api<AvailableSlot[]>(`/api/guides/${id}/slots`);
    setSlots(data);
  }, []);

  useEffect(() => {
    let cancelled = false;
    api<Profile>("/api/guide-profiles/me", { auth: true })
      .then(async (p) => {
        if (cancelled) return;
        setProfileId(p.id);
        await loadSlots(p.id);
      })
      .catch(() => { /* no guide profile yet — render nothing */ })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [loadSlots]);

  async function onAddSlot(startAt: string, endAt: string) {
    if (profileId == null) return;
    await api("/api/guide-profiles/me/slots", {
      method: "POST", auth: true,
      body: { startAt, endAt },
    });
    await loadSlots(profileId);
  }

  async function onDeleteSlot(slotId: number) {
    try {
      await api(`/api/guide-profiles/me/slots/${slotId}`, { method: "DELETE", auth: true });
      setSlots((prev) => prev.filter((s) => s.id !== slotId));
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    }
  }

  if (loading) {
    return <div className="py-4 text-center text-xs text-stone-400">{t.common.loading}</div>;
  }
  if (profileId == null) return null;

  return (
    <div>
      {error && (
        <p className="mb-2 rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-xs text-red-600">{error}</p>
      )}
      <SlotCalendar mode="guide" slots={slots} onAddSlot={onAddSlot} onDeleteSlot={onDeleteSlot} />
    </div>
  );
}
