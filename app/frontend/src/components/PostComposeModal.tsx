"use client";

// 게시글 작성 모달 — 내 게시글(/guide/posts), 가이드 홈, 여행자 홈에서 공유한다.
// 열림/닫힘은 부모가 open prop으로 제어하고, 게시 성공 시 onCreated()를 호출한다.

import { useEffect, useState } from "react";
import { apiUpload } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { CameraIcon } from "@/components/icons";

type Props = {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
  /** 미리 채워둘 본문(예: 일정 공유 스냅샷). 미전달 시 빈 문자열로 시작(기존 동작 불변). */
  initialContent?: string;
};

export default function PostComposeModal({ open, onClose, onCreated, initialContent }: Props) {
  const { t } = useLanguage();
  const lp = t.guidePosts;

  const [content, setContent] = useState("");
  const [image, setImage] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  // 모달이 열릴 때마다 입력 상태를 초기화한다.
  useEffect(() => {
    if (open) {
      setContent(initialContent ?? "");
      setImage(null);
      setPreview(null);
      setSubmitting(false);
      setError("");
    }
  }, [open, initialContent]);

  function onImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setImage(file);
    if (file) setPreview(URL.createObjectURL(file));
    else setPreview(null);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!content.trim()) return;
    setError("");
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append("content", content.trim());
      if (image) fd.append("image", image);
      await apiUpload("/api/guide-profiles/me/posts", fd, { auth: true });
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setSubmitting(false);
    }
  }

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        onClick={() => !submitting && onClose()}
      />
      <div className="relative z-10 max-h-[90vh] w-full overflow-y-auto rounded-t-2xl bg-white shadow-2xl sm:max-w-lg sm:rounded-2xl">
        {/* Modal header */}
        <div className="flex items-center justify-between border-b border-stone-100 px-5 py-4">
          <button
            type="button"
            onClick={() => !submitting && onClose()}
            className="text-sm text-stone-500 transition-colors hover:text-stone-800"
          >
            {lp.cancelBtn}
          </button>
          <h2 className="text-sm font-bold text-stone-900">{lp.newPost}</h2>
          <button
            type="submit"
            form="compose-form"
            disabled={submitting || !content.trim()}
            className="text-sm font-semibold text-sky-500 transition-colors hover:text-sky-700 disabled:opacity-40"
          >
            {submitting ? lp.submitting : lp.submitBtn}
          </button>
        </div>

        <form id="compose-form" onSubmit={onSubmit} className="flex flex-col gap-4 px-5 py-4">
          {error && (
            <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-2 text-sm text-red-600">{error}</div>
          )}

          <textarea
            placeholder={lp.postPlaceholder}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={5}
            required
            autoFocus
            className="input resize-none text-base leading-relaxed"
          />

          {preview ? (
            <div className="relative">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={preview} alt="preview" className="max-h-72 w-full rounded-xl object-cover" />
              <label className="absolute bottom-2 right-2 flex cursor-pointer items-center gap-1.5 rounded-full bg-black/50 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-black/70">
                <CameraIcon className="h-3.5 w-3.5" /> {lp.imageBtnChange}
                <input type="file" accept="image/*" onChange={onImageChange} className="hidden" />
              </label>
            </div>
          ) : (
            <label className="group flex cursor-pointer items-center gap-3 rounded-xl border-2 border-dashed border-stone-200 p-4 transition-colors hover:border-sky-300">
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-stone-100 text-stone-400 transition-colors group-hover:bg-sky-50 group-hover:text-sky-400">
                <CameraIcon className="h-5 w-5" />
              </span>
              <span className="text-sm text-stone-400 transition-colors group-hover:text-sky-500">{lp.imageBtnAdd}</span>
              <span className="ml-auto text-xs text-stone-300">{lp.imageHint}</span>
              <input type="file" accept="image/*" onChange={onImageChange} className="hidden" />
            </label>
          )}
        </form>
      </div>
    </div>
  );
}
