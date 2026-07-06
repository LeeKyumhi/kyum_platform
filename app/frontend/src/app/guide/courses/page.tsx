"use client";

// 가이드 투어 코스 관리 — 고정 코스 상품 등록/삭제.
// 등록된 코스는 /guides의 "투어 코스" 탭과 가이드 상세 페이지에 노출된다.

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, apiUpload, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect, { loadCities, cityLabel, type City } from "@/components/CitySelect";
import DistrictSelect from "@/components/DistrictSelect";
import TripMap from "@/components/TripMap";
import { CameraIcon, PinIcon } from "@/components/icons";

type Waypoint = {
  sortOrder: number; placeId: string | null; placeName: string; category: string | null;
  address: string | null; latitude: number | null; longitude: number | null;
};

type Course = {
  id: number; title: string; description: string | null; city: string | null;
  durationHours: number; price: number; currency: string; maxPeople: number;
  imageUrl: string | null; waypoints: Waypoint[];
};

type RecStop = {
  order: number; name: string; category: string; address: string | null;
  latitude: number | null; longitude: number | null; placeUrl: string | null;
  distanceFromPrevMeters: number | null;
};

type RecResponse = {
  city: string; district: string | null; theme: string; kakaoEnabled: boolean;
  stops: RecStop[]; totalDistanceMeters: number; suggestedDurationHours: number;
};

const REC_THEMES = ["mixed", "attraction", "food", "cafe", "culture", "market"] as const;
type RecTheme = (typeof REC_THEMES)[number];

