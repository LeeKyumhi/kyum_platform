"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import InterestPicker from "@/components/InterestPicker";
import CitySelect from "@/components/CitySelect";
import { PinIcon } from "@/components/icons";

const MBTI_TYPES = [
  ["INTJ", "INTP", "ENTJ", "ENTP"],
  ["INFJ", "INFP", "ENFJ", "ENFP"],
  ["ISTJ", "ISFJ", "ESTJ", "ESFJ"],
  ["ISTP", "ISFP", "ESTP", "ESFP"],
] as const;

type Me = { id: number; fullName: string; email: string; city: string | null; mbti: string | null; interests: string[]; gender: string | null };

export default function TravelerProfilePage() {
  const router = useRouter();
  const { t }  = useLanguage();
  const ltp    = t.travelerProfile;
  const lper   = t.personality;

  const [me, setMe]                 = useState<Me | null>(null);
  const [editMbti, setEditMbti]     = useState("");
  const [editGender, setEditGender] = useState("");
  const [editInterests, setEditInterests] = useState<string[]>([]);
  const [editing, setEditing]       = useState(false);
  const [saving, setSaving]         = useState(false);
  const [saved, setSaved]           = useState(false);
  const [error, setError]           = useState("");

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Me>("/api/users/me", { auth: true }).then((data) => {
      setMe(data);
      setEditMbti(data.mbti ?? "");
      setEditGender(data.gender ?? "");
      setEditInterests(data.interests ?? []);
    }).catch(() => router.replace("/login"));
  }, [router]);

  async function onSave() {
    setError(""); setSaving(true); setSaved(false);
    try {
      const updated = await api<Me>("/api/users/me/personality", {
        method: "PATCH", auth: true,
        body: { mbti: editMbti || null, interests: editInterests, gender: editGender || null },
      });
      setMe(updated);
      setEditing(false);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setSaving(false); }
  }

  async function onSetLocation(city: string, lat: number | null, lng: number | null) {
    if (!me || !city) return;
    setError("");
    try {
      const updated = await api<Me>("/api/users/me/location", {
        method: "PATCH", auth: true,
        body: { city, latitude: lat, longitude: lng },
      });
      setMe(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    }
  }

  function startEdit() {
    if (!me) return;
    setEditMbti(me.mbti ?? "");
    setEditGender(me.gender ?? "");
    setEditInterests(me.interests ?? []);
    setEditing(true);
    setSaved(false);
  }

  const genderText = (g: string) =>
    g === "male" ? lper.genderMale : g === "female" ? lper.genderFemale : lper.genderOther;

  const GENDER_OPTIONS = [
    { value: "male",   label: lper.genderMale },
    { value: "female", label: lper.genderFemale },
    { value: "other",  label: lper.genderOther },
  ];

  if (!me) return (
    <main className="page flex items-center justify-center">
      <div className="text-sm text-stone-400">{t.common.loading}</div>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 flex items-center gap-3">
          <Link href="/traveler" className="btn-ghost text-sm">{t.common.back}</Link>
          <h1 className="section-title">{ltp.title}</h1>
        </div>

        {/* 기본 정보 */}
        <div className="card mb-5 p-6">
          <div className="flex items-center gap-4">
            <div className="flex h-16 w-16 flex-shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl font-bold text-white shadow-sm">
              {me.fullName.slice(0, 1).toUpperCase()}
            </div>
            <div className="min-w-0">
              <p className="text-lg font-bold text-stone-900">{me.fullName}</p>
              <p className="truncate text-sm text-stone-400">{me.email}</p>
            </div>
          </div>
        </div>

        {/* 여행 도시 */}
        <div className="card mb-5 p-6">
          <h2 className="mb-4 flex items-center gap-1.5 font-bold text-stone-900">
            <PinIcon className="h-5 w-5 text-sky-500" /> {t.location.cityLabel}
          </h2>
          <CitySelect
            value={me.city ?? ""}
            onChange={(city, lat, lng) => onSetLocation(city, lat, lng)}
          />
        </div>

        {/* MBTI + 관심사 */}
        <div className="card mb-5 p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-bold text-stone-900">✨ {lper.mbtiLabel} &amp; {ltp.interestsLabel}</h2>
            {!editing && (
              <button onClick={startEdit} className="btn-ghost px-3 py-1.5 text-sm">{ltp.editBtn}</button>
            )}
            {saved && (
              <span className="text-sm font-medium text-emerald-600">{ltp.saved}</span>
            )}
          </div>

          {!editing ? (
            /* 보기 모드 */
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <span className="w-16 text-xs font-semibold uppercase tracking-wide text-stone-400">{lper.mbtiLabel}</span>
                {me.mbti ? (
                  <span className="rounded-lg bg-violet-100 px-3 py-1 text-sm font-bold text-violet-700">{me.mbti}</span>
                ) : (
                  <span className="text-sm text-stone-300">—</span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <span className="w-16 text-xs font-semibold uppercase tracking-wide text-stone-400">{lper.genderLabel}</span>
                {me.gender ? (
                  <span className="badge-gray py-1">{genderText(me.gender)}</span>
                ) : (
                  <span className="text-sm text-stone-300">—</span>
                )}
              </div>
              <div className="flex items-start gap-2">
                <span className="mt-1 w-16 text-xs font-semibold uppercase tracking-wide text-stone-400">{ltp.interestsLabel}</span>
                {me.interests && me.interests.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {me.interests.map((k) => (
                      <span key={k} className="badge-indigo py-1">
                        {t.interests[k as keyof typeof t.interests] ?? k}
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-sm text-stone-300">—</span>
                )}
              </div>
              {!me.mbti && (!me.interests || me.interests.length === 0) && (
                <p className="py-2 text-center text-sm text-stone-400">{ltp.interestsHint}</p>
              )}
            </div>
          ) : (
            /* 편집 모드 */
            <div className="flex flex-col gap-5">
              {/* MBTI picker */}
              <div>
                <p className="input-label">{lper.mbtiLabel}</p>
                <div className="flex flex-col gap-2">
                  {MBTI_TYPES.map((row, ri) => (
                    <div key={ri} className="grid grid-cols-4 gap-1.5">
                      {row.map((type) => (
                        <button
                          key={type}
                          type="button"
                          onClick={() => setEditMbti(editMbti === type ? "" : type)}
                          className={`rounded-xl border py-1.5 text-xs font-bold transition-colors ${
                            editMbti === type
                              ? "border-violet-600 bg-violet-600 text-white"
                              : "border-stone-200 bg-white text-stone-500 hover:border-violet-300 hover:text-violet-600"
                          }`}
                        >
                          {type}
                        </button>
                      ))}
                    </div>
                  ))}
                </div>
              </div>

              {/* 성별 선택 */}
              <div>
                <p className="input-label">{lper.genderLabel}</p>
                <div className="grid grid-cols-3 gap-2">
                  {GENDER_OPTIONS.map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => setEditGender(editGender === opt.value ? "" : opt.value)}
                      className={`justify-center ${editGender === opt.value ? "chip-active" : "chip"}`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* 관심사 피커 (카테고리별) */}
              <div>
                <p className="input-label !mb-3">{ltp.interestsLabel}</p>
                <InterestPicker selected={editInterests} onChange={setEditInterests} />
              </div>

              {error && (
                <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-2 text-sm text-red-600">{error}</p>
              )}

              <div className="flex gap-2">
                <button onClick={onSave} disabled={saving} className="btn-primary px-5 py-2 text-sm">
                  {saving ? ltp.saving : ltp.saveBtn}
                </button>
                <button onClick={() => setEditing(false)} className="btn-ghost px-4 py-2 text-sm">
                  {ltp.cancelBtn}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
