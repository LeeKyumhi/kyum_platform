"use client";

// 가이드 투어 코스 관리 — 고정 코스 상품 등록/삭제.
// 동선은 여행자 일정과 동일한 드래그 타임테이블(TimetableBuilder, course 모드)로 구성한다:
// 우측 팔레트(장소 검색 + ✨코스 추천)에서 끌어다 시간표에 놓는다. 시간/레인은 저장·복원된다.
// 등록된 코스는 /guides의 "투어 코스" 탭과 가이드 상세 페이지에 노출된다.

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, apiUpload, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect, { loadCities, cityLabel, type City } from "@/components/CitySelect";
import { SERVICE_CATEGORIES, type ServiceCategoryKey } from "@/lib/serviceCategories";
import TripMap from "@/components/TripMap";
import TimetableBuilder, { type BuilderItem, type RecResponse, newItemKey } from "@/components/TimetableBuilder";
import { CameraIcon, PinIcon } from "@/components/icons";

type Waypoint = {
  sortOrder: number; placeId: string | null; placeName: string; category: string | null;
  address: string | null; latitude: number | null; longitude: number | null;
  startHour: number | null; durationHours: number | null; laneIndex: number | null; laneSpan: number | null;
};

type Course = {
  id: number; title: string; description: string | null; city: string | null;
  durationHours: number; price: number; currency: string; maxPeople: number;
  imageUrl: string | null; serviceCategory: string | null; waypoints: Waypoint[];
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
  const [serviceCategory, setServiceCategory] = useState("");
  const [verified, setVerified] = useState(false);
  const [image, setImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [items, setItems] = useState<BuilderItem[]>([]);   // 동선 = 타임테이블 블록
  const [editingId, setEditingId] = useState<number | null>(null);
  const [recFilled, setRecFilled] = useState(false);
  const [expandedCourseId, setExpandedCourseId] = useState<number | null>(null);
  const formRef = useRef<HTMLDivElement>(null);

  const load = useCallback(() => {
    api<Course[]>("/api/guide-profiles/me/courses", { auth: true })
      .then(setCourses)
      .catch(() => router.replace("/guide"));
  }, [router]);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load();
    // 관광 카테고리 선택 가능 여부(인증 상태) 확인
    api<{ verificationStatus: string }>("/api/guide-profiles/me", { auth: true })
      .then((p) => setVerified(p.verificationStatus === "VERIFIED"))
      .catch(() => {});
  }, [router, load]);

  function onImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setImage(file);
    setImagePreview(file ? URL.createObjectURL(file) : null);
  }

  function resetForm() {
    setTitle(""); setDescription(""); setImage(null); setImagePreview(null);
    setItems([]); setEditingId(null); setRecFilled(false); setServiceCategory("");
  }

  /** 저장된 waypoint → 타임테이블 블록. 시간 있으면 복원, 없으면(레거시/추천) 10시부터 순차 배치. */
  function waypointToItem(w: Waypoint, i: number): BuilderItem {
    return {
      _k: newItemKey(), dayIndex: 1, sortOrder: i,
      placeId: w.placeId, placeName: w.placeName, category: w.category,
      address: w.address, latitude: w.latitude, longitude: w.longitude, memo: null,
      startHour: w.startHour ?? Math.min(23, 10 + i),
      durationHours: w.durationHours ?? 1,
      laneIndex: w.laneIndex ?? 0,
      laneSpan: w.laneSpan ?? 1,
      sourceCourseId: null,
    };
  }

  function onEdit(c: Course) {
    setEditingId(c.id);
    setTitle(c.title);
    setDescription(c.description ?? "");
    setCity(c.city ?? "");
    setDurationHours(c.durationHours);
    setPrice(c.price);
    setMaxPeople(c.maxPeople);
    setServiceCategory(c.serviceCategory ?? "");
    setImage(null);
    setImagePreview(c.imageUrl);
    setItems(c.waypoints.map(waypointToItem));
    setRecFilled(false);
    formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  /** 추천 결과를 폼(제목·소개·도시·소요)에 채우고, 정차지를 시간표에 순차 배치한다. */
  async function fillFromRec(rec: RecResponse) {
    if (rec.stops.length === 0) return;
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
    setItems(rec.stops.map((s, i) => ({
      _k: newItemKey(), dayIndex: 1, sortOrder: i,
      placeId: null, placeName: s.name, category: s.category,
      address: s.address, latitude: s.latitude, longitude: s.longitude, memo: null,
      startHour: Math.min(23, 10 + i), durationHours: 1, laneIndex: 0, laneSpan: 1, sourceCourseId: null,
    })));
    setRecFilled(true);
    formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  async function onSubmit() {
    if (!title.trim()) return;
    if (!serviceCategory) { setError(t.serviceCategories.pickPrompt); return; }
    setError(""); setCreating(true);
    try {
      // 배치 블록을 시간→레인 순으로 정렬해 sortOrder 재부여 (미배치는 뒤로). 시간/레인도 함께 저장.
      const ordered = [...items].sort((a, b) => {
        const ah = a.startHour ?? 99, bh = b.startHour ?? 99;
        return (ah - bh) || ((a.laneIndex ?? 0) - (b.laneIndex ?? 0));
      });
      const waypoints = ordered.map((it, idx) => ({
        sortOrder: idx, placeId: it.placeId, placeName: it.placeName, category: it.category,
        address: it.address, latitude: it.latitude, longitude: it.longitude,
        startHour: it.startHour, durationHours: it.durationHours,
        laneIndex: it.laneIndex, laneSpan: it.laneSpan,
      }));

      const fd = new FormData();
      fd.append("title", title.trim());
      if (description.trim()) fd.append("description", description.trim());
      if (city) fd.append("city", city);
      fd.append("durationHours", String(durationHours));
      fd.append("price", String(price));
      fd.append("maxPeople", String(maxPeople));
      fd.append("serviceCategory", serviceCategory);
      if (image) fd.append("image", image);
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
      <div className="mx-auto w-full max-w-6xl px-4">
        <div className="mb-2 flex items-center gap-3">
          <Link href="/guide" className="btn-ghost text-sm">← {t.nav.guideHome}</Link>
          <h1 className="section-title">{lc.manageTitle}</h1>
        </div>
        <p className="section-subtitle mb-6">{lc.manageSub}</p>

        {error && (
          <div className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>
        )}

        {/* ── 코스 메타 폼 ── */}
        <div ref={formRef} className="card mb-5 flex flex-col gap-4 p-6">
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
              placeholder={lc.titlePh} className="input" />
          </div>

          <div>
            <label className="input-label">{t.serviceCategories.sectionTitle}</label>
            <select value={serviceCategory} onChange={(e) => setServiceCategory(e.target.value)} className="input">
              <option value="">—</option>
              <optgroup label={t.serviceCategories.tourGroup}>
                {SERVICE_CATEGORIES.filter((c) => c.requiresLicense).map((c) => (
                  <option key={c.key} value={c.key} disabled={!verified}>
                    {t.serviceCategories[c.key as ServiceCategoryKey]}{!verified ? " 🔒" : ""}
                  </option>
                ))}
              </optgroup>
              <optgroup label={t.serviceCategories.nonTourGroup}>
                {SERVICE_CATEGORIES.filter((c) => !c.requiresLicense).map((c) => (
                  <option key={c.key} value={c.key}>{t.serviceCategories[c.key as ServiceCategoryKey]}</option>
                ))}
              </optgroup>
            </select>
            {!verified && <p className="mt-1 text-xs text-amber-600">{t.serviceCategories.lockedHint}</p>}
          </div>

          <div>
            <label className="input-label">{lc.descLabel}</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder={lc.descPh} rows={4} className="input resize-none" />
          </div>

          <div>
            <label className="input-label">{lc.cityLabel}</label>
            <CitySelect value={city} onChange={(c) => setCity(c)} />
            <p className="mt-1 text-xs text-stone-400">{lc.cityDrivesPalette}</p>
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

          {recFilled && (
            <p className="rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
              ✅ {lc.recFilled}
            </p>
          )}
        </div>

        {/* ── 동선 = 드래그 타임테이블 (장소 검색 + ✨추천 팔레트) ── */}
        <div className="mb-2">
          <h2 className="mb-1 font-bold text-stone-900">🗺️ {lc.routeBuilderTitle}</h2>
          <p className="section-subtitle mb-4">{lc.routeBuilderSub}</p>
          <TimetableBuilder
            items={items} onItemsChange={setItems}
            city={city} mode="course" singleDay
            onFillFormFromRec={fillFromRec}
          />
        </div>

        <button type="button" onClick={onSubmit} disabled={creating || !title.trim()}
          className="btn-primary mb-8 w-full py-3 sm:w-auto sm:px-8">
          {creating ? (editingId ? lc.updating : lc.creating) : (editingId ? lc.updateBtn : lc.createBtn)}
        </button>

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