export default function GuideCoursesPage() {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const lc = t.courses;

  const [courses, setCourses] = useState<Course[] | null>(null);
  const [error, setError] = useState("");

  // Form state
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [city, setCity] = useState("");
  const [durationHours, setDurationHours] = useState(3);
  const [price, setPrice] = useState(50000);
  const [maxPeople, setMaxPeople] = useState(4);
  const [image, setImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [waypoints, setWaypoints] = useState<Waypoint[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [expandedCourseId, setExpandedCourseId] = useState<number | null>(null);

  // ── 코스 추천 상태 ──
  const [recCity, setRecCity] = useState("");
  const [recDistrict, setRecDistrict] = useState("");
  const [recTheme, setRecTheme] = useState<RecTheme>("mixed");
  const [recLoading, setRecLoading] = useState(false);
  const [rec, setRec] = useState<RecResponse | null>(null);
  const [recFilled, setRecFilled] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  const load = useCallback(() => {
    api<Course[]>("/api/guide-profiles/me/courses", { auth: true })
      .then(setCourses)
      .catch(() => router.replace("/guide"));
  }, [router]);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load();
  }, [router, load]);

  function onImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setImage(file);
    setImagePreview(file ? URL.createObjectURL(file) : null);
  }

  function resetForm() {
    setTitle(""); setDescription(""); setImage(null); setImagePreview(null);
    setWaypoints([]); setEditingId(null);
  }

  function onEdit(c: Course) {
    setEditingId(c.id);
    setTitle(c.title);
    setDescription(c.description ?? "");
    setCity(c.city ?? "");
    setDurationHours(c.durationHours);
    setPrice(c.price);
    setMaxPeople(c.maxPeople);
    setImage(null);
    setImagePreview(c.imageUrl);
    setWaypoints(c.waypoints.map((w, i) => ({ ...w, sortOrder: i })));
    formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    setError(""); setCreating(true);
    try {
      const fd = new FormData();
      fd.append("title", title.trim());
      if (description.trim()) fd.append("description", description.trim());
      if (city) fd.append("city", city);
      fd.append("durationHours", String(durationHours));
      fd.append("price", String(price));
      fd.append("maxPeople", String(maxPeople));
      if (image) fd.append("image", image);
      // waypoints는 등록/수정 둘 다 통째로 교체되므로 비어있어도 항상 보낸다(수정 시 누락되면 기존 동선이 지워짐)
      fd.append("waypoints", JSON.stringify(waypoints));
      if (editingId) {
        await apiUpload(`/api/guide-profiles/me/courses/${editingId}`, fd, { auth: true, method: "PUT" });
      } else {
        await apiUpload("/api/guide-profiles/me/courses", fd, { auth: true });
      }
      resetForm();
      load();
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setCreating(false); }
  }

  function addStopFromRec(s: RecStop) {
    setWaypoints((prev) => [
      ...prev,
      {
        sortOrder: prev.length, placeId: null, placeName: s.name, category: s.category,
        address: s.address, latitude: s.latitude, longitude: s.longitude,
      },
    ]);
  }

  function removeWaypoint(idx: number) {
    setWaypoints((prev) => prev.filter((_, i) => i !== idx).map((w, i) => ({ ...w, sortOrder: i })));
  }

  function moveWaypoint(idx: number, dir: -1 | 1) {
    setWaypoints((prev) => {
      const target = idx + dir;
      if (target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[idx], next[target]] = [next[target], next[idx]];
      return next.map((w, i) => ({ ...w, sortOrder: i }));
    });
  }

  async function onRecommend() {
    if (!recCity) return;
    setError(""); setRecLoading(true); setRecFilled(false);
    try {
      const params = new URLSearchParams({ city: recCity, theme: recTheme, lang });
      if (recDistrict) params.set("district", recDistrict);
      const res = await api<RecResponse>(`/api/courses/recommend?${params}`, { auth: true });
      setRec(res);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setRecLoading(false); }
  }

  /** 추천 결과를 지역 라벨(현재 언어)과 함께 등록 폼에 채워 넣는다. */
  async function useRecommendation() {
    if (!rec || rec.stops.length === 0) return;
    const cities = await loadCities();
    const cityObj = cities.find((c: City) => c.key === rec.city);
    const districtObj = cityObj?.districts?.find((d) => d.ko === rec.district);
    const area = districtObj
      ? (lang === "ko" ? districtObj.ko : lang === "zh" ? districtObj.zh : districtObj.en)
      : cityObj ? cityLabel(cityObj, lang) : rec.city;
    const themeLabel = lc.recThemes[rec.theme as RecTheme] ?? lc.recThemes.mixed;

    setTitle(lc.recTitleTemplate.replace("{area}", area).replace("{theme}", themeLabel));
    const km = (rec.totalDistanceMeters / 1000).toFixed(1);
    setDescription(
      `📍 ${lc.recDescHeader} (${km}km)\n` +
      rec.stops.map((s) => `${s.order}. ${s.name}${s.address ? ` — ${s.address}` : ""}`).join("\n")
    );
    setCity(rec.city);
    setDurationHours(rec.suggestedDurationHours || durationHours);
    setWaypoints(rec.stops.map((s, i) => ({
      sortOrder: i, placeId: null, placeName: s.name, category: s.category,
      address: s.address, latitude: s.latitude, longitude: s.longitude,
    })));
    setRecFilled(true);
    formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  async function onDelete(courseId: number) {
    if (!confirm(lc.deleteConfirm)) return;
    try {
      await api(`/api/guide-profiles/me/courses/${courseId}`, { method: "DELETE", auth: true });
      setCourses((prev) => prev ? prev.filter((c) => c.id !== courseId) : prev);
      if (editingId === courseId) resetForm();
      if (expandedCourseId === courseId) setExpandedCourseId(null);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
  }

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-2 flex items-center gap-3">
          <Link href="/guide" className="btn-ghost text-sm">← {t.nav.guideHome}</Link>
          <h1 className="section-title">{lc.manageTitle}</h1>
        </div>
        <p className="section-subtitle mb-6">{lc.manageSub}</p>

        {error && (
          <div className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>
        )}

        {/* ── ✨ 코스 추천 받기 ── */}
        <div className="card mb-6 flex flex-col gap-4 p-6">
          <div>
            <h2 className="font-bold text-stone-900">✨ {lc.recTitle}</h2>
            <p className="mt-0.5 text-sm text-stone-500">{lc.recSub}</p>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <CitySelect value={recCity} className="flex-1"
              onChange={(c) => { setRecCity(c); setRecDistrict(""); }} />
            <DistrictSelect city={recCity} value={recDistrict}
              onChange={setRecDistrict} className="sm:w-44" />
          </div>

          <div className="flex flex-wrap gap-2">
            {REC_THEMES.map((th) => (
              <button key={th} type="button" onClick={() => setRecTheme(th)}
                className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
                  recTheme === th
                    ? "bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow-sm"
                    : "bg-stone-100 text-stone-500 hover:bg-stone-200"
                }`}>
                {lc.recThemes[th]}
              </button>
            ))}
          </div>

          <button type="button" onClick={onRecommend} disabled={!recCity || recLoading}
            className="btn-primary self-start px-6 py-2.5 disabled:opacity-50">
            {recLoading ? lc.recLoading : `✨ ${lc.recBtn}`}
          </button>

          {rec && !recLoading && (
            rec.stops.length === 0 ? (
              <p className="rounded-xl bg-amber-50 border border-amber-100 px-4 py-3 text-sm text-amber-700">
                {lc.recEmpty}
              </p>
            ) : (
              <div className="flex flex-col gap-3">
                <TripMap points={rec.stops.map((s) => ({
                  key: String(s.order), name: s.name, latitude: s.latitude, longitude: s.longitude,
                }))} />

                <ol className="flex flex-col gap-1.5">
                  {rec.stops.map((s) => {
                    const added = waypoints.some((w) => w.placeName === s.name && w.latitude === s.latitude);
                    return (
                      <li key={s.order} className="flex items-start gap-2.5 rounded-xl bg-stone-50 px-3 py-2">
                        <span className="mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-emerald-500 text-[11px] font-bold text-white">
                          {s.order}
                        </span>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-baseline gap-x-2">
                            <span className="text-sm font-semibold text-stone-800">{s.name}</span>
                            {s.category && <span className="text-xs text-stone-400">{s.category}</span>}
                            {s.distanceFromPrevMeters != null && (
                              <span className="text-xs text-teal-600">🚶 {s.distanceFromPrevMeters >= 1000
                                ? `${(s.distanceFromPrevMeters / 1000).toFixed(1)}km` : `${s.distanceFromPrevMeters}m`}</span>
                            )}
                          </div>
                          {s.address && <p className="truncate text-xs text-stone-400">{s.address}</p>}
                        </div>
                        <button type="button" onClick={() => addStopFromRec(s)} disabled={added}
                          className="flex-shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold text-emerald-600 transition-colors hover:bg-emerald-50 disabled:text-stone-300 disabled:hover:bg-transparent">
                          {added ? `✓ ${lc.addedWaypoint}` : lc.addWaypoint}
                        </button>
                      </li>
                    );
                  })}
                </ol>

                <p className="text-xs text-stone-500">
                  {lc.recTotalWalk} <b>{(rec.totalDistanceMeters / 1000).toFixed(1)}km</b>
                  <span className="mx-1.5 text-stone-300">·</span>
                  {lc.recSuggested} <b>~{rec.suggestedDurationHours}{lc.hoursUnit}</b>
                </p>

                <div className="flex flex-wrap gap-2">
                  <button type="button" onClick={onRecommend} className="btn-secondary px-4 py-2 text-sm">
                    🔄 {lc.recAgain}
                  </button>
                  <button type="button" onClick={useRecommendation} className="btn-primary px-4 py-2 text-sm">
                    ⬇️ {lc.recUse}
                  </button>
                </div>
                {recFilled && (
                  <p className="rounded-xl bg-emerald-50 border border-emerald-100 px-4 py-3 text-sm text-emerald-700">
                    ✅ {lc.recFilled}
                  </p>
                )}
              </div>
            )
          )}
        </div>

        {/* ── 새 코스 등록 폼 ── */}
        <form ref={formRef} onSubmit={onCreate} className="card mb-6 flex flex-col gap-4 p-6">
          <div className="flex items-center justify-between">
            <h2 className="font-bold text-stone-900">🎫 {editingId ? lc.editTitle : lc.createTitle}</h2>
            {editingId && (
              <button type="button" onClick={resetForm} className="text-sm font-semibold text-stone-400 hover:text-stone-700">
                {lc.cancelEdit}
              </button>
            )}
          </div>

          <div>
            <label className="input-label">{lc.titleLabel}</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)}
              placeholder={lc.titlePh} required className="input" />
          </div>

          <div>
            <label className="input-label">{lc.descLabel}</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder={lc.descPh} rows={4} className="input resize-none" />
          </div>

          <div>
            <label className="input-label">{lc.cityLabel}</label>
            <CitySelect value={city} onChange={(c) => setCity(c)} />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="input-label">{lc.durationLabel}</label>
              <div className="flex items-center gap-1.5">
                <input type="number" min={1} value={durationHours}
                  onChange={(e) => setDurationHours(Math.max(1, Number(e.target.value) || 1))}
                  className="input" />
                <span className="flex-shrink-0 text-xs text-stone-400">{lc.hoursUnit}</span>
              </div>
            </div>
            <div>
              <label className="input-label">{lc.priceLabel}</label>
              <input type="number" min={0} step={1000} value={price}
                onChange={(e) => setPrice(Math.max(0, Number(e.target.value) || 0))}
                className="input" />
            </div>
            <div>
              <label className="input-label">{lc.maxLabel}</label>
              <div className="flex items-center gap-1.5">
                <input type="number" min={1} value={maxPeople}
                  onChange={(e) => setMaxPeople(Math.max(1, Number(e.target.value) || 1))}
                  className="input" />
                <span className="flex-shrink-0 text-xs text-stone-400">{lc.peopleUnit}</span>
              </div>
            </div>
          </div>

          <div>
            <label className="input-label">{lc.imageLabel}</label>
            {imagePreview ? (
              <div className="relative">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={imagePreview} alt="preview" className="max-h-56 w-full rounded-xl object-cover" />
                <label className="absolute bottom-2 right-2 flex cursor-pointer items-center gap-1.5 rounded-full bg-black/50 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-black/70">
                  <CameraIcon className="h-3.5 w-3.5" /> {t.guidePosts.imageBtnChange}
                  <input type="file" accept="image/*" onChange={onImageChange} className="hidden" />
                </label>
              </div>
            ) : (
              <label className="group flex cursor-pointer items-center gap-3 rounded-xl border-2 border-dashed border-stone-200 p-4 transition-colors hover:border-emerald-300">
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-stone-100 text-stone-400 transition-colors group-hover:bg-emerald-50 group-hover:text-emerald-400">
                  <CameraIcon className="h-5 w-5" />
                </span>
                <span className="text-sm text-stone-400 transition-colors group-hover:text-emerald-500">{t.guidePosts.imageBtnAdd}</span>
                <span className="ml-auto text-xs text-stone-300">{t.guidePosts.imageHint}</span>
                <input type="file" accept="image/*" onChange={onImageChange} className="hidden" />
              </label>
            )}
          </div>

          {/* ── 동선(waypoints) 편집 — 위 추천 패널에서 담거나, 이미 담긴 코스를 수정할 때 표시 ── */}
          {waypoints.length > 0 && (
            <div>
              <label className="input-label">{lc.waypointsSection}</label>
              <p className="mb-2 text-xs text-stone-400">{lc.waypointsHint}</p>
              <TripMap points={waypoints.map((w, i) => ({
                key: String(i), name: w.placeName, latitude: w.latitude, longitude: w.longitude,
              }))} className="mb-2" />
              <ol className="flex flex-col gap-1.5">
                {waypoints.map((w, i) => (
                  <li key={i} className="flex items-center gap-2.5 rounded-xl bg-stone-50 px-3 py-2">
                    <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-emerald-500 text-[11px] font-bold text-white">
                      {i + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-stone-800">{w.placeName}</p>
                      {w.address && <p className="truncate text-xs text-stone-400">{w.address}</p>}
                    </div>
                    <button type="button" onClick={() => moveWaypoint(i, -1)} disabled={i === 0}
                      className="btn-ghost h-7 w-7 flex-shrink-0 px-0 py-0 text-xs disabled:opacity-30" title={lc.moveUp}>▲</button>
                    <button type="button" onClick={() => moveWaypoint(i, 1)} disabled={i === waypoints.length - 1}
                      className="btn-ghost h-7 w-7 flex-shrink-0 px-0 py-0 text-xs disabled:opacity-30" title={lc.moveDown}>▼</button>
                    <button type="button" onClick={() => removeWaypoint(i)}
                      className="flex-shrink-0 text-xs font-semibold text-red-400 hover:text-red-600">
                      {lc.removeWaypoint}
                    </button>
                  </li>
                ))}
              </ol>
            </div>
          )}

          <button type="submit" disabled={creating || !title.trim()} className="btn-primary self-start px-6 py-2.5">
            {creating ? (editingId ? lc.updating : lc.creating) : (editingId ? lc.updateBtn : lc.createBtn)}
          </button>
        </form>

        {/* ── 내 코스 목록 ── */}
        {courses === null ? (
          <div className="flex flex-col gap-3">
            {[1, 2].map((i) => <div key={i} className="card h-28 animate-pulse" />)}
          </div>
        ) : courses.length === 0 ? (
          <div className="card p-8 py-14 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 text-2xl shadow-md">
              🎫
            </div>
            <p className="mx-auto max-w-xs text-sm leading-relaxed text-stone-500">{lc.myEmpty}</p>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {courses.map((c) => {
              const expanded = expandedCourseId === c.id;
              return (
                <div key={c.id} className="card overflow-hidden p-4">
                  <div className="flex gap-4">
                    {c.imageUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={c.imageUrl} alt={c.title} className="h-24 w-24 flex-shrink-0 rounded-xl object-cover" />
                    ) : (
                      <div className="flex h-24 w-24 flex-shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-400 to-teal-500 text-2xl">
                        🎫
                      </div>
                    )}
                    <div className="min-w-0 flex-1">
                      <h3 className="truncate font-bold text-stone-900">{c.title}</h3>
                      {c.description && (
                        <p className="mt-0.5 text-sm text-stone-500 line-clamp-2">{c.description}</p>
                      )}
                      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-stone-400">
                        {c.city && <span className="flex items-center gap-1"><PinIcon className="h-3 w-3" /> {c.city}</span>}
                        <span>⏱ {c.durationHours}{lc.hoursUnit}</span>
                        <span>👥 {lc.upTo} {c.maxPeople}{lc.peopleUnit}</span>
                        <span className="font-bold text-stone-700">{c.price.toLocaleString()} {c.currency}/{lc.perPerson}</span>
                      </div>
                      {c.waypoints.length > 0 && (
                        <button type="button" onClick={() => setExpandedCourseId(expanded ? null : c.id)}
                          className="mt-1.5 text-xs font-semibold text-emerald-600 hover:text-emerald-800">
                          {expanded ? `▲ ${lc.hideRoute}` : `▼ ${lc.viewRoute} (${c.waypoints.length}${lc.stopUnit})`}
                        </button>
                      )}
                    </div>
                    <div className="flex h-fit flex-shrink-0 flex-col gap-1.5">
                      <button onClick={() => onEdit(c)} className="btn-secondary px-3 py-1.5 text-xs">
                        {lc.editBtn}
                      </button>
                      <button onClick={() => onDelete(c.id)} className="btn-danger px-3 py-1.5 text-xs">
                        {lc.deleteBtn}
                      </button>
                    </div>
                  </div>

                  {expanded && c.waypoints.length > 0 && (
                    <div className="mt-4 border-t border-stone-100 pt-4">
                      <TripMap points={c.waypoints.map((w) => ({
                        key: String(w.sortOrder), name: w.placeName, latitude: w.latitude, longitude: w.longitude,
                      }))} className="mb-3" />
                      <ol className="flex flex-col gap-1.5">
                        {c.waypoints.map((w, i) => (
                          <li key={w.sortOrder} className="flex items-start gap-2.5 rounded-xl bg-stone-50 px-3 py-2">
                            <span className="mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-emerald-500 text-[11px] font-bold text-white">
                              {i + 1}
                            </span>
                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-baseline gap-x-2">
                                <span className="text-sm font-semibold text-stone-800">{w.placeName}</span>
                                {w.category && <span className="text-xs text-stone-400">{w.category}</span>}
                              </div>
                              {w.address && <p className="truncate text-xs text-stone-400">{w.address}</p>}
                            </div>
                          </li>
                        ))}
                      </ol>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </main>
  );
}
