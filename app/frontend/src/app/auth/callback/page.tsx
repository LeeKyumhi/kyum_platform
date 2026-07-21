"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { saveToken, saveRole, saveUserName, api } from "@/lib/api";

export default function AuthCallbackPage() {
  const router = useRouter();

  useEffect(() => {
    const hash = window.location.hash.startsWith("#")
      ? window.location.hash.slice(1)
      : window.location.hash;
    const params = new URLSearchParams(hash);
    const token = params.get("token");
    const role = params.get("role");

    if (!token) {
      router.replace("/login?error=oauth_failed");
      return;
    }

    saveToken(token);
    if (role) saveRole(role);
    // 사용자 이름을 채워 넣고 모드 선택으로 (로컬 로그인과 동일 흐름).
    api<{ fullName: string }>("/api/users/me", { auth: true })
      .then((me) => saveUserName(me.fullName))
      .catch(() => { /* 이름 조회 실패는 치명적이지 않음 */ })
      .finally(() => router.replace("/select-mode"));
  }, [router]);

  return (
    <main className="page flex items-center justify-center px-4">
      <p className="text-sm text-stone-500">로그인 처리 중…</p>
    </main>
  );
}
