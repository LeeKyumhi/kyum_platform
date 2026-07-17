"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import TimetableBuilder, { type BuilderItem, newItemKey } from "@/components/TimetableBuilder";

type TripResponse = {
  id: number; title: string; city: string | null;
  startDate: string | null; endDate: string | null;
  items: Omit<BuilderItem, "_k">[];
};

function daysBetween(start: string, end: string): number {
  const s = new Date(start).getTime();
  const e = new Date(end).getTime();
  if (isNaN(s) || isNaN(e) || e < s) return 0;
  return Math.floor((e - s) / 86400000) + 1;
}

export default function TripBuilderPage() {
  const router = useRouter();
  const params = useParams();
  const id = params.id as string;
  const { t } = useLanguage();
  const li = t.itinerary;

  const [loading, setLoading] = useState(true);
  const [title, setTitle]     = useState("");
  const [city, setCity]       = useState("");
  const [startDate, setStart] = useState("");
  const [endDate, setEnd]     = useState("");
  const [items, setItems]     = useState<BuilderItem[]>([]);
  const [saving, setSaving]   = useState(false);
  const [saved, setSaved]     = useState(false);
  const [error, setError]     = useState("");

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<TripResponse>(`/api/itineraries/me/${id}`, { auth: true })
      .then((data) => {
        setTitle(data.title ?? "");
        setCity(data.city ?? "");
        setStart(data.startDate ?? "");
        setEnd(data.endDate ?? "");
        setItems((data.items ?? []).map((i) => ({ ...i, _k: newItemKey() })));
        setLoading(false);
      })
      .catch(() => router.replace("/trips"));
  }, [id, router]);

  const minDayCount = useMemo(
    () => (startDate && endDate ? daysBetween(startDate, endDate) : 0),
    [startDate, endDate]);

  async function onSave() {
    setSaving(true); setSaved(false); setError("");
    // 일자별로 묶어 시간→레인 순으로 sortOrder 재부여 (미배치는 뒤로)
    const byDay = new Map<number, BuilderItem[]>();
    items.forEach((i) => { const arr = byDay.get(i.dayIndex) ?? []; arr.push(i); byDay.set(i.dayIndex, arr); });
    const normalized = [...byDay.entries()].flatMap(([d, di]) => {
      const sorted = [...di].sort((a, b) => {
        const ah = a.startHour ?? 99, bh = b.startHour ?? 99;
        return (ah - bh) || ((a.laneIndex ?? 0) - (b.laneIndex ?? 0));
      });
      return sorted.map((i, idx) => ({
        dayIndex: d, sortOrder: idx,
        placeId: i.placeId, placeName: i.placeName, category: i.category,
        address: i.address, latitude: i.latitude, longitude: i.longitude, memo: i.memo || null,
        startHour: i.startHour, durationHours: i.durationHours,
        laneIndex: i.laneIndex, laneSpan: i.laneSpan, sourceCourseId: i.sourceCourseId,
      }));
    });
    try {
      await api(`/api/itineraries/me/${id}`, {
        method: "PUT", auth: true,
        body: {
          title: title.trim() || li.untitled, city: city || null,
          startDate: startDate || null, endDate: endDate || null, items: normalized,
        },
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setSaving(false); }
  }

  async function onDelete() {
    if (!confirm(li.deleteConfirm)) return;
    try {
      await api(`/api/itineraries/me/${id}`, { method: "DELETE", auth: true });
      router.push("/trips");
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    }
  }

  if (loading) return (
    <main className="page flex items-center justify-center">
      <div className="text-sm text-stone-400">{li.loading}</div>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="mx-auto w-full max-w-6xl px-4">
        {/* Top bar */}
        <div className="mb-5 flex items-center justify-between">
          <Link href="/trips" className="btn-ghost text-sm">{li.back}</Link>
          <div className="flex items-center gap-3">
            {saved && <span className="badge-emerald py-1">✓ {li.saved}</span>}
            <button onClick={onSave} disabled={saving} className="btn-primary px-5 text-sm">
              {saving ? li.saving : li.save}
            </button>
          </div>
        </div>

        {/* 메타 편집 */}
        <div className="card mb-5 flex flex-col gap-4 p-5">
          <input value={title} onChange={(e) => setTitle(e.target.value)}
            placeholder={li.tripTitlePh} className="input text-lg font-bold" />
          <div>
            <label className="input-label">{li.cityOptional}</label>
            <CitySelect value={city} onChange={(c) => setCity(c)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="input-label">{li.startDate}</label>
              <input type="date" value={startDate} onChange={(e) => setStart(e.target.value)} className="input" />
            </div>
            <div>
              <label className="input-label">{li.endDate}</label>
              <input type="date" value={endDate} min={startDate || undefined}
                onChange={(e) => setEnd(e.target.value)} className="input" />
            </div>
          </div>
        </div>

        <TimetableBuilder
          items={items} onItemsChange={setItems}
          city={city} mode="trip" minDayCount={minDayCount}
        />

        {error && (
          <p className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
        )}

        <div className="divider" />
        <button onClick={onDelete} className="w-full py-2 text-sm text-stone-400 transition-colors hover:text-red-500">
          {li.deleteTrip}
        </button>
      </div>
    </main>
  );
}
