"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import DistrictSelect from "@/components/DistrictSelect";
import TripMap from "@/components/TripMap";
import { PinIcon } from "@/components/icons";
import { matchSpot } from "@/lib/spots";
import PlaceDetailModal from "@/components/PlaceDetailModal";

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
  phone?: string | null;
  placeUrl?: string | null;
  distanceMeters?: number | null;
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
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [saving, setSaving]   = useState(false);
  const [saved, setSaved]     = useState(false);
  const [error, setError]     = useState("");

  // 장소 담기 패널
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerCat, setPickerCat]   = useState("attraction");
  const [pickerDistrict, setPickerDistrict] = useState("");
  const [places, setPlaces]         = useState<Place[]>([]);
  const [placesLoading, setPlacesLoading] = useState(false);
  const [kakaoOn, setKakaoOn]       = useState(true);
  const [manualName, setManualName] = useState("");
  const [detailPlace, setDetailPlace] = useState<Place | null>(null);

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
    const districtParam = pickerDistrict ? `&district=${encodeURIComponent(pickerDistrict)}` : "";
    api<PlacesResponse>(`/api/places?city=${encodeURIComponent(city)}&category=${pickerCat}${districtParam}&lang=${lang}`)
      .then((res) => { if (!cancelled) { setPlaces(res.places); setKakaoOn(res.kakaoEnabled); } })
      .catch(() => { if (!cancelled) setPlaces([]); })
      .finally(() => { if (!cancelled) setPlacesLoading(false); });
    return () => { cancelled = true; };
  }, [pickerOpen, city, pickerCat, pickerDistrict]);

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
      <div className="text-sm text-stone-400">{li.loading}</div>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="container-sm">
        {/* Top bar — back / saved / save */}
        <div className="mb-5 flex items-center justify-between">
          <Link href="/trips" className="btn-ghost text-sm">{li.back}</Link>
          <div className="flex items-center gap-3">
            {saved && (
              <span className="badge-emerald py-1">✓ {li.saved}</span>
            )}
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
            <CitySelect value={city} onChange={(c) => { setCity(c); setPickerDistrict(""); }} />
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

        {/* 일차 탭 */}
        <div className="mb-4 flex items-center gap-2 overflow-x-auto pb-1">
          <div className="seg-wrap flex-shrink-0">
            {days.map((d) => (
              <button key={d} onClick={() => setActiveDay(d)}
                className={`whitespace-nowrap ${activeDay === d ? "seg-active" : "seg"}`}>
                {dayLabel(d)}
              </button>
            ))}
          </div>
          <button onClick={() => setExtraDays(Math.max(extraDays, dayCount) + 1)}
            className="flex-shrink-0 whitespace-nowrap rounded-full border border-dashed border-stone-300 px-4 py-2 text-sm font-medium text-stone-400 transition-colors hover:border-sky-300 hover:text-sky-500">
            {li.addDay}
          </button>
        </div>

        {/* 현재 일차의 장소 목록 */}
        <div className="mb-4 flex flex-col gap-3">
          {itemsForDay(activeDay).length === 0 ? (
            <div className="card p-8 text-center">
              <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-xl shadow-md">
                📍
              </div>
              <p className="text-sm text-stone-400">{li.noItems}</p>
            </div>
          ) : (
            itemsForDay(activeDay).map((it, idx, arr) => {
              const expanded = expandedKey === it._k;
              const isTour = it.category === "tour";
              const spot = expanded && !isTour ? matchSpot(it.placeName) : undefined;
              return (
                <div key={it._k} className={`card p-4 ${isTour ? "border-amber-200 bg-amber-50/50 ring-1 ring-amber-100" : ""}`}>
                  <div className="flex items-start gap-3">
                    <span className={`mt-0.5 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold text-white shadow-sm ${
                      isTour ? "bg-gradient-to-br from-amber-400 to-orange-400" : "bg-gradient-to-br from-sky-400 to-cyan-400"
                    }`}>
                      {isTour ? "🎫" : idx + 1}
                    </span>
                    <div
                      className="min-w-0 flex-1 cursor-pointer"
                      onClick={() => setExpandedKey(expanded ? null : it._k)}
                      aria-expanded={expanded}
                    >
                      <p className="flex items-center gap-1.5 font-bold text-stone-900">
                        <span className="truncate">{it.placeName}</span>
                        <span className="flex-shrink-0 text-[10px] text-stone-300">{expanded ? "▲" : "▼"}</span>
                      </p>
                      {isTour ? (
                        <p className="mt-0.5 inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-700">
                          🎫 {li.tourBadge}
                        </p>
                      ) : it.category && (
                        <p className="mt-0.5 truncate text-xs text-stone-400">{it.category}</p>
                      )}
                      {it.address && (
                        <p className="mt-0.5 flex items-center gap-1 text-xs text-stone-400">
                          <PinIcon className="h-3 w-3 flex-shrink-0 text-sky-400" />
                          <span className="truncate">{it.address}</span>
                        </p>
                      )}
                    </div>
                    <div className="flex flex-shrink-0 items-center gap-0.5">
                      <button onClick={() => move(it._k, -1)} disabled={idx === 0}
                        className="btn-ghost h-8 w-8 px-0 py-0 disabled:opacity-30" title={li.moveUp}>▲</button>
                      <button onClick={() => move(it._k, 1)} disabled={idx === arr.length - 1}
                        className="btn-ghost h-8 w-8 px-0 py-0 disabled:opacity-30" title={li.moveDown}>▼</button>
                      <button onClick={() => removeItem(it._k)}
                        className="btn-ghost h-8 w-8 px-0 py-0 hover:bg-red-50 hover:text-red-500" title={li.remove}>✕</button>
                    </div>
                  </div>
                  {expanded && (
                    <div className="mt-3">
                      {spot ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={spot.img}
                          alt={it.placeName}
                          className="aspect-video w-full rounded-xl object-cover"
                        />
                      ) : (
                        <div className={`flex aspect-video w-full items-center justify-center rounded-xl shadow-sm ${
                          isTour ? "bg-gradient-to-br from-amber-400 to-orange-400" : "bg-gradient-to-br from-sky-400 to-cyan-400"
                        }`}>
                          <span className="text-5xl drop-shadow-sm">{isTour ? "🎫" : "📍"}</span>
                        </div>
                      )}
                    </div>
                  )}
                  <input value={it.memo ?? ""} onChange={(e) => setMemo(it._k, e.target.value)}
                    placeholder={li.memoPh} className="input mt-3 py-2 text-sm" />
                </div>
              );
            })
          )}
        </div>

        {/* 이 날 경로 지도 */}
        <div className="card mb-5 p-5">
          <h3 className="mb-3 flex items-center gap-2 font-bold text-stone-900">
            <PinIcon className="h-5 w-5 text-sky-500" /> {t.tripMap.routeTitle} · {dayLabel(activeDay)}
          </h3>
          <TripMap
            points={itemsForDay(activeDay).map((it) => ({
              key: it._k, name: it.placeName, latitude: it.latitude, longitude: it.longitude,
            }))}
            className="rounded-2xl"
          />
        </div>

        {/* 장소 담기 */}
        <div className="card mb-6 p-5">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="font-bold text-stone-900">{li.addPlace} · {dayLabel(activeDay)}</h3>
            <button onClick={() => setPickerOpen((v) => !v)} className="btn-ghost px-3 py-1 text-xs">
              {pickerOpen ? "▲" : "▼"}
            </button>
          </div>

          {/* 수동 입력 (Kakao 없이도 사용 가능) */}
          <div className="mb-3 flex gap-2">
            <input value={manualName} onChange={(e) => setManualName(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") addManual(); }}
              placeholder={li.manualAddPh} className="input flex-1 text-sm" />
            <button onClick={addManual} disabled={!manualName.trim()}
              className="btn-secondary px-4 text-sm disabled:opacity-50">{li.add}</button>
          </div>

          {pickerOpen && (
            <>
              {!city ? (
                <p className="py-4 text-center text-xs text-stone-400">{li.pickCityForPlaces}</p>
              ) : (
                <>
                  <DistrictSelect city={city} value={pickerDistrict} onChange={setPickerDistrict} className="mb-2 text-sm" />
                  <div className="mb-2 flex gap-1.5 overflow-x-auto pb-2">
                    {PLACE_CATS.map((c) => (
                      <button key={c.key} onClick={() => setPickerCat(c.key)}
                        className={`${pickerCat === c.key ? "chip-active" : "chip"} px-3 py-1.5 text-xs`}>
                        {c.icon} {le[c.labelKey]}
                      </button>
                    ))}
                  </div>
                  {!kakaoOn && (
                    <p className="mb-2 rounded-xl border border-amber-100 bg-amber-50 px-3 py-2 text-xs text-amber-700">
                      ⚠️ {le.kakaoDisabled}
                    </p>
                  )}
                  {placesLoading ? (
                    <p className="py-4 text-center text-xs text-stone-400">{le.loading}</p>
                  ) : places.length === 0 ? (
                    <p className="py-4 text-center text-xs text-stone-400">{le.empty}</p>
                  ) : (
                    <div className="flex max-h-72 flex-col gap-1.5 overflow-y-auto">
                      {places.map((p) => (
                        <div key={p.id}
                          className="flex items-center rounded-xl border border-stone-100 transition-colors hover:border-sky-200 hover:bg-sky-50/60">
                          <button
                            onClick={() => addItem({ placeId: p.id, placeName: p.name, category: p.category, address: p.address, latitude: p.latitude, longitude: p.longitude })}
                            className="min-w-0 flex-1 px-3 py-2.5 text-left">
                            <p className="truncate text-sm font-semibold text-stone-800">{p.name}</p>
                            {p.address && <p className="mt-0.5 truncate text-xs text-stone-400">{p.address}</p>}
                          </button>
                          <button
                            onClick={() => setDetailPlace(p)}
                            title={li.detailsBtn}
                            className="flex-shrink-0 self-stretch px-3 text-xs text-stone-300 transition-colors hover:text-sky-500">
                            ⓘ
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </div>

        {error && (
          <p className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
        )}

        <div className="divider" />
        <button onClick={onDelete} className="w-full py-2 text-sm text-stone-400 transition-colors hover:text-red-500">
          {li.deleteTrip}
        </button>
      </div>

      {detailPlace && (
        <PlaceDetailModal
          place={detailPlace}
          onClose={() => setDetailPlace(null)}
          addLabel={li.add}
          onAdd={() => {
            addItem({
              placeId: detailPlace.id,
              placeName: detailPlace.name,
              category: detailPlace.category,
              address: detailPlace.address,
              latitude: detailPlace.latitude,
              longitude: detailPlace.longitude,
            });
            setDetailPlace(null);
          }}
        />
      )}
    </main>
  );
}
