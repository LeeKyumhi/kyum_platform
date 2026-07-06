"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import { PinIcon, CalendarIcon } from "@/components/icons";

type TripSummary = {
  id: number;
  title: string;
  city: string | null;
  startDate: string | null;
  endDate: string | null;
  createdAt: string;
  itemCount: number;
};

export default function TripsPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const li = t.itinerary;

  const [trips, setTrips]     = useState<TripSummary[] | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [title, setTitle]     = useState("");
  const [city, setCity]       = useState("");
  const [startDate, setStart] = useState("");
  const [endDate, setEnd]     = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError]     = useState("");

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<TripSummary[]>("/api/itineraries/me", { auth: true })
      .then(setTrips)
      .catch(() => router.replace("/login"));
  }, [router]);

  async function onCreate() {
    if (!title.trim()) return;
    setCreating(true); setError("");
    try {
      const created = await api<{ id: number }>("/api/itineraries/me", {
        method: "POST", auth: true,
        body: {
          title: title.trim(),
          city: city || null,
          startDate: startDate || null,
          endDate: endDate || null,
        },
      });
      router.push(`/trips/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
      setCreating(false);
    }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        {/* Header */}
        <div className="mb-6">
          <div className="flex items-center gap-3">
            <Link href="/" className="btn-ghost text-sm">{t.common.back}</Link>
            <h1 className="section-title">{li.title}</h1>
          </div>
          <p className="section-subtitle">{li.subtitle}</p>
        </div>

        {!showForm ? (
          <button onClick={() => setShowForm(true)} className="btn-primary mb-5 w-full">
            + {li.newTrip}
          </button>
        ) : (
          <div className="card mb-5 flex flex-col gap-4 p-5">
            <h2 className="font-bold text-stone-900">{li.formTitle}</h2>
            <div>
              <label className="input-label">{li.tripTitle}</label>
              <input value={title} onChange={(e) => setTitle(e.target.value)}
                placeholder={li.tripTitlePh} className="input" />
            </div>
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
            {error && (
              <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
            )}
            <div className="flex gap-2">
              <button onClick={onCreate} disabled={creating || !title.trim()} className="btn-primary px-6">
                {creating ? li.creating : li.createBtn}
              </button>
              <button onClick={() => setShowForm(false)} className="btn-ghost px-4">{li.cancel}</button>
            </div>
          </div>
        )}

        {trips === null ? (
          <div className="flex flex-col gap-3">
            {[1, 2, 3].map((i) => <div key={i} className="card h-20 animate-pulse p-4" />)}
          </div>
        ) : trips.length === 0 ? (
          <div className="card p-8 py-16 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl shadow-md">
              🧳
            </div>
            <p className="text-sm text-stone-500">{li.empty}</p>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {trips.map((trip) => (
              <Link key={trip.id} href={`/trips/${trip.id}`} className="card-hover flex items-center gap-3 p-4">
                <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-sky-400 to-cyan-400 text-lg shadow-sm">
                  🧳
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold text-stone-900">{trip.title || li.untitled}</p>
                  <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-stone-400">
                    {trip.city && (
                      <span className="flex items-center gap-1">
                        <PinIcon className="h-3.5 w-3.5 text-sky-400" /> {trip.city}
                      </span>
                    )}
                    {trip.startDate && (
                      <span className="flex items-center gap-1">
                        <CalendarIcon className="h-3.5 w-3.5 text-sky-400" />
                        {trip.startDate}{trip.endDate ? ` ~ ${trip.endDate}` : ""}
                      </span>
                    )}
                  </div>
                </div>
                <span className="badge-indigo flex-shrink-0 py-1">
                  {trip.itemCount} {li.placesUnit}
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
