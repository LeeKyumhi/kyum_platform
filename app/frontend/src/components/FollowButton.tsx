"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export default function FollowButton({ userId, initialFollowing = false, onChange }:
  { userId: number; initialFollowing?: boolean; onChange?: (f: boolean) => void }) {
  const router = useRouter();
  const { t } = useLanguage();
  const [following, setFollowing] = useState(initialFollowing);
  const [busy, setBusy] = useState(false);

  async function toggle() {
    if (!getToken()) { router.push("/login"); return; }
    const next = !following;
    setFollowing(next); setBusy(true); onChange?.(next);
    try {
      await api(`/api/users/${userId}/follow`, { method: next ? "POST" : "DELETE", auth: true });
    } catch { setFollowing(!next); onChange?.(!next); }
    finally { setBusy(false); }
  }
  return (
    <button onClick={toggle} disabled={busy}
      className={following ? "btn-ghost text-sm" : "btn-primary text-sm"}>
      {following ? t.follow.following : t.follow.follow}
    </button>
  );
}
