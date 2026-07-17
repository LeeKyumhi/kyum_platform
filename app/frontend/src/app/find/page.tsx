"use client";

import { useLanguage } from "@/context/LanguageContext";
import TrackEntryCards from "@/components/TrackEntryCards";

export default function FindPage() {
  const { t } = useLanguage();
  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 text-center">
          <h1 className="section-title">{t.find.title}</h1>
          <p className="section-subtitle">{t.find.sub}</p>
        </div>
        <TrackEntryCards compact />
      </div>
    </main>
  );
}
