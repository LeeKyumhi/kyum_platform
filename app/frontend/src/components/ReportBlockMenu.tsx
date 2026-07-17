"use client";

// 신고·차단 케밥 메뉴 — DM 대화방 헤더·가이드 프로필에서 공유한다.
// 대상은 "사람"(targetUserId). 차단 성공 시 onBlocked()로 부모가 후처리(대화방은 목록으로 이동 등).
// 차단/신고 전부 로그인 필요(백엔드가 authenticated). 예약 채팅(ChatRoom 공용 본문)에는 넣지 않는다.

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type Props = {
  targetUserId: number;
  onBlocked?: () => void;
  /** 케밥 버튼 색조 — 밝은 배경(기본) / 어두운 배경 */
  tone?: "light" | "dark";
};

type ReasonKey = "SPAM" | "HARASSMENT" | "SCAM" | "INAPPROPRIATE" | "OTHER";

export default function ReportBlockMenu({ targetUserId, onBlocked, tone = "light" }: Props) {
  const { t } = useLanguage();
  const s = t.safety;

  const [open, setOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  // 바깥 클릭 / Esc 로 드롭다운 닫기
  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") setOpen(false); }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  async function handleBlock() {
    setOpen(false);
    if (!window.confirm(s.blockConfirm)) return;
    try {
      await api("/api/blocks", { method: "POST", auth: true, body: { targetUserId } });
      onBlocked?.();
    } catch (e) {
      alert(e instanceof Error ? e.message : s.block);
    }
  }

  const btnColor =
    tone === "dark"
      ? "text-white/80 hover:bg-white/15"
      : "text-stone-500 hover:bg-stone-100";

  return (
    <div ref={wrapRef} className="relative flex-shrink-0">
      <button
        type="button"
        aria-label={s.menuLabel}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className={`flex h-8 w-8 items-center justify-center rounded-full transition-colors ${btnColor}`}
      >
        <svg viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5">
          <circle cx="10" cy="4" r="1.6" />
          <circle cx="10" cy="10" r="1.6" />
          <circle cx="10" cy="16" r="1.6" />
        </svg>
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 z-30 mt-1 w-40 overflow-hidden rounded-xl border border-stone-200 bg-white py-1 shadow-lg"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => { setOpen(false); setReportOpen(true); }}
            className="block w-full px-4 py-2 text-left text-sm text-stone-700 hover:bg-stone-50"
          >
            {s.report}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={handleBlock}
            className="block w-full px-4 py-2 text-left text-sm font-semibold text-red-600 hover:bg-red-50"
          >
            {s.block}
          </button>
        </div>
      )}

      {reportOpen && (
        <ReportModal
          targetUserId={targetUserId}
          onClose={() => setReportOpen(false)}
        />
      )}
    </div>
  );
}

const REASONS: ReasonKey[] = ["SPAM", "HARASSMENT", "SCAM", "INAPPROPRIATE", "OTHER"];

function ReportModal({ targetUserId, onClose }: { targetUserId: number; onClose: () => void }) {
  const { t } = useLanguage();
  const s = t.safety;

  const [reason, setReason] = useState<ReasonKey>("SPAM");
  const [detail, setDetail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") onClose(); }
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [onClose]);

  const reasonLabel: Record<ReasonKey, string> = {
    SPAM: s.reasonSpam,
    HARASSMENT: s.reasonHarassment,
    SCAM: s.reasonScam,
    INAPPROPRIATE: s.reasonInappropriate,
    OTHER: s.reasonOther,
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await api("/api/reports", {
        method: "POST",
        auth: true,
        body: { targetType: "USER", targetId: targetUserId, reason, detail: detail.trim() || null },
      });
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : s.report);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="w-full max-w-md rounded-t-2xl bg-white p-5 shadow-xl sm:rounded-2xl">
        {done ? (
          <div className="py-6 text-center">
            <div className="mb-3 text-3xl">✅</div>
            <p className="mb-5 text-sm font-medium text-stone-700">{s.reported}</p>
            <button
              type="button"
              onClick={onClose}
              className="rounded-full bg-stone-900 px-6 py-2 text-sm font-semibold text-white hover:bg-stone-700"
            >
              {s.cancel}
            </button>
          </div>
        ) : (
          <form onSubmit={submit}>
            <h2 className="text-base font-bold text-stone-900">{s.reportTitle}</h2>
            <p className="mt-1 text-xs text-stone-500">{s.reportSub}</p>

            <div className="mt-4 space-y-1.5">
              {REASONS.map((r) => (
                <label
                  key={r}
                  className={`flex cursor-pointer items-center gap-2.5 rounded-xl border px-3 py-2.5 text-sm transition-colors ${
                    reason === r
                      ? "border-stone-900 bg-stone-50 font-semibold text-stone-900"
                      : "border-stone-200 text-stone-600 hover:bg-stone-50"
                  }`}
                >
                  <input
                    type="radio"
                    name="report-reason"
                    value={r}
                    checked={reason === r}
                    onChange={() => setReason(r)}
                    className="accent-stone-900"
                  />
                  {reasonLabel[r]}
                </label>
              ))}
            </div>

            <label className="mt-4 block text-xs font-medium text-stone-600">
              {s.detailLabel}
              <textarea
                value={detail}
                onChange={(e) => setDetail(e.target.value)}
                placeholder={s.detailPh}
                rows={3}
                maxLength={1000}
                className="mt-1 w-full resize-none rounded-xl border border-stone-200 px-3 py-2 text-sm text-stone-800 outline-none focus:border-stone-400"
              />
            </label>

            {error && <p className="mt-2 text-xs text-red-600">{error}</p>}

            <div className="mt-4 flex gap-2">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 rounded-full border border-stone-200 py-2.5 text-sm font-semibold text-stone-600 hover:bg-stone-50"
              >
                {s.cancel}
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="flex-1 rounded-full bg-red-600 py-2.5 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
              >
                {submitting ? s.submitting : s.submit}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
