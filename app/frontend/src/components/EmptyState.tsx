import type { ReactNode } from "react";

/**
 * 목록 0건 / 검색 결과 없음 공용 빈 상태 — trips·explore·traveler/bookings에서
 * 3번 반복되던 카드 + gradient 아이콘 타일 패턴을 추출한 단일 소스.
 */
export default function EmptyState({
  icon,
  title,
  message,
  action,
  accent = "sky",
}: {
  icon: ReactNode;
  title?: string;
  message: string;
  action?: ReactNode;
  accent?: "sky" | "emerald";
}) {
  const grad = accent === "emerald"
    ? "from-emerald-400 to-teal-400"
    : "from-sky-400 to-cyan-400";

  return (
    <div className="card animate-fade-up p-8 py-16 text-center">
      <div className={`mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br ${grad} text-2xl shadow-md`}>
        {icon}
      </div>
      {title && <p className="mb-1 font-bold text-stone-900">{title}</p>}
      <p className="text-sm text-stone-500">{message}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
