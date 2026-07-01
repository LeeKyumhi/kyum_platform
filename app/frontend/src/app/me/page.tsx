"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken } from "@/lib/api";

type Me = {
  id: number;
  email: string;
  fullName: string;
  nationality: string | null;
};

export default function MePage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    // 토큰이 없으면 로그인 페이지로
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    // 보호된 API 호출 (auth: true → Authorization 헤더에 토큰 첨부)
    api<Me>("/api/users/me", { auth: true })
      .then(setMe)
      .catch((err) => setError(err instanceof Error ? err.message : "조회 실패"));
  }, [router]);

  function onLogout() {
    clearToken();
    router.push("/login");
  }

  return (
    <main className="min-h-screen flex items-center justify-center px-6">
      <div className="w-full max-w-sm text-center">
        <h1 className="text-2xl font-bold mb-6">내 정보</h1>

        {error && <p className="text-sm text-red-600">{error}</p>}

        {me ? (
          <div className="rounded-lg border border-gray-200 p-6 text-left">
            <p className="mb-2">
              <span className="text-gray-500">이름: </span>
              {me.fullName}
            </p>
            <p className="mb-2">
              <span className="text-gray-500">이메일: </span>
              {me.email}
            </p>
            <p>
              <span className="text-gray-500">국적: </span>
              {me.nationality || "-"}
            </p>
          </div>
        ) : (
          !error && <p className="text-gray-400">불러오는 중...</p>
        )}

        <div className="mt-6 flex flex-col gap-3">
          <Link
            href="/guides"
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition"
          >
            가이드 찾기
          </Link>
          <Link
            href="/become-guide"
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50 transition"
          >
            가이드로 활동하기
          </Link>
          <Link
            href="/guide/manage"
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50 transition"
          >
            가이드 프로필 관리 (사진·자격증)
          </Link>
          <button
            onClick={onLogout}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50 transition"
          >
            로그아웃
          </button>
        </div>
      </div>
    </main>
  );
}
