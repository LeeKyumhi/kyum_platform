"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, apiUpload, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import InterestPicker from "@/components/InterestPicker";
import SlotCalendar from "@/components/SlotCalendar";

const CRED_KEYS = ["EDUCATION", "CERTIFICATE", "LICENSE"] as const;
const MBTI_TYPES = [
  ["INTJ","INTP","ENTJ","ENTP"],
  ["INFJ","INFP","ENFJ","ENFP"],
  ["ISTJ","ISFJ","ESTJ","ESFJ"],
  ["ISTP","ISFP","ESTP","ESFP"],
] as const;

type Profile    = { id: number; headline: string; region: string; hourlyRate: number; currency: string; avatarUrl: string | null; active: boolean; mbti: string | null; interests: string[] };
type Credential = { id: number; type: string; title: string; fileUrl: string };
type GuidePost  = { id: number; content: string; imageUrl: string | null; createdAt: string };
type AvailableSlot = { id: number; guideProfileId: number; startAt: string; endAt: string };

export default function GuideManagePage() {
  const router      = useRouter();
  const { t, lang } = useLanguage();
  const l           = t.guideManage;
  const lp          = t.guidePosts;
  const lper        = t.personality;

  const [profile, setProfile]         = useState<Profile | null>(null);
  const [credentials, setCredentials] = useState<Credential[]>([]);
  const [posts, setPosts]             = useState<GuidePost[]>([]);
  const [slots, setSlots]             = useState<AvailableSlot[]>([]);
  const [error, setError]             = useState("");
  const [noProfile, setNoProfile]     = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [togglingActive, setTogglingActive]   = useState(false);


  // Personality (MBTI + interests)
  const [editingPersonality, setEditingPersonality] = useState(false);
  const [editMbti, setEditMbti]         = useState("");
  const [editInterests, setEditInterests] = useState<string[]>([]);
  const [savingPersonality, setSavingPersonality] = useState(false);

  // Post form
  const [postContent, setPostContent]   = useState("");
  const [postImage, setPostImage]       = useState<File | null>(null);
  const [postImagePreview, setPostImagePreview] = useState<string | null>(null);
  const [postSubmitting, setPostSubmitting]     = useState(false);

  // Credential form
  const [credType, setCredType]   = useState("CERTIFICATE");
  const [credTitle, setCredTitle] = useState("");
  const [credFile, setCredFile]   = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);

  const loadCredentials = useCallback(async () => {
    const creds = await api<Credential[]>("/api/guide-profiles/me/credentials", { auth: true });
    setCredentials(creds);
  }, []);

  const loadPosts = useCallback(async (profileId: number) => {
    const data = await api<GuidePost[]>(`/api/guides/${profileId}/posts`);
    setPosts(data);
  }, []);

  const loadSlots = useCallback(async (profileId: number) => {
    const data = await api<AvailableSlot[]>(`/api/guides/${profileId}/slots`);
    setSlots(data);
  }, []);

  const loadProfile = useCallback(async () => {
    try {
      const p = await api<Profile>("/api/guide-profiles/me", { auth: true });
      setProfile(p);
      await Promise.all([loadCredentials(), loadPosts(p.id), loadSlots(p.id)]);
    } catch { setNoProfile(true); }
  }, [loadCredentials, loadPosts, loadSlots]);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    loadProfile();
  }, [router, loadProfile]);

  function startEditPersonality() {
    if (!profile) return;
    setEditMbti(profile.mbti ?? "");
    setEditInterests(profile.interests ?? []);
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
      setEditingPersonality(false);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setSavingPersonality(false); }
  }

  async function onToggleActive() {
    if (!profile) return;
    setError(""); setTogglingActive(true);
    try {
      const updated = await api<Profile>("/api/guide-profiles/me/active", {
        method: "PATCH", auth: true,
        body: { active: !profile.active },
      });
      setProfile(updated);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setTogglingActive(false); }
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

  function onPostImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setPostImage(file);
    if (file) {
      const url = URL.createObjectURL(file);
      setPostImagePreview(url);
    } else {
      setPostImagePreview(null);
    }
  }

  async function onPostSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!postContent.trim() || !profile) return;
    setError(""); setPostSubmitting(true);
    try {
      const fd = new FormData();
      fd.append("content", postContent.trim());
      if (postImage) fd.append("image", postImage);
      await apiUpload<GuidePost>("/api/guide-profiles/me/posts", fd, { auth: true });
      setPostContent(""); setPostImage(null); setPostImagePreview(null);
      await loadPosts(profile.id);
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
    finally { setPostSubmitting(false); }
  }

  async function onDeletePost(postId: number) {
    if (!confirm(lp.deleteConfirm)) return;
    try {
      await api(`/api/guide-profiles/me/posts/${postId}`, { method: "DELETE", auth: true });
      setPosts((prev) => prev.filter((p) => p.id !== postId));
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
  }

  async function onAddSlot(startAt: string, endAt: string) {
    if (!profile) return;
    setError("");
    await api("/api/guide-profiles/me/slots", {
      method: "POST", auth: true,
      body: { startAt, endAt },
    });
    await loadSlots(profile.id);
  }

  async function onDeleteSlot(slotId: number) {
    if (!profile) return;
    try {
      await api(`/api/guide-profiles/me/slots/${slotId}`, { method: "DELETE", auth: true });
      setSlots((prev) => prev.filter((s) => s.id !== slotId));
    } catch (err) { setError(err instanceof Error ? err.message : t.common.error); }
  }

  async function onCredentialSubmit(e: React.FormEvent) {
    e.preventDefault(); if (!credFile) { setError("파일을 선택하세요."); return; }
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

  if (noProfile) return (
    <main className="page flex flex-col items-center justify-center px-4 text-center">
      <div className="text-4xl mb-3">📋</div>
      <p className="font-semibold text-gray-700 mb-1">{l.noProfileTitle}</p>
      <p className="text-sm text-gray-500 mb-5">{l.noProfileDesc}</p>
      <Link href="/become-guide" className="btn-primary">{l.noProfileBtn}</Link>
    </main>
  );

  if (!profile) return (
    <main className="page flex items-center justify-center">
      <div className="text-gray-400 text-sm">{t.common.loading}</div>
    </main>
  );

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center gap-3 mb-6">
          <Link href="/guide" className="btn-ghost text-sm">{l.backHome}</Link>
          <h1 className="section-title">{l.title}</h1>
        </div>

        {error && (
          <div className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 border border-red-100">{error}</div>
        )}

        {/* Profile card */}
        <div className="card p-6 mb-5">
          <div className="flex items-start justify-between mb-1">
            <h2 className="font-semibold text-gray-900">{profile.headline}</h2>
            <button
              onClick={onToggleActive}
              disabled={togglingActive}
              className={`flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold transition-colors ${
                profile.active
                  ? "bg-emerald-100 text-emerald-700 hover:bg-emerald-200"
                  : "bg-gray-100 text-gray-500 hover:bg-gray-200"
              } disabled:opacity-60`}
            >
              <span className={`h-2 w-2 rounded-full ${profile.active ? "bg-emerald-500" : "bg-gray-400"}`} />
              {togglingActive ? "..." : profile.active ? l.activeOn : l.activeOff}
            </button>
          </div>
          <p className="text-xs text-gray-400 mb-1">{l.activeHint}</p>
          <p className="text-sm text-gray-500 mb-4">📍 {profile.region} · {profile.hourlyRate.toLocaleString()} {profile.currency}/hr</p>

          <div className="flex items-center gap-5">
            <div className="relative">
              {profile.avatarUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={profile.avatarUrl} alt="profile"
                  className="w-20 h-20 rounded-2xl object-cover ring-2 ring-indigo-100" />
              ) : (
                <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-indigo-400 to-violet-500 flex items-center justify-center text-white text-3xl font-bold">
                  {profile.headline.slice(0, 1)}
                </div>
              )}
              {avatarUploading && (
                <div className="absolute inset-0 rounded-2xl bg-black/30 flex items-center justify-center">
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                </div>
              )}
            </div>
            <div>
              <label className="btn-secondary text-sm cursor-pointer">
                {profile.avatarUrl ? l.changePhoto : l.uploadPhoto}
                <input type="file" accept="image/*" onChange={onAvatarChange} className="hidden" />
              </label>
              <p className="text-xs text-gray-400 mt-1.5">{l.photoHint}</p>
            </div>
          </div>
        </div>

        {/* Personality (MBTI + 관심사) */}
        <div className="card p-6 mb-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-gray-900">✨ {lper.mbtiLabel} &amp; {lper.interestsLabel}</h2>
            {!editingPersonality && (
              <button onClick={startEditPersonality} className="btn-ghost text-sm px-3 py-1.5">{lper.editBtn}</button>
            )}
          </div>

          {!editingPersonality ? (
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <span className="text-xs text-gray-400 w-16">{lper.mbtiLabel}</span>
                {profile.mbti ? (
                  <span className="rounded-lg bg-violet-100 text-violet-700 px-3 py-1 text-sm font-bold">{profile.mbti}</span>
                ) : (
                  <span className="text-sm text-gray-300">—</span>
                )}
              </div>
              <div className="flex items-start gap-2">
                <span className="text-xs text-gray-400 w-16 mt-1">{lper.interestsLabel}</span>
                {profile.interests && profile.interests.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {profile.interests.map((k) => (
                      <span key={k} className="rounded-full bg-emerald-50 text-emerald-700 border border-emerald-100 px-2.5 py-0.5 text-xs font-medium">
                        {t.interests[k as keyof typeof t.interests] ?? k}
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-sm text-gray-300">—</span>
                )}
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-5">
              {/* MBTI picker */}
              <div>
                <p className="text-xs font-medium text-gray-500 mb-2">{lper.mbtiLabel}</p>
                <div className="flex flex-col gap-2">
                  {MBTI_TYPES.map((row, ri) => (
                    <div key={ri} className="grid grid-cols-4 gap-1.5">
                      {row.map((type) => (
                        <button key={type} type="button"
                          onClick={() => setEditMbti(editMbti === type ? "" : type)}
                          className={`rounded-lg py-1.5 text-xs font-bold border transition-colors ${
                            editMbti === type
                              ? "bg-violet-600 text-white border-violet-600"
                              : "bg-gray-50 text-gray-500 border-gray-200 hover:border-violet-300"
                          }`}>{type}</button>
                      ))}
                    </div>
                  ))}
                </div>
              </div>
              {/* Interests picker (카테고리별) */}
              <div>
                <p className="text-xs font-medium text-gray-500 mb-3">{lper.interestsLabel}</p>
                <InterestPicker selected={editInterests} onChange={setEditInterests} />
              </div>
              <div className="flex gap-2">
                <button onClick={onSavePersonality} disabled={savingPersonality} className="btn-primary text-sm py-2 px-4">
                  {savingPersonality ? lper.saving : lper.saveBtn}
                </button>
                <button onClick={() => setEditingPersonality(false)} className="btn-ghost text-sm py-2 px-4">취소</button>
              </div>
            </div>
          )}
        </div>

        {/* Available Slots */}
        <div className="card p-6 mb-5">
          <h2 className="font-semibold text-gray-900 mb-4">📅 {t.availability.sectionTitle}</h2>
          <SlotCalendar
            mode="guide"
            slots={slots}
            onAddSlot={onAddSlot}
            onDeleteSlot={onDeleteSlot}
          />
        </div>

        {/* Post composer */}
        <div className="card p-6 mb-5">
          <h2 className="font-semibold text-gray-900 mb-4">{lp.manageTitle}</h2>

          <form onSubmit={onPostSubmit} className="rounded-xl bg-gray-50 border border-gray-100 p-4 mb-5">
            <textarea
              placeholder={lp.postPlaceholder}
              value={postContent}
              onChange={(e) => setPostContent(e.target.value)}
              rows={3}
              required
              className="input resize-none mb-3"
            />
            {postImagePreview && (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={postImagePreview} alt="preview"
                className="mb-3 w-full max-h-60 rounded-xl object-cover" />
            )}
            <div className="flex items-center gap-3">
              <label className="btn-ghost text-sm cursor-pointer px-3 py-1.5">
                {postImage ? lp.imageBtnChange : lp.imageBtnAdd}
                <input type="file" accept="image/*" onChange={onPostImageChange} className="hidden" />
              </label>
              <span className="text-xs text-gray-400 flex-1">{postImage ? postImage.name : lp.imageHint}</span>
              <button type="submit" disabled={postSubmitting || !postContent.trim()} className="btn-primary text-sm py-2 px-4">
                {postSubmitting ? lp.submitting : lp.submitBtn}
              </button>
            </div>
          </form>

          {/* Post list */}
          {posts.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-4">{lp.manageEmpty}</p>
          ) : (
            <div className="flex flex-col gap-4">
              {posts.map((p) => (
                <div key={p.id} className="rounded-xl border border-gray-100 overflow-hidden">
                  {p.imageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={p.imageUrl} alt="" className="w-full h-48 object-cover" />
                  )}
                  <div className="p-4">
                    <p className="text-sm text-gray-700 whitespace-pre-line leading-relaxed mb-2">{p.content}</p>
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-gray-400">
                        {new Date(p.createdAt).toLocaleDateString(lang === "ko" ? "ko-KR" : "en-US")}
                      </span>
                      <button
                        onClick={() => onDeletePost(p.id)}
                        className="text-xs text-red-400 hover:text-red-600 font-medium"
                      >
                        {lp.deleteBtn}
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Credentials */}
        <div className="card p-6">
          <h2 className="font-semibold text-gray-900 mb-4">{l.credSection}</h2>

          <form onSubmit={onCredentialSubmit} className="rounded-xl bg-gray-50 border border-gray-100 p-4 mb-5 flex flex-col gap-3">
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
                required className="text-sm text-gray-600" />
            </div>
            <button type="submit" disabled={uploading} className="btn-primary self-start text-sm py-2">
              {uploading ? l.credUploading : l.credAddBtn}
            </button>
          </form>

          {credentials.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-4">{l.noCredentials}</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {credentials.map((c) => (
                <li key={c.id} className="flex items-center justify-between rounded-xl bg-gray-50 px-4 py-3">
                  <span className="flex items-center gap-2 text-sm">
                    <span className="badge-gray">{t.credType[c.type as keyof typeof t.credType] ?? c.type}</span>
                    <span className="text-gray-700">{c.title}</span>
                  </span>
                  <a href={c.fileUrl} target="_blank" rel="noopener noreferrer"
                    className="text-xs text-indigo-600 hover:underline font-medium">{l.view}</a>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </main>
  );
}
