"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";

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
        <div className="flex items-center gap-3 mb-2">
          <Link href="/" className="btn-ghost text-sm">{t.common.back}</Link>
          <h1 className="section-title">🧳 {li.title}</h1>
        </div>
        <p className="text-sm text-gray-400 mb-5">{li.subtitle}</p>

        {!showForm ? (
          <button onClick={() => setShowForm(true)} className="btn-primary w-full mb-5">+ {li.newTrip}</button>
        ) : (
          <div className="card p-5 mb-5 flex flex-col gap-4">
            <h2 className="font-semibold text-gray-900">{li.formTitle}</h2>
            <div>
              <label className="text-xs text-gray-500">{li.tripTitle}</label>
              <input value={title} onChange={(e) => setTitle(e.target.value)}
                placeholder={li.tripTitlePh} className="input mt-1 w-full" />
            </div>
            <div>
              <label className="text-xs text-gray-500">{li.cityOptional}</label>
              <div className="mt-1"><CitySelect value={city} onChange={(c) => setCity(c)} /></div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-gray-500">{li.startDate}</label>
                <input type="date" value={startDate} onChange={(e) => setStart(e.target.value)} className="input mt-1 w-full" />
              </div>
              <div>
                <label className="text-xs text-gray-500">{li.endDate}</label>
                <input type="date" value={endDate} min={startDate || undefined}
                  onChange={(e) => setEnd(e.target.value)} className="input mt-1 w-full" />
              </div>
            </div>
            {error && <p className="text-sm text-red-500">{error}</p>}
            <div className="flex gap-2">
              <button onClick={onCreate} disabled={creating || !title.trim()} className="btn-primary px-6 disabled:opacity-60">
                {creating ? li.creating : li.createBtn}
              </button>
              <button onClick={() => setShowForm(false)} className="btn-ghost px-4">{li.cancel}</button>
            </div>
          </div>
        )}

        {trips === null ? (
          <p className="text-center text-gray-400 text-sm py-10">{li.loading}</p>
        ) : trips.length === 0 ? (
          <p className="text-center text-gray-400 text-sm py-10">{li.empty}</p>
        ) : (
          <div className="flex flex-col gap-3">
            {trips.map((trip) => (
              <Link key={trip.id} href={`/trips/${trip.id}`} className="card-hover p-4 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold text-gray-900 truncate">{trip.title || li.untitled}</p>
                  <p className="text-xs text-gray-400 mt-0.5">
                    {trip.city ? `📍 ${trip.city}` : ""}
                    {trip.startDate ? `  ${trip.startDate}${trip.endDate ? ` ~ ${trip.endDate}` : ""}` : ""}
                  </p>
                </div>
                <span className="text-xs text-gray-400 whitespace-nowrap flex-shrink-0">
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
