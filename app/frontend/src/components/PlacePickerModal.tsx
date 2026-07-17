"use client";

// 채팅에서 만날 장소를 자유 검색해 고르는 모달 (T1).
// GET /api/places/search?query=&lang= (authenticated — SecurityConfig permitAll 아님 → auth:true 필수).
// 선택하면 onSelect로 장소 정보를 넘겨 ChatRoom이 장소 카드로 전송한다.

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { useModalDismiss } from "@/lib/useModalDismiss";
import { PinIcon } from "@/components/icons";
import type { PlacePayload } from "@/lib/placeCard";

type SearchPlace = {
  id: string; name: string; category: string | null; address: string | null;
  latitude: number | null; longitude: number | null; placeUrl: string | null;
};
type SearchResponse = { kakaoEnabled: boolean; places: SearchPlace[] };

export default function PlacePickerModal({ onSelect, onClose }: {
  onSelect: (p: PlacePayload) => void;
  onClose: () => void;
}) {
  const { t, lang } = useLanguage();
  const pc = t.placeCard;
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchPlace[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [kakaoOn, setKakaoOn] = useState(true);
  const inputRef = useRef<HTMLInputElement>(null);

  useModalDismiss(onClose);
  useEffect(() => { inputRef.current?.focus(); }, []);

  async function search() {
    const q = query.trim();
    if (!q) return;
    setLoading(true);
    try {
      const res = await api<SearchResponse>(
        `/api/places/search?query=${encodeURIComponent(q)}&lang=${lang}`, { auth: true });
      setResults(res.places);
      setKakaoOn(res.kakaoEnabled);
    } catch {
      setResults([]);
    } finally { setLoading(false); }
  }

  function pick(p: SearchPlace) {
    if (p.latitude == null || p.longitude == null) return;
    onSelect({
      name: p.name, address: p.address, lat: p.latitude, lng: p.longitude,
      url: p.placeUrl, kind: "place",
    });
    onClose();
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center sm:items-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 flex max-h-[85vh] w-full flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl sm:max-w-md sm:rounded-2xl">
        <div className="flex items-center justify-between gap-3 border-b border-stone-100 px-5 py-4">
          <h2 className="text-base font-bold text-stone-900">📍 {pc.pickerTitle}</h2>
          <button onClick={onClose} aria-label={pc.close}
            className="flex h-8 w-8 items-center justify-center rounded-full text-lg text-stone-400 hover:bg-stone-100 hover:text-stone-700">✕</button>
        </div>

        <div className="flex gap-2 px-5 py-3">
          <input ref={inputRef} value={query} onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") search(); }}
            placeholder={pc.pickerPlaceholder}
            className="input flex-1 text-sm" />
          <button onClick={search} disabled={!query.trim() || loading}
            className="btn-primary px-4 text-sm disabled:opacity-50">{pc.pickerSearch}</button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-4">
          {results === null ? (
            <p className="px-2 py-6 text-center text-xs text-stone-400">{pc.pickerHint}</p>
          ) : loading ? (
            <p className="px-2 py-6 text-center text-xs text-stone-400">…</p>
          ) : results.length === 0 ? (
            <p className="px-2 py-6 text-center text-xs text-stone-400">
              {kakaoOn ? pc.pickerEmpty : "⚠️"}
            </p>
          ) : (
            <ul className="flex flex-col gap-1">
              {results.map((p) => (
                <li key={p.id}>
                  <button onClick={() => pick(p)} disabled={p.latitude == null}
                    className="flex w-full items-start gap-2.5 rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-sky-50 disabled:opacity-40">
                    <PinIcon className="mt-0.5 h-4 w-4 flex-shrink-0 text-sky-400" />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline gap-1.5">
                        <span className="truncate text-sm font-semibold text-stone-800">{p.name}</span>
                        {p.category && <span className="flex-shrink-0 truncate text-[11px] text-stone-400">{p.category}</span>}
                      </span>
                      {p.address && <span className="mt-0.5 block truncate text-xs text-stone-400">{p.address}</span>}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
