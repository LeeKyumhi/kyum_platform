"use client";

import { useLanguage } from "@/context/LanguageContext";
import { INTEREST_CATEGORIES, type InterestKey } from "@/lib/interests";

interface Props {
  selected: string[];
  onChange: (keys: string[]) => void;
}

export default function InterestPicker({ selected, onChange }: Props) {
  const { t } = useLanguage();

  function toggle(key: string) {
    onChange(
      selected.includes(key) ? selected.filter((k) => k !== key) : [...selected, key]
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {INTEREST_CATEGORIES.map((cat) => {
        const catLabel = t.interestCategoryLabels[cat.key];
        return (
          <div key={cat.key}>
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">
              {catLabel}
            </p>
            <div className="flex flex-wrap gap-2">
              {cat.interests.map((key) => {
                const label = t.interests[key as InterestKey] ?? key;
                const active = selected.includes(key);
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => toggle(key)}
                    className={`rounded-full px-3 py-1.5 text-sm border transition-all ${
                      active
                        ? "bg-indigo-600 text-white border-indigo-600 shadow-sm"
                        : "bg-white text-gray-600 border-gray-200 hover:border-indigo-300 hover:text-indigo-600"
                    }`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
