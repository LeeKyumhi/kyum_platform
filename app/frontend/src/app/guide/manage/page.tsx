"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, apiUpload, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import InterestPicker from "@/components/InterestPicker";
import ServiceCategoryPicker from "@/components/ServiceCategoryPicker";
import CitySelect from "@/components/CitySelect";
import { PinIcon, CheckBadgeIcon, CameraIcon } from "@/components/icons";

const CRED_KEYS = ["EDUCATION", "CERTIFICATE", "LICENSE"] as const;
const MBTI_TYPES = [
  ["INTJ","INTP","ENTJ","ENTP"],
  ["INFJ","INFP","ENFJ","ENFP"],
  ["ISTJ","ISFJ","ESTJ","ESFJ"],
  ["ISTP","ISFP","ESTP","ESFP"],
] as const;

type Profile    = { id: number; headline: string; region: string; city: string | null; hourlyRate: number; currency: string; avatarUrl: string | null; active: boolean; instantBooking: boolean; mbti: string | null; interests: string[]; serviceCategories: string[]; verificationStatus: string };
type Credential = { id: number; type: string; title: string; fileUrl: string };
type UserMe     = { id: number; mbti: string | null; interests: string[]; gender: string | null };
type Verification = { status: string; legalName: string | null; licenseNumber: string | null; licenseIssueDate: string | null; licenseInnerNumber: string | null; rejectReason: string | null };

