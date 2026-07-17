"use client";

import { useLanguage } from "@/context/LanguageContext";
import { SERVICE_CATEGORIES, type ServiceCategoryKey } from "@/lib/serviceCategories";

interface Props {
  selected: string[];
  onChange: (keys: string[]) => void;
  /** 관광(자격 필수) 카테고리 활성화 여부. 인증(VERIFIED)이 아니면 잠금. */
  verified: boolean;
  /** true면 관광 그룹 자체를 렌더하지 않음(자물쇠 힌트도 없음) — 무자격 온보딩(동행 전용) 경로용. */
  hideTour?: boolean;
}

/**
 * 제공 서비스 카테고리 선택기 (InterestPicker 미러).
 * 관광 카테고리는 미인증이면 자물쇠로 비활성 — 관광/비관광 완전 분리를 UI에서도 강제.
 */
export default function ServiceCategoryPicker({ selected, onChange, verified, hideTour }: Props) {
  const { t } = useLanguage();
  const ls = t.serviceCategories;

  function toggle(key: string, locked: boolean) {
    if (locked) return;
    onChange(selected.includes(key) ? selected.filter((k) => k !== key) : [...selected, key]);
  }

  const groups = [
    ...(hideTour ? [] : [{ label: ls.tourGroup, items: SERVICE_CATEGORIES.filter((c) => c.requiresLicense) }]),
    { label: ls.nonTourGroup, items: SERVICE_CATEGORIES.filter((c) => !c.requiresLicense) },
  ];

  return (
    <div className="flex flex-col gap-4">
      {groups.map((g) => {
        const hasLocked = g.items.some((c) => c.requiresLicense) && !verified;
        return (
          <div key={g.label}>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-400">{g.label}</p>
            <div className="flex flex-wrap gap-2">
              {g.items.map((c) => {
                const locked = c.requiresLicense && !verified;
                const active = selected.includes(c.key);
                return (
                  <button
                    key={c.key}
                    type="button"
                    onClick={() => toggle(c.key, locked)}
                    disabled={locked}
                    className={`rounded-full border px-3 py-1.5 text-sm transition-all ${
                      active
                        ? "border-emerald-600 bg-emerald-600 text-white shadow-sm"
                        : locked
                          ? "cursor-not-allowed border-gray-100 bg-gray-50 text-gray-300"
                          : "border-gray-200 bg-white text-gray-600 hover:border-emerald-300 hover:text-emerald-600"
                    }`}
                  >
                    {locked && "🔒 "}{ls[c.key as ServiceCategoryKey]}
                  </button>
                );
              })}
            </div>
            {hasLocked && <p className="mt-1.5 text-xs text-amber-600">{ls.lockedHint}</p>}
          </div>
        );
      })}
    </div>
  );
}
