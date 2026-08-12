"use client";

import { useState } from "react";
import { apiUpload, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { useModalDismiss } from "@/lib/useModalDismiss";

/** 백엔드 `spring.servlet.multipart.max-file-size`와 같은 값. 넘으면 서버가 400도 아닌
 *  형태로 끊어서 사용자에게 "업로드 실패"만 남는다 — 여기서 먼저 잡아 이유를 말해준다. */
const MAX_PHOTO_BYTES = 10 * 1024 * 1024;
/** `PlaceNoteService.MAX_TIP_LENGTH`와 같은 값. */
const MAX_TIP = 140;

/**
 * 장소에 사진·한줄팁을 남기는 모달. `PlaceDetailModal` 안에서만 열린다.
 *
 * 에러 문구는 백엔드 것을 그대로 보여준다 — 상한·형식 안내가 거기 담겨 있고,
 * `apiUpload`가 응답 본문의 `error` 키를 이미 Error.message로 옮겨준다.
 * (본문 키는 `message`가 아니라 `error`다 — `GlobalExceptionHandler` 규약.)
 */
export default function PlaceNoteComposer({
  placeId, kakaoPlaceId, placeName, onClose, onCreated,
}: {
  placeId?: number | null;
  kakaoPlaceId?: string | null;
  placeName: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const { t } = useLanguage();
  const pn = t.placeNotes;
  const [photo, setPhoto] = useState<File | null>(null);
  const [tip, setTip] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useModalDismiss(onClose);

  const canSubmit = (photo !== null || tip.trim().length > 0) && !busy;

  function pickPhoto(file: File | null) {
    setError(null);
    if (file && file.size > MAX_PHOTO_BYTES) {
      setPhoto(null);
      setError(pn.tooLarge);
      return;
    }
    setPhoto(file);
  }

  async function submit() {
    if (!getToken()) { setError(pn.loginRequired); return; }
    if (!canSubmit) { setError(pn.needSomething); return; }
    setBusy(true);
    setError(null);
    try {
      const fd = new FormData();
      // 두 식별자 중 있는 것만 싣는다 — 백엔드가 둘 다 없으면 400으로 막는다.
      if (placeId != null) fd.append("placeId", String(placeId));
      if (kakaoPlaceId) fd.append("kakaoPlaceId", kakaoPlaceId);
      fd.append("placeName", placeName);
      if (photo) fd.append("photo", photo);
      if (tip.trim()) fd.append("tip", tip.trim());
      // auth: true 필수 — apiUpload의 기본값은 false라 빼면 토큰이 안 실려 401이 난다.
      await apiUpload("/api/places/notes", fd, { auth: true });
      onCreated();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : pn.needSomething);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 flex max-h-[85dvh] w-full flex-col overflow-y-auto overscroll-contain rounded-2xl bg-white p-5 shadow-2xl sm:max-w-md">
        <div className="mb-1 flex items-start justify-between gap-2">
          <h2 className="text-base font-bold text-stone-900">{pn.composerTitle}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={t.placeDetail.close}
            className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-lg text-stone-400 transition-colors hover:bg-stone-100 hover:text-stone-700"
          >
            ✕
          </button>
        </div>
        <p className="mb-4 truncate text-xs text-stone-400">{placeName}</p>

        <label className="input-label" htmlFor="place-note-photo">{pn.photoLabel}</label>
        <input
          id="place-note-photo"
          type="file"
          accept="image/jpeg,image/png"
          onChange={(e) => pickPhoto(e.target.files?.[0] ?? null)}
          className="mb-4 w-full text-sm text-stone-600 file:mr-3 file:rounded-full file:border-0 file:bg-sky-50 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-sky-600 hover:file:bg-sky-100"
        />

        <label className="input-label" htmlFor="place-note-tip">{pn.tipLabel}</label>
        <textarea
          id="place-note-tip"
          value={tip}
          maxLength={MAX_TIP}
          rows={3}
          onChange={(e) => setTip(e.target.value)}
          placeholder={pn.tipPlaceholder}
          className="input mb-1 resize-none"
        />
        <p className="mb-4 text-right text-[11px] text-stone-400">
          {pn.tipCounter.replace("{n}", String(tip.length))}
        </p>

        {error && (
          <p className="mb-3 rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-xs text-red-600">
            {error}
          </p>
        )}

        <button
          type="button"
          onClick={submit}
          disabled={!canSubmit}
          className="btn-primary w-full py-2 text-sm"
        >
          {busy ? pn.submitting : pn.submit}
        </button>
      </div>
    </div>
  );
}
