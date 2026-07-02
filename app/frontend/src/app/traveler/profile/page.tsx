"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import InterestPicker from "@/components/InterestPicker";
import CitySelect from "@/components/CitySelect";

const MBTI_TYPES = [
  ["INTJ", "INTP", "ENTJ", "ENTP"],
  ["INFJ", "INFP", "ENFJ", "ENFP"],
  ["ISTJ", "ISFJ", "ESTJ", "ESFJ"],
  ["ISTP", "ISFP", "ESTP", "ESFP"],
] as const;

type Me = { id: number; fullName: string; email: string; city: string | null; mbti: string | null; interests: string[] };

export default function TravelerProfilePage() {
  const router = useRouter();
  const { t }  = useLanguage();
  const ltp    = t.travelerProfile;
  const lper   = t.personality;

  const [me, setMe]                 = useState<Me | null>(null);
  const [editMbti, setEditMbti]     = useState("");
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
      setEditInterests(data.interests ?? []);
    }).catch(() => router.replace("/login"));
  }, [router]);

  async function onSave() {
    setError(""); setSaving(true); setSaved(false);
    try {
      const updated = await api<Me>("/api/users/me/personality", {
        method: "PATCH", auth: true,
        body: { mbti: editMbti || null, interests: editInterests },
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
    setEditInterests(me.interests ?? []);
    setEditing(true);
    setSaved(false);
  }

  if (!me) return (
    <main className="page flex items-center justify-center">
      <div className="text-gray-400 text-sm">{t.common.loading}</div>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center gap-3 mb-6">
          <Link href="/traveler" className="btn-ghost text-sm">{t.common.back}</Link>
          <h1 className="section-title">{ltp.title}</h1>
        </div>

        {/* 기본 정보 */}
        <div className="card p-6 mb-5">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 flex items-center justify-center text-white text-2xl font-bold flex-shrink-0">
              {me.fullName.slice(0, 1).toUpperCase()}
            </div>
            <div>
              <p className="font-bold text-gray-900 text-lg">{me.fullName}</p>
              <p className="text-sm text-gray-400">{me.email}</p>
            </div>
          </div>
        </div>

        {/* 여행 도시 */}
        <div className="card p-6 mb-5">
          <h2 className="font-semibold text-gray-900 mb-4">📍 {t.location.cityLabel}</h2>
          <CitySelect
            value={me.city ?? ""}
            onChange={(city, lat, lng) => onSetLocation(city, lat, lng)}
          />
        </div>

        {/* MBTI + 관심사 */}
        <div className="card p-6 mb-5">
          <div className="flex items-center justify-between mb-5">
            <h2 className="font-semibold text-gray-900">✨ {lper.mbtiLabel} &amp; {ltp.interestsLabel}</h2>
            {!editing && (
              <button onClick={startEdit} className="btn-ghost text-sm px-3 py-1.5">{ltp.editBtn}</button>
            )}
            {saved && (
              <span className="text-sm text-emerald-600 font-medium">{ltp.saved}</span>
            )}
          </div>

          {!editing ? (
            /* 보기 모드 */
            <div className="flex flex-col gap-4">
              <div className="flex items-center gap-3">
                <span className="text-xs text-gray-400 w-16">{lper.mbtiLabel}</span>
                {me.mbti ? (
                  <span className="rounded-lg bg-violet-100 text-violet-700 px-3 py-1 text-sm font-bold">{me.mbti}</span>
                ) : (
                  <span className="text-sm text-gray-300">—</span>
                )}
              </div>
              <div className="flex items-start gap-3">
                <span className="text-xs text-gray-400 w-16 mt-1">{ltp.interestsLabel}</span>
                {me.interests && me.interests.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {me.interests.map((k) => (
                      <span key={k} className="rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100 px-2.5 py-0.5 text-xs font-medium">
                        {t.interests[k as keyof typeof t.interests] ?? k}
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-sm text-gray-300">—</span>
                )}
              </div>
              {!me.mbti && (!me.interests || me.interests.length === 0) && (
                <p className="text-sm text-gray-400 text-center py-2">{ltp.interestsHint}</p>
              )}
            </div>
          ) : (
            /* 편집 모드 */
            <div className="flex flex-col gap-6">
              {/* MBTI picker */}
              <div>
                <p className="text-sm font-medium text-gray-700 mb-3">{lper.mbtiLabel}</p>
                <div className="flex flex-col gap-2">
                  {MBTI_TYPES.map((row, ri) => (
                    <div key={ri} className="grid grid-cols-4 gap-1.5">
                      {row.map((type) => (
                        <button
                          key={type}
                          type="button"
                          onClick={() => setEditMbti(editMbti === type ? "" : type)}
                          className={`rounded-xl py-2 text-sm font-bold border-2 transition-all ${
                            editMbti === type
                              ? "bg-violet-600 text-white border-violet-600 shadow-md"
                              : "bg-gray-50 text-gray-500 border-gray-200 hover:border-violet-300 hover:text-violet-600"
                          }`}
                        >
                          {type}
                        </button>
                      ))}
                    </div>
                  ))}
                </div>
              </div>

              {/* 관심사 피커 (카테고리별) */}
              <div>
                <p className="text-sm font-medium text-gray-700 mb-3">{ltp.interestsLabel}</p>
                <InterestPicker selected={editInterests} onChange={setEditInterests} />
              </div>

              {error && (
                <p className="rounded-xl bg-red-50 px-4 py-2 text-sm text-red-600 border border-red-100">{error}</p>
              )}

              <div className="flex gap-2">
                <button
                  onClick={onSave}
                  disabled={saving}
                  className="btn-primary text-sm py-2.5 px-6"
                >
                  {saving ? ltp.saving : ltp.saveBtn}
                </button>
                <button
                  onClick={() => setEditing(false)}
                  className="btn-ghost text-sm py-2.5 px-4"
                >
                  취소
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