export default function GuideManagePage() {
  const router      = useRouter();
  const { t } = useLanguage();
  const l           = t.guideManage;
  const lper        = t.personality;
  const lv          = t.verification;

  const [profile, setProfile]         = useState<Profile | null>(null);
  const [credentials, setCredentials] = useState<Credential[]>([]);
  const [error, setError]             = useState("");
  const [noProfile, setNoProfile]     = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [togglingActive, setTogglingActive]   = useState(false);
  const [togglingInstant, setTogglingInstant] = useState(false);

  // Personality (MBTI + interests + gender)
  const [userMe, setUserMe]             = useState<UserMe | null>(null);
  const [editingPersonality, setEditingPersonality] = useState(false);
  const [editMbti, setEditMbti]         = useState("");
  const [editGender, setEditGender]     = useState("");
  const [editInterests, setEditInterests] = useState<string[]>([]);
  const [savingPersonality, setSavingPersonality] = useState(false);

  // Credential form
  const [credType, setCredType]   = useState("CERTIFICATE");
  const [credTitle, setCredTitle] = useState("");
  const [credFile, setCredFile]   = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);

  // 제공 서비스 카테고리
  const [editServiceCategories, setEditServiceCategories] = useState<string[]>([]);
  const [savingServiceCategories, setSavingServiceCategories] = useState(false);

  // 관광통역안내사 인증 신청
  const [verification, setVerification] = useState<Verification | null>(null);
  const [vLegalName, setVLegalName]         = useState("");
  const [vLicenseNumber, setVLicenseNumber] = useState("");
  const [vIssueDate, setVIssueDate]         = useState("");
  const [vInnerNumber, setVInnerNumber]     = useState("");
  const [vLicenseFile, setVLicenseFile]     = useState<File | null>(null);
  const [vIdDocFile, setVIdDocFile]         = useState<File | null>(null);
  const [vSubmitting, setVSubmitting]       = useState(false);

  const loadCredentials = useCallback(async () => {
    const creds = await api<Credential[]>("/api/guide-profiles/me/credentials", { auth: true });
    setCredentials(creds);
  }, []);

  const loadVerification = useCallback(async () => {
    try {
      const v = await api<Verification>("/api/guide-profiles/me/verification", { auth: true });
      setVerification(v);
    } catch { /* 프로필 없음 등 — 조용히 무시 */ }
  }, []);

  const loadProfile = useCallback(async () => {
    try {
      const p = await api<Profile>("/api/guide-profiles/me", { auth: true });
      setProfile(p);
      setEditServiceCategories(p.serviceCategories ?? []);
      await loadCredentials();
      await loadVerification();
    } catch { setNoProfile(true); }
  }, [loadCredentials, loadVerification]);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    loadProfile();
    // Gender lives on the User account (not GuideProfile) — fetch it separately.
    api<UserMe>("/api/users/me", { auth: true }).then(setUserMe).catch(() => {});
  }, [router, loadProfile]);

  function startEditPersonality() {
    if (!profile) return;
    setEditMbti(profile.mbti ?? "");
    setEditInterests(profile.interests ?? []);
    setEditGender(userMe?.gender ?? "");
    setEditingPersonality(true);
  }

  async function onSavePersonality() {
    setError(""); setSavingPersonality(true);
    try {
      const updated = await api<Profile>("/api/guide-profiles/me/personality", {
        method: "PATCH", auth: true,
        body: { mbti: editMbti || null, interests: editInterests },
      });
      setProfile(updated);
      // Gender is stored on the User account — save via the users endpoint,
      // resending the user's own mbti/interests unchanged so they aren't cleared.
      if (userMe) {
        const updatedUser = await api<UserMe>("/api/users/me/personality", {
          method: "PATCH", auth: true,
          body: { mbti: userMe.mbti, interests: userMe.interests ?? [], gender: editGender || null },
        });
        setUserMe(updatedUser);
      }
      setEditingPersonality(false);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setSavingPersonality(false); }
  }

  const genderText = (g: string) =>
    g === "male" ? lper.genderMale : g === "female" ? lper.genderFemale : lper.genderOther;

  const GENDER_OPTIONS = [
    { value: "male",   label: lper.genderMale },
    { value: "female", label: lper.genderFemale },
    { value: "other",  label: lper.genderOther },
  ];

  async function onSetActive(next: boolean) {
    if (!profile || profile.active === next) return;
    setError(""); setTogglingActive(true);
    try {
      const updated = await api<Profile>("/api/guide-profiles/me/active", {
        method: "PATCH", auth: true,
        body: { active: next },
      });
      setProfile(updated);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setTogglingActive(false); }
  }

  async function onSetInstantBooking(next: boolean) {
    if (!profile || profile.instantBooking === next) return;
    setError(""); setTogglingInstant(true);
    try {
      const updated = await api<Profile>("/api/guide-profiles/me/instant-booking", {
        method: "PATCH", auth: true,
        body: { instantBooking: next },
      });
      setProfile(updated);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setTogglingInstant(false); }
  }

  async function onSetLocation(city: string, lat: number | null, lng: number | null) {
    if (!profile || !city) return;
    setError("");
    try {
      const updated = await api<Profile>("/api/guide-profiles/me/location", {
        method: "PATCH", auth: true,
        body: { city, latitude: lat, longitude: lng },
      });
      setProfile(updated);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
  }

  async function onAvatarChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]; if (!file) return;
    setError(""); setAvatarUploading(true);
    try {
      const fd = new FormData(); fd.append("file", file);
      const updated = await apiUpload<Profile>("/api/guide-profiles/me/avatar", fd, { auth: true });
      setProfile(updated);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setAvatarUploading(false); }
  }

  async function onCredentialSubmit(e: React.FormEvent) {
    e.preventDefault(); if (!credFile) { setError(l.credFileRequired); return; }
    setError(""); setUploading(true);
    try {
      const fd = new FormData(); fd.append("type", credType); fd.append("title", credTitle); fd.append("file", credFile);
      await apiUpload("/api/guide-profiles/me/credentials", fd, { auth: true });
      setCredTitle(""); setCredFile(null);
      (document.getElementById("credFileInput") as HTMLInputElement).value = "";
      await loadCredentials();
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setUploading(false); }
  }

  async function onSaveServiceCategories() {
    setError(""); setSavingServiceCategories(true);
    try {
      const updated = await api<Profile>("/api/guide-profiles/me/service-categories", {
        method: "PATCH", auth: true,
        body: { serviceCategories: editServiceCategories },
      });
      setProfile(updated);
      setEditServiceCategories(updated.serviceCategories ?? []);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setSavingServiceCategories(false); }
  }

  async function onVerificationSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!vLicenseFile || !vIdDocFile) { setError(lv.filesRequired); return; }
    setError(""); setVSubmitting(true);
    try {
      const fd = new FormData();
      fd.append("legalName", vLegalName);
      fd.append("licenseNumber", vLicenseNumber);
      if (vIssueDate) fd.append("issueDate", vIssueDate);
      if (vInnerNumber) fd.append("innerNumber", vInnerNumber);
      fd.append("licenseFile", vLicenseFile);
      fd.append("idDocFile", vIdDocFile);
      const updated = await apiUpload<Verification>("/api/guide-profiles/me/verification", fd, { auth: true });
      setVerification(updated);
      setVLicenseFile(null); setVIdDocFile(null);
      const lf = document.getElementById("vLicenseFileInput") as HTMLInputElement | null;
      const idf = document.getElementById("vIdDocFileInput") as HTMLInputElement | null;
      if (lf) lf.value = ""; if (idf) idf.value = "";
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setVSubmitting(false); }
  }

  if (noProfile) return (
    <main className="page flex flex-col items-center justify-center px-4 text-center">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 text-2xl shadow-md">📋</div>
      <p className="mb-1 font-bold text-stone-900">{l.noProfileTitle}</p>
      <p className="mb-5 text-sm text-stone-500">{l.noProfileDesc}</p>
      <Link href="/become-guide" className="btn-primary">{l.noProfileBtn}</Link>
    </main>
  );

  if (!profile) return (
    <main className="page flex items-center justify-center">
      <div className="text-sm text-stone-400">{t.common.loading}</div>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 flex items-center gap-3">
          <Link href="/guide" className="btn-ghost text-sm">{l.backHome}</Link>
          <h1 className="section-title">{l.title}</h1>
        </div>

        {error && (
          <div className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>
        )}

        {/* Profile card */}
        <div className="card mb-5 p-6">
          <div className="flex items-center gap-4">
            <div className="relative flex-shrink-0">
              {profile.avatarUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={profile.avatarUrl} alt="profile"
                  className="h-20 w-20 rounded-2xl object-cover shadow-sm ring-2 ring-stone-100" />
              ) : (
                <div className="flex h-20 w-20 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 text-3xl font-bold text-white shadow-sm">
                  {profile.headline.slice(0, 1)}
                </div>
              )}
              {avatarUploading && (
                <div className="absolute inset-0 flex items-center justify-center rounded-2xl bg-black/30">
                  <div className="h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                </div>
              )}
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="font-bold leading-snug text-stone-900">{profile.headline}</h2>
              <p className="mt-1 flex items-center gap-1 text-sm text-stone-500">
                <PinIcon className="h-3.5 w-3.5 flex-shrink-0" /> {profile.city ?? profile.region}
              </p>
              <p className="mt-0.5 text-sm">
                <span className="font-bold text-stone-900">{profile.hourlyRate.toLocaleString()}</span>
                <span className="text-xs font-medium text-stone-400"> {profile.currency}/{t.guides.perHour}</span>
              </p>
            </div>
          </div>

          <div className="mt-4 flex items-center gap-3">
            <label className="btn-secondary cursor-pointer px-4 py-2 text-xs">
              <CameraIcon className="h-4 w-4" />
              {profile.avatarUrl ? l.changePhoto : l.uploadPhoto}
              <input type="file" accept="image/*" onChange={onAvatarChange} className="hidden" />
            </label>
            <p className="text-xs text-stone-400">{l.photoHint}</p>
          </div>

          <div className="divider !my-5" />

          {/* 예약 상태 세그먼트 토글 */}
          <div>
            <label className="input-label">{l.activeLabel}</label>
            <div className="inline-flex rounded-full bg-stone-100 p-1">
              <button
                onClick={() => onSetActive(true)}
                disabled={togglingActive}
                className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-bold transition-all disabled:opacity-60 ${
                  profile.active
                    ? "bg-emerald-500 text-white shadow-sm"
                    : "text-stone-500 hover:text-stone-800"
                }`}
              >
                <span className={`h-2 w-2 rounded-full ${profile.active ? "animate-pulse-dot bg-white" : "bg-stone-300"}`} />
                {l.activeOn}
              </button>
              <button
                onClick={() => onSetActive(false)}
                disabled={togglingActive}
                className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-bold transition-all disabled:opacity-60 ${
                  !profile.active
                    ? "bg-stone-700 text-white shadow-sm"
                    : "text-stone-500 hover:text-stone-800"
                }`}
              >
                <span className={`h-2 w-2 rounded-full ${!profile.active ? "bg-white" : "bg-stone-300"}`} />
                {l.activeOff}
              </button>
            </div>
            <p className="mt-1.5 text-xs text-stone-400">{l.activeHint}</p>
          </div>

          <div className="divider !my-5" />

          {/* 즉시 예약 세그먼트 토글 */}
          <div>
            <label className="input-label">{l.instantLabel}</label>
            <div className="inline-flex rounded-full bg-stone-100 p-1">
              <button
                onClick={() => onSetInstantBooking(true)}
                disabled={togglingInstant}
                className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-bold transition-all disabled:opacity-60 ${
                  profile.instantBooking
                    ? "bg-amber-500 text-white shadow-sm"
                    : "text-stone-500 hover:text-stone-800"
                }`}
              >
                ⚡ {l.instantOn}
              </button>
              <button
                onClick={() => onSetInstantBooking(false)}
                disabled={togglingInstant}
                className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-bold transition-all disabled:opacity-60 ${
                  !profile.instantBooking
                    ? "bg-stone-700 text-white shadow-sm"
                    : "text-stone-500 hover:text-stone-800"
                }`}
              >
                {l.instantOff}
              </button>
            </div>
            <p className="mt-1.5 text-xs text-stone-400">{l.instantHint}</p>
          </div>

          {/* 활동 도시 변경 */}
          <div className="mt-5">
            <label className="input-label">{t.location.cityLabel}</label>
            <CitySelect
              value={profile.city ?? ""}
              onChange={(city, lat, lng) => onSetLocation(city, lat, lng)}
            />
          </div>
        </div>

        {/* Personality (MBTI + 관심사) */}
        <div className="card mb-5 p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-bold text-stone-900">✨ {lper.mbtiLabel} &amp; {lper.interestsLabel}</h2>
            {!editingPersonality && (
              <button onClick={startEditPersonality} className="btn-ghost px-3 py-1.5 text-sm">{lper.editBtn}</button>
            )}
          </div>

          {!editingPersonality ? (
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <span className="w-16 text-xs font-semibold uppercase tracking-wide text-stone-400">{lper.mbtiLabel}</span>
                {profile.mbti ? (
                  <span className="rounded-lg bg-violet-100 px-3 py-1 text-sm font-bold text-violet-700">{profile.mbti}</span>
                ) : (
                  <span className="text-sm text-stone-300">—</span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <span className="w-16 text-xs font-semibold uppercase tracking-wide text-stone-400">{lper.genderLabel}</span>
                {userMe?.gender ? (
                  <span className="badge-gray py-1">{genderText(userMe.gender)}</span>
                ) : (
                  <span className="text-sm text-stone-300">—</span>
                )}
              </div>
              <div className="flex items-start gap-2">
                <span className="mt-1 w-16 text-xs font-semibold uppercase tracking-wide text-stone-400">{lper.interestsLabel}</span>
                {profile.interests && profile.interests.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {profile.interests.map((k) => (
                      <span key={k} className="badge-emerald py-1">
                        {t.interests[k as keyof typeof t.interests] ?? k}
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-sm text-stone-300">—</span>
                )}
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-5">
              {/* MBTI picker */}
              <div>
                <p className="input-label">{lper.mbtiLabel}</p>
                <div className="flex flex-col gap-2">
                  {MBTI_TYPES.map((row, ri) => (
                    <div key={ri} className="grid grid-cols-4 gap-1.5">
                      {row.map((type) => (
                        <button key={type} type="button"
                          onClick={() => setEditMbti(editMbti === type ? "" : type)}
                          className={`rounded-xl border py-1.5 text-xs font-bold transition-colors ${
                            editMbti === type
                              ? "border-violet-600 bg-violet-600 text-white"
                              : "border-stone-200 bg-white text-stone-500 hover:border-violet-300 hover:text-violet-600"
                          }`}>{type}</button>
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
              {/* Interests picker (카테고리별) */}
              <div>
                <p className="input-label !mb-3">{lper.interestsLabel}</p>
                <InterestPicker selected={editInterests} onChange={setEditInterests} />
              </div>
              <div className="flex gap-2">
                <button onClick={onSavePersonality} disabled={savingPersonality} className="btn-primary px-5 py-2 text-sm">
                  {savingPersonality ? lper.saving : lper.saveBtn}
                </button>
                <button onClick={() => setEditingPersonality(false)} className="btn-ghost px-4 py-2 text-sm">{l.cancelBtn}</button>
              </div>
            </div>
          )}
        </div>

        {/* 제공 서비스 카테고리 */}
        <div className="card p-6">
          <h2 className="mb-1 font-bold text-stone-900">{t.serviceCategories.sectionTitle}</h2>
          <p className="mb-4 text-xs text-stone-500">{t.serviceCategories.sectionHint}</p>
          <ServiceCategoryPicker
            selected={editServiceCategories}
            onChange={setEditServiceCategories}
            verified={profile.verificationStatus === "VERIFIED"}
          />
          <button
            onClick={onSaveServiceCategories}
            disabled={savingServiceCategories}
            className="btn-primary mt-4 px-5 py-2 text-sm"
          >
            {savingServiceCategories ? t.serviceCategories.saving : t.serviceCategories.saveBtn}
          </button>
        </div>

        {/* 관광통역안내사 자격 인증 */}
        <div id="verification" className="card p-6">
          <h2 className="mb-1 flex items-center gap-1.5 font-bold text-stone-900">
            <CheckBadgeIcon className="h-5 w-5 text-emerald-500" /> {lv.section}
          </h2>
          <p className="mb-4 text-xs leading-relaxed text-stone-500">{lv.intro}</p>

          {verification?.status === "VERIFIED" && (
            <div className="rounded-xl bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700">
              ✅ {lv.statusVerified}
            </div>
          )}
          {verification?.status === "PENDING" && (
            <div className="rounded-xl bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-700">
              ⏳ {lv.statusPending}
            </div>
          )}
          {verification?.status === "REJECTED" && (
            <div className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
              <p className="font-semibold">⚠ {lv.statusRejected}</p>
              {verification.rejectReason && (
                <p className="mt-1 text-xs">{lv.rejectReasonLabel}: {verification.rejectReason}</p>
              )}
            </div>
          )}

          {(verification == null || verification.status === "NONE" || verification.status === "REJECTED") && (
            <form onSubmit={onVerificationSubmit} className="mt-3 flex flex-col gap-3 rounded-xl border border-stone-100 bg-stone-50 p-4">
              <div>
                <label className="input-label">{lv.legalNameLabel}</label>
                <input value={vLegalName} onChange={(e) => setVLegalName(e.target.value)}
                  placeholder={lv.legalNamePlaceholder} required className="input" />
                <p className="mt-1 text-xs text-stone-400">{lv.legalNameHint}</p>
              </div>
              <div>
                <label className="input-label">{lv.licenseNumberLabel}</label>
                <input value={vLicenseNumber} onChange={(e) => setVLicenseNumber(e.target.value)}
                  placeholder={lv.licenseNumberPlaceholder} required className="input" />
              </div>
              <div className="flex gap-2">
                <div className="flex-1">
                  <label className="input-label">{lv.issueDateLabel}</label>
                  <input type="date" value={vIssueDate} onChange={(e) => setVIssueDate(e.target.value)} className="input" />
                </div>
                <div className="flex-1">
                  <label className="input-label">{lv.innerNumberLabel}</label>
                  <input value={vInnerNumber} onChange={(e) => setVInnerNumber(e.target.value)}
                    placeholder={lv.innerNumberPlaceholder} className="input" />
                </div>
              </div>
              <div>
                <label className="input-label">{lv.licenseFileLabel}</label>
                <input id="vLicenseFileInput" type="file"
                  onChange={(e) => setVLicenseFile(e.target.files?.[0] ?? null)}
                  required className="text-sm text-stone-600 file:mr-3 file:cursor-pointer file:rounded-full file:border-0 file:bg-stone-200 file:px-4 file:py-1.5 file:text-xs file:font-semibold file:text-stone-700 hover:file:bg-stone-300" />
              </div>
              <div>
                <label className="input-label">{lv.idDocFileLabel}</label>
                <input id="vIdDocFileInput" type="file"
                  onChange={(e) => setVIdDocFile(e.target.files?.[0] ?? null)}
                  required className="text-sm text-stone-600 file:mr-3 file:cursor-pointer file:rounded-full file:border-0 file:bg-stone-200 file:px-4 file:py-1.5 file:text-xs file:font-semibold file:text-stone-700 hover:file:bg-stone-300" />
                <p className="mt-1 text-xs text-stone-400">{lv.idDocHint}</p>
              </div>
              <button type="submit" disabled={vSubmitting} className="btn-primary self-start px-5 py-2 text-sm">
                {vSubmitting ? lv.submitting : (verification?.status === "REJECTED" ? lv.resubmitBtn : lv.submitBtn)}
              </button>
            </form>
          )}
        </div>

        {/* Credentials */}
        <div className="card p-6">
          <h2 className="mb-4 flex items-center gap-1.5 font-bold text-stone-900">
            <CheckBadgeIcon className="h-5 w-5 text-emerald-500" /> {l.credSection}
          </h2>

          <form onSubmit={onCredentialSubmit} className="mb-5 flex flex-col gap-3 rounded-xl border border-stone-100 bg-stone-50 p-4">
            <div className="flex gap-2">
              <select value={credType} onChange={(e) => setCredType(e.target.value)} className="input w-32">
                {CRED_KEYS.map((k) => (
                  <option key={k} value={k}>{t.credType[k]}</option>
                ))}
              </select>
              <input placeholder={l.credTitlePlaceholder} value={credTitle}
                onChange={(e) => setCredTitle(e.target.value)} required className="input flex-1" />
            </div>
            <div>
              <label className="input-label">{l.credFileLabel}</label>
              <input id="credFileInput" type="file"
                onChange={(e) => setCredFile(e.target.files?.[0] ?? null)}
                required className="text-sm text-stone-600 file:mr-3 file:cursor-pointer file:rounded-full file:border-0 file:bg-stone-200 file:px-4 file:py-1.5 file:text-xs file:font-semibold file:text-stone-700 hover:file:bg-stone-300" />
            </div>
            <button type="submit" disabled={uploading} className="btn-primary self-start px-5 py-2 text-sm">
              {uploading ? l.credUploading : l.credAddBtn}
            </button>
          </form>

          {credentials.length === 0 ? (
            <p className="py-4 text-center text-sm text-stone-400">{l.noCredentials}</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {credentials.map((c) => (
                <li key={c.id} className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-3">
                  <span className="flex items-center gap-2 text-sm">
                    <span className="badge-gray">{t.credType[c.type as keyof typeof t.credType] ?? c.type}</span>
                    <span className="text-stone-700">{c.title}</span>
                  </span>
                  <a href={c.fileUrl} target="_blank" rel="noopener noreferrer"
                    className="text-xs font-semibold text-sky-500 hover:underline">{l.view}</a>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </main>
  );
}
