import Link from "next/link";
import type { ReactNode } from "react";

/**
 * 페이지 상단 헤더 — 프리미엄 에디토리얼 리브랜딩의 accent bar 패턴 단일 소스.
 * guides/companions 페이지에 손으로 쓰던 마크업을 추출한 것. 내부 페이지 헤더는 전부 이걸 쓸 것.
 *  - accent: sky(투어/기본) | emerald(동행 세계)
 *  - back: 기존 btn-ghost 뒤로가기 링크를 제목 행 왼쪽에 배치
 *  - title: ReactNode — 제목 옆 배지 버튼(guides의 ✓ 인증 설명 등) 허용
 */
export default function PageHeader({
  title,
  subtitle,
  accent = "sky",
  back,
  action,
}: {
  title: ReactNode;
  subtitle?: string;
  accent?: "sky" | "emerald";
  back?: { href: string; label: string };
  action?: ReactNode;
}) {
  const bar = accent === "emerald"
    ? "from-emerald-400 to-teal-400"
    : "from-sky-400 to-cyan-400";

  return (
    <div className="animate-fade-up mb-5">
      <span className={`mb-3 block h-1.5 w-10 rounded-full bg-gradient-to-r ${bar}`} />
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            {back && (
              <Link href={back.href} className="btn-ghost text-sm">{back.label}</Link>
            )}
            <h1 className="text-2xl font-extrabold tracking-tight text-stone-900 md:text-3xl">
              {title}
            </h1>
          </div>
          {subtitle && <p className="mt-1 text-sm text-stone-500">{subtitle}</p>}
        </div>
        {action && <div className="flex-shrink-0 pt-1">{action}</div>}
      </div>
    </div>
  );
}
