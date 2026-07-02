"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import TripMap from "@/components/TripMap";

type Item = {
  _k: string;
  dayIndex: number;
  sortOrder: number;
  placeId: string | null;
  placeName: string;
  category: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  memo: string | null;
};

type TripResponse = {
  id: number;
  title: string;
  city: string | null;
  startDate: string | null;
  endDate: string | null;
  items: Omit<Item, "_k">[];
};

type Place = {
  id: string;
  name: string;
  category: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
};

type PlacesResponse = { kakaoEnabled: boolean; places: Place[] };

const PLACE_CATS = [
  { key: "attraction", labelKey: "catAttraction", icon: "🏛️" },
  { key: "food",       labelKey: "catFood",       icon: "🍜" },
  { key: "cafe",       labelKey: "catCafe",       icon: "☕" },
  { key: "culture",    labelKey: "catCulture",    icon: "🎭" },
  { key: "market",     labelKey: "catMarket",     icon: "🛍️" },
] as const;

let keySeq = 0;
const newKey = () => `it_${keySeq++}_${Date.now()}`;

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
  const { t, lang } = useLanguage();
  const li = t.itinerary;
  const le = t.explore;

  const [loading, setLoading] = useState(true);
  const [title, setTitle]     = useState("");
  const [city, setCity]       = useState("");
  const [startDate, setStart] = useState("");
  const [endDate, setEnd]     = useState("");
  const [items, setItems]     = useState<Item[]>([]);
  const [activeDay, setActiveDay] = useState(1);
  const [extraDays, setExtraDays] = useState(1);
  const [saving, setSaving]   = useState(false);
  const [saved, setSaved]     = useState(false);
  const [error, setError]     = useState("");

  // 장소 담기 패널
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerCat, setPickerCat]   = useState("attraction");
  const [places, setPlaces]         = useState<Place[]>([]);
  const [placesLoading, setPlacesLoading] = useState(false);
  const [kakaoOn, setKakaoOn]       = useState(true);
  const [manualName, setManualName] = useState("");

  const dayLabel = (n: number) =>
    lang === "ko" ? `${n}일차` : lang === "zh" ? `第${n}天` : `Day ${n}`;

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<TripResponse>(`/api/itineraries/me/${id}`, { auth: true })
      .then((data) => {
        setTitle(data.title ?? "");
        setCity(data.city ?? "");
        setStart(data.startDate ?? "");
        setEnd(data.endDate ?? "");
        setItems((data.items ?? []).map((i) => ({ ...i, _k: newKey() })));
        setLoading(false);
      })
      .catch(() => router.replace("/trips"));
  }, [id, router]);

  // 일수: 날짜 기간 / 담긴 아이템의 최대 일차 / 수동 추가한 일수 중 최댓값 (아이템이 사라지지 않도록)
  const dayCount = useMemo(() => {
    const dateDays = startDate && endDate ? daysBetween(startDate, endDate) : 0;
    const maxItemDay = items.reduce((m, i) => Math.max(m, i.dayIndex), 1);
    return Math.max(extraDays, dateDays, maxItemDay, 1);
  }, [startDate, endDate, items, extraDays]);

  const days = useMemo(() => Array.from({ length: dayCount }, (_, i) => i + 1), [dayCount]);

  function itemsForDay(d: number) {
    return items.filter((i) => i.dayIndex === d).sort((a, b) => a.sortOrder - b.sortOrder);
  }

  function addItem(p: { placeId: string | null; placeName: string; category: string | null; address: string | null; latitude: number | null; longitude: number | null }) {
    // 같은 날의 현재 최대 sortOrder + 1 (중간 삭제 후 추가해도 충돌하지 않도록 length 대신 max 사용)
    const nextOrder = items
      .filter((i) => i.dayIndex === activeDay)
      .reduce((m, i) => Math.max(m, i.sortOrder + 1), 0);
    setItems((prev) => [...prev, { _k: newKey(), dayIndex: activeDay, sortOrder: nextOrder, memo: null, ...p }]);
  }

  function removeItem(k: string) {
    setItems((prev) => prev.filter((i) => i._k !== k));
  }

  function setMemo(k: string, memo: string) {
    setItems((prev) => prev.map((i) => (i._k === k ? { ...i, memo } : i)));
  }

  function move(k: string, dir: -1 | 1) {
    const item = items.find((i) => i._k === k);
    if (!item) return;
    const dayItems = itemsForDay(item.dayIndex);
    const idx = dayItems.findIndex((i) => i._k === k);
    const swapWith = idx + dir;
    if (swapWith < 0 || swapWith >= dayItems.length) return;
    const a = dayItems[idx], b = dayItems[swapWith];
    setItems((prev) => prev.map((i) => {
      if (i._k === a._k) return { ...i, sortOrder: b.sortOrder };
      if (i._k === b._k) return { ...i, sortOrder: a.sortOrder };
      return i;
    }));
  }

  function addManual() {
    if (!manualName.trim()) return;
    addItem({ placeId: null, placeName: manualName.trim(), category: null, address: null, latitude: null, longitude: null });
    setManualName("");
  }

  // 장소 검색 패널 열림 + 도시/카테고리 변경 시 조회
  useEffect(() => {
    if (!pickerOpen || !city) { setPlaces([]); return; }
    let cancelled = false;
    setPlacesLoading(true);
    api<PlacesResponse>(`/api/places?city=${encodeURIComponent(city)}&category=${pickerCat}`)
      .then((res) => { if (!cancelled) { setPlaces(res.places); setKakaoOn(res.kakaoEnabled); } })
      .catch(() => { if (!cancelled) setPlaces([]); })
      .finally(() => { if (!cancelled) setPlacesLoading(false); });
    return () => { cancelled = true; };
  }, [pickerOpen, city, pickerCat]);

  async function onSave() {
    setSaving(true); setSaved(false); setError("");
    // 일자별 순서를 0부터 다시 매겨 깔끔하게 저장
    const normalized = days.flatMap((d) =>
      itemsForDay(d).map((i, idx) => ({
        dayIndex: d, sortOrder: idx,
        placeId: i.placeId, placeName: i.placeName, category: i.category,
        address: i.address, latitude: i.latitude, longitude: i.longitude, memo: i.memo || null,
      }))
    );
    try {
      await api(`/api/itineraries/me/${id}`, {
        method: "PUT", auth: true,
        body: {
          title: title.trim() || li.untitled,
          city: city || null,
          startDate: startDate || null,
          endDate: endDate || null,
          items: normalized,
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
      <div className="text-gray-400 text-sm">{li.loading}</div>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center justify-between mb-4">
          <Link href="/trips" className="btn-ghost text-sm">{li.back}</Link>
          <div className="flex items-center gap-2">
            {saved && <span className="text-sm text-emerald-600 font-medium">{li.saved}</span>}
            <button onClick={onSave} disabled={saving} className="btn-primary text-sm px-5 disabled:opacity-60">
              {saving ? li.saving : li.save}
            </button>
          </div>
        </div>

        {/* 메타 편집 */}
        <div className="card p-5 mb-5 flex flex-col gap-4">
          <input value={title} onChange={(e) => setTitle(e.target.value)}
            placeholder={li.tripTitlePh} className="input w-full text-lg font-semibold" />
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
        </div>

        {/* 일차 탭 */}
        <div className="flex gap-2 overflow-x-auto pb-2 mb-3">
          {days.map((d) => (
            <button key={d} onClick={() => setActiveDay(d)}
              className={`whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium border transition-all ${
                activeDay === d
                  ? "bg-indigo-600 text-white border-indigo-600 shadow-sm"
                  : "bg-white text-gray-500 border-gray-200 hover:border-indigo-300"
              }`}>
              {dayLabel(d)}
            </button>
          ))}
          <button onClick={() => setExtraDays(Math.max(extraDays, dayCount) + 1)}
            className="whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium border border-dashed border-gray-300 text-gray-400 hover:text-indigo-600 hover:border-indigo-300">
            {li.addDay}
          </button>
        </div>

        {/* 현재 일차의 장소 목록 */}
        <div className="flex flex-col gap-2 mb-4">
          {itemsForDay(activeDay).length === 0 ? (
            <p className="text-center text-gray-400 text-sm py-8">{li.noItems}</p>
          ) : (
            itemsForDay(activeDay).map((it, idx, arr) => (
              <div key={it._k} className="card p-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-gray-900 truncate">{idx + 1}. {it.placeName}</p>
                    {it.category && <p className="text-xs text-gray-400 truncate">{it.category}</p>}
                    {it.address && <p className="text-xs text-gray-400 truncate">📍 {it.address}</p>}
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button onClick={() => move(it._k, -1)} disabled={idx === 0}
                      className="text-gray-400 hover:text-indigo-600 disabled:opacity-30 px-1.5 py-0.5 text-sm" title={li.moveUp}>▲</button>
                    <button onClick={() => move(it._k, 1)} disabled={idx === arr.length - 1}
                      className="text-gray-400 hover:text-indigo-600 disabled:opacity-30 px-1.5 py-0.5 text-sm" title={li.moveDown}>▼</button>
                    <button onClick={() => removeItem(it._k)}
                      className="text-gray-400 hover:text-red-500 px-1.5 py-0.5 text-sm" title={li.remove}>✕</button>
                  </div>
                </div>
                <input value={it.memo ?? ""} onChange={(e) => setMemo(it._k, e.target.value)}
                  placeholder={li.memoPh} className="input mt-2 w-full text-sm py-1.5" />
              </div>
            ))
          )}
        </div>

        {/* 이 날 경로 지도 */}
        <div className="card p-4 mb-4">
          <h3 className="font-semibold text-gray-900 text-sm mb-3">🗺️ {t.tripMap.routeTitle} · {dayLabel(activeDay)}</h3>
          <TripMap
            points={itemsForDay(activeDay).map((it) => ({
              key: it._k, name: it.placeName, latitude: it.latitude, longitude: it.longitude,
            }))}
          />
        </div>

        {/* 장소 담기 */}
        <div className="card p-4 mb-6">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-semibold text-gray-900 text-sm">{li.addPlace} · {dayLabel(activeDay)}</h3>
            <button onClick={() => setPickerOpen((v) => !v)} className="btn-ghost text-xs px-3 py-1">
              {pickerOpen ? "▲" : "▼"}
            </button>
          </div>

          {/* 수동 입력 (Kakao 없이도 사용 가능) */}
          <div className="flex gap-2 mb-3">
            <input value={manualName} onChange={(e) => setManualName(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") addManual(); }}
              placeholder={li.manualAddPh} className="input flex-1 text-sm" />
            <button onClick={addManual} disabled={!manualName.trim()}
              className="btn-secondary text-sm px-4 disabled:opacity-50">{li.add}</button>
          </div>

          {pickerOpen && (
            <>
              {!city ? (
                <p className="text-center text-gray-400 text-xs py-4">{li.pickCityForPlaces}</p>
              ) : (
                <>
                  <div className="flex gap-1.5 overflow-x-auto pb-2 mb-2">
                    {PLACE_CATS.map((c) => (
                      <button key={c.key} onClick={() => setPickerCat(c.key)}
                        className={`whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium border ${
                          pickerCat === c.key ? "bg-indigo-600 text-white border-indigo-600" : "bg-white text-gray-500 border-gray-200"
                        }`}>
                        {c.icon} {le[c.labelKey]}
                      </button>
                    ))}
                  </div>
                  {!kakaoOn && (
                    <p className="rounded-lg bg-amber-50 border border-amber-100 px-3 py-2 text-xs text-amber-700 mb-2">
                      ⚠️ {le.kakaoDisabled}
                    </p>
                  )}
                  {placesLoading ? (
                    <p className="text-center text-gray-400 text-xs py-4">{le.loading}</p>
                  ) : places.length === 0 ? (
                    <p className="text-center text-gray-400 text-xs py-4">{le.empty}</p>
                  ) : (
                    <div className="flex flex-col gap-1.5 max-h-72 overflow-y-auto">
                      {places.map((p) => (
                        <button key={p.id}
                          onClick={() => addItem({ placeId: p.id, placeName: p.name, category: p.category, address: p.address, latitude: p.latitude, longitude: p.longitude })}
                          className="text-left rounded-lg border border-gray-100 hover:border-indigo-300 hover:bg-indigo-50/40 px-3 py-2 transition-colors">
                          <p className="text-sm font-medium text-gray-800 truncate">{p.name}</p>
                          {p.address && <p className="text-xs text-gray-400 truncate">{p.address}</p>}
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </div>

        {error && <p className="rounded-xl bg-red-50 px-4 py-2 text-sm text-red-600 border border-red-100 mb-4">{error}</p>}

        <div className="divider" />
        <button onClick={onDelete} className="w-full text-sm text-gray-400 hover:text-red-500 transition-colors py-2">
          {li.deleteTrip}
        </button>
      </div>
    </main>
  );
}
