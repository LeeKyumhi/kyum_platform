"use client";

// 모달 공통 닫기 동작 — Esc 키 + 열려있는 동안 배경 스크롤 잠금(닫힐 때 원복).
// PlaceDetailModal/PlacePickerModal/SharePickerModal/PlanPreviewModal/CoursePreviewModal이 공유.

import { useEffect } from "react";

export function useModalDismiss(onClose: () => void) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);
}
