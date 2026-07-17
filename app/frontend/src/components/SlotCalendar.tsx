"use client";

import { useState, useMemo } from "react";
import { useLanguage } from "@/context/LanguageContext";
import { localeOf } from "@/lib/i18n";

type Slot = { id: number; startAt: string; endAt: string };

type GuideProps<T extends Slot> = {
  mode: "guide";
  slots: T[];
  onAddSlot: (startAt: string, endAt: string) => Promise<void>;
  onDeleteSlot: (id: number) => void;
};

type TravelerProps<T extends Slot> = {
  mode: "traveler";
  slots: T[];
  /** 다중 선택 — 여러 날짜/슬롯을 골라 한 번에 예약(#3). */
  selectedSlots: T[];
  onToggleSlot: (slot: T) => void;
  hourlyRate?: number;
  currency?: string;
};

type Props<T extends Slot> = GuideProps<T> | TravelerProps<T>;

const WEEK_DAYS = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];

export default function SlotCalendar<T extends Slot>(props: Props<T>) {
  const { t, lang } = useLanguage();
  const av = t.availability;
  const locale = localeOf(lang);

  const todayDate = useMemo(() => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d;
  }, []);
  const todayStr = todayDate.toISOString().slice(0, 10);

  const [viewYear, setViewYear] = useState(todayDate.getFullYear());
  const [viewMonth, setViewMonth] = useState(todayDate.getMonth());
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  // Guide mode form state
  const [slotStart, setSlotStart] = useState("");
  const [slotEnd, setSlotEnd] = useState("");
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState("");

  const slotsByDate = useMemo(() => {
    const map: Record<string, T[]> = {};
    for (const s of props.slots) {
      const d = s.startAt.slice(0, 10);
      if (!map[d]) map[d] = [];
      map[d].push(s);
    }
    return map;
  }, [props.slots]);

  // 여행자 다중 선택 — 날짜별 선택된 슬롯 수 (셀 표시용, #3)
  // deps를 [props] 전체로 두면 부모 렌더마다 새 객체라 메모가 무효 — 실제 원본 배열만 본다
  const travelerSelected = props.mode === "traveler" ? props.selectedSlots : null;
  const selectedCountByDate = useMemo(() => {
    const map: Record<string, number> = {};
    for (const s of travelerSelected ?? []) {
      const d = s.startAt.slice(0, 10);
      map[d] = (map[d] ?? 0) + 1;
    }
    return map;
  }, [travelerSelected]);

  const cells = useMemo(() => {
    const firstDay = new Date(viewYear, viewMonth, 1).getDay();
    const startOffset = (firstDay + 6) % 7; // Monday-first
    const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
    const arr: (number | null)[] = [
      ...Array(startOffset).fill(null),
      ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
    ];
    while (arr.length % 7 !== 0) arr.push(null);
    return arr;
  }, [viewYear, viewMonth]);

  // 월 이동 시 열어둔 날짜만 접는다. 여행자 다중 선택은 월이 바뀌어도 유지한다(#3).
  function prevMonth() {
    if (viewMonth === 0) { setViewYear(y => y - 1); setViewMonth(11); }
    else setViewMonth(m => m - 1);
    setSelectedDate(null);
  }

  function nextMonth() {
    if (viewMonth === 11) { setViewYear(y => y + 1); setViewMonth(0); }
    else setViewMonth(m => m + 1);
    setSelectedDate(null);
  }

  function getDateStr(day: number) {
    return `${viewYear}-${String(viewMonth + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }

  function handleDayClick(dateStr: string) {
    // 날짜는 슬롯 목록을 펼치는 용도일 뿐 — 다중 선택은 슬롯 클릭으로만 토글(#3).
    setSelectedDate((prev) => (prev === dateStr ? null : dateStr));
  }

  async function handleAddSlot(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedDate || !slotStart || !slotEnd || props.mode !== "guide") return;
    if (slotEnd <= slotStart) { setAddError("종료 시각은 시작 시각 이후여야 합니다."); return; }
    setAddError("");
    setAdding(true);
    try {
      await props.onAddSlot(`${selectedDate}T${slotStart}`, `${selectedDate}T${slotEnd}`);
      setSlotStart("");
      setSlotEnd("");
    } catch {
      setAddError("슬롯 추가에 실패했습니다.");
    } finally {
      setAdding(false);
    }
  }

  const monthLabel = new Date(viewYear, viewMonth, 1).toLocaleDateString(locale, {
    year: "numeric",
    month: "long",
  });

  const slotsOnDate: T[] = selectedDate ? (slotsByDate[selectedDate] ?? []) : [];

  return (
    <div>
      {/* Month navigation */}
      <div className="flex items-center justify-between mb-3">
        <button
          type="button"
          onClick={prevMonth}
          className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 text-gray-500 text-lg transition-colors"
        >
          ‹
        </button>
        <span className="text-sm font-semibold text-gray-800">{monthLabel}</span>
        <button
          type="button"
          onClick={nextMonth}
          className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 text-gray-500 text-lg transition-colors"
        >
          ›
        </button>
      </div>

      {/* Week day headers */}
      <div className="grid grid-cols-7 mb-1">
        {WEEK_DAYS.map((d) => (
          <div key={d} className="text-center text-[10px] font-medium text-gray-400 py-1">
            {d}
          </div>
        ))}
      </div>

      {/* Day cells */}
      <div className="grid grid-cols-7 gap-y-1">
        {cells.map((day, i) => {
          if (!day) return <div key={i} />;

          const dateStr = getDateStr(day);
          const hasSlots = !!slotsByDate[dateStr];
          const isSelected = selectedDate === dateStr;
          const pickedCount = selectedCountByDate[dateStr] ?? 0;
          const isPast = new Date(dateStr + "T00:00") < todayDate;
          const isToday = dateStr === todayStr;
          const isClickable = props.mode === "guide" ? !isPast : hasSlots;

          return (
            <button
              key={i}
              type="button"
              disabled={!isClickable}
              onClick={() => handleDayClick(dateStr)}
              className={[
                "relative mx-auto flex h-9 w-9 flex-col items-center justify-center rounded-full text-xs font-medium transition-colors",
                isSelected
                  ? "bg-indigo-600 text-white"
                  : isToday && !isPast
                    ? hasSlots
                      ? "ring-2 ring-indigo-400 text-indigo-700 hover:bg-indigo-50"
                      : props.mode === "guide"
                        ? "ring-2 ring-indigo-300 text-gray-700 hover:bg-gray-100"
                        : "ring-2 ring-gray-300 text-gray-300 cursor-default"
                    : isPast
                      ? "text-gray-300 cursor-default"
                      : hasSlots
                        ? "text-indigo-700 hover:bg-indigo-50"
                        : props.mode === "guide"
                          ? "text-gray-700 hover:bg-gray-100"
                          : "text-gray-300 cursor-default",
              ].join(" ")}
            >
              {day}
              {hasSlots && (
                <span
                  className={`absolute bottom-1 h-1 w-1 rounded-full ${
                    isSelected ? "bg-indigo-200" : pickedCount > 0 ? "bg-emerald-500" : "bg-indigo-400"
                  }`}
                />
              )}
              {pickedCount > 0 && (
                <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-emerald-500 px-1 text-[9px] font-bold text-white shadow-sm">
                  {pickedCount}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Legend (traveler only) */}
      {props.mode === "traveler" && (
        <div className="flex items-center gap-3 mt-3 pt-2 border-t border-gray-100">
          <span className="flex items-center gap-1 text-[10px] text-gray-400">
            <span className="h-2 w-2 rounded-full bg-indigo-400 inline-block" />
            {av.legendAvailable ?? "예약 가능"}
          </span>
          <span className="flex items-center gap-1 text-[10px] text-gray-400">
            <span className="h-2 w-2 rounded-full bg-gray-200 inline-block" />
            {av.legendUnavailable ?? "불가"}
          </span>
        </div>
      )}

      {/* Detail panel */}
      {selectedDate && (
        <div className="mt-3 border-t border-gray-100 pt-3">
          <p className="text-xs font-semibold text-gray-700 mb-2">
            {new Date(selectedDate + "T00:00").toLocaleDateString(locale, {
              month: "long",
              day: "numeric",
              weekday: "short",
            })}
          </p>

          {/* Slots on selected date */}
          {slotsOnDate.length > 0 ? (
            <div className="flex flex-col gap-1.5 mb-2">
              {slotsOnDate.map((s) => {
                const start = new Date(s.startAt);
                const end = new Date(s.endAt);
                const startS = start.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
                const endS = end.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
                const hrs = Math.round((end.getTime() - start.getTime()) / 3600000);

                if (props.mode === "guide") {
                  return (
                    <div
                      key={s.id}
                      className="flex items-center justify-between rounded-lg bg-indigo-50 px-3 py-2"
                    >
                      <div>
                        <span className="text-xs font-semibold text-indigo-900">
                          {startS} – {endS}
                        </span>
                        <span className="text-[10px] text-indigo-400 ml-2">({hrs}h)</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => props.onDeleteSlot(s.id)}
                        className="text-[10px] text-red-400 hover:text-red-600 font-medium transition-colors"
                      >
                        {av.deleteBtn}
                      </button>
                    </div>
                  );
                } else {
                  const isSlotSelected = props.selectedSlots.some((x) => x.id === s.id);
                  const price =
                    props.hourlyRate != null
                      ? (props.hourlyRate * hrs).toLocaleString()
                      : null;
                  return (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => props.onToggleSlot(s)}
                      className={`flex w-full items-center gap-2.5 rounded-xl border-2 px-3 py-2.5 text-left transition-colors ${
                        isSlotSelected
                          ? "border-indigo-500 bg-indigo-50"
                          : "border-gray-100 hover:border-indigo-200"
                      }`}
                    >
                      <span className={`flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-md border-2 text-[11px] font-bold ${
                        isSlotSelected ? "border-indigo-500 bg-indigo-500 text-white" : "border-gray-200 text-transparent"
                      }`}>✓</span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-gray-900">{startS} – {endS}</span>
                          <span className="text-[10px] text-gray-400">{hrs}h</span>
                        </span>
                        {price && (
                          <span className="mt-0.5 block text-xs font-medium text-indigo-600">{price} {props.currency}</span>
                        )}
                      </span>
                    </button>
                  );
                }
              })}
            </div>
          ) : (
            <p className="text-xs text-gray-400 text-center py-2">
              {props.mode === "traveler" ? av.noSlotsOnDate : ""}
            </p>
          )}

          {/* Guide: add slot form */}
          {props.mode === "guide" && (
            <form onSubmit={handleAddSlot} className="flex flex-col gap-2 mt-2 pt-2 border-t border-gray-100">
              <p className="text-[10px] font-medium text-gray-500">{av.addTitle}</p>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="input-label text-[10px] mb-1">{av.startLabel}</label>
                  <input
                    type="time"
                    value={slotStart}
                    onChange={(e) => setSlotStart(e.target.value)}
                    required
                    className="input text-xs py-1.5"
                  />
                </div>
                <div>
                  <label className="input-label text-[10px] mb-1">{av.endLabel}</label>
                  <input
                    type="time"
                    value={slotEnd}
                    onChange={(e) => setSlotEnd(e.target.value)}
                    required
                    className="input text-xs py-1.5"
                  />
                </div>
              </div>
              {addError && (
                <p className="text-[10px] text-red-500">{addError}</p>
              )}
              <button
                type="submit"
                disabled={adding || !slotStart || !slotEnd}
                className="btn-primary text-xs py-1.5"
              >
                {adding ? av.adding : "+ " + av.addBtn}
              </button>
            </form>
          )}
        </div>
      )}

      {/* Prompt when nothing selected */}
      {!selectedDate && props.mode === "traveler" && props.slots.length > 0 && (
        <p className="text-xs text-gray-400 text-center pt-3">{av.selectDate}</p>
      )}
      {!selectedDate && props.mode === "guide" && props.slots.length === 0 && (
        <p className="text-xs text-gray-400 text-center pt-3">{av.empty}</p>
      )}
    </div>
  );
}
