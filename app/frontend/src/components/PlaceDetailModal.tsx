"use client";

// 장소 상세 모달 — /explore, /trips/[id] 공유.
// Kakao REST API가 주는 데이터(이름/카테고리/주소/전화/좌표/거리/placeUrl)만 사용한다.
// 사진·영업시간·평점은 Kakao REST에 없으므로 여기서도 절대 만들어내지 않는다.

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";
import { useModalDismiss } from "@/lib/useModalDismiss";
import { PinIcon } from "@/components/icons";
import TripMap from "@/components/TripMap";
import { matchSpot } from "@/lib/spots";

export type ModalPlace = {
  id: string;
  name: string;
  category?: string | null;
  phone?: string | null;
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  placeUrl?: string | null;
  distanceMeters?: number | null;
};

type Props = {
  place: ModalPlace;
  onClose: () => void;
  /** 여행 일정 빌더(/trips/[id])에서만 전달 — 있으면 "일정에 추가" 버튼을 함께 보여준다. */
  onAdd?: () => void;
  addLabel?: string;
};

export default function PlaceDetailModal({ place, onClose, onAdd, addLabel }: Props) {
  const { t } = useLanguage();
  const pd = t.placeDetail;
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const [copied, setCopied] = useState(false);

  useModalDismiss(onClose);
  useEffect(() => { closeBtnRef.current?.focus(); }, []);

  const hasCoords = typeof place.latitude === "number" && typeof place.longitude === "number";
  const spot = matchSpot(place.name);

  async function copyAddress() {
    if (!place.address) return;
    try {
      await navigator.clipboard.writeText(place.address);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      /* clipboard 미지원 브라우저는 조용히 무시 */
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 max-h-[90vh] w-full overflow-y-auto rounded-t-2xl bg-white shadow-2xl sm:max-w-lg sm:rounded-2xl">
        {/* Header */}
        <div className="flex items-start justify-between gap-3 border-b border-stone-100 px-5 py-4">
          <div className="min-w-0">
            <h2 className="truncate text-lg font-bold text-stone-900">{place.name}</h2>
            {place.category && (
              <p className="mt-0.5 truncate text-xs text-stone-400">{place.category}</p>
            )}
          </div>
          <button
            ref={closeBtnRef}
            type="button"
            onClick={onClose}
            aria-label={pd.close}
            className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-lg text-stone-400 transition-colors hover:bg-stone-100 hover:text-stone-700"
          >
            ✕
          </button>
        </div>

        <div className="flex flex-col gap-4 px-5 py-4">
          {hasCoords ? (
            // TripMap의 기본 높이는 h-64 고정 — 두 클래스가 함께 있으면 나중에 정의된 유틸리티가
            // 이기므로(h-64가 h-56보다 나중) className으로 줄일 수 없어 기본값 그대로 둔다.
            <TripMap
              points={[{ key: place.id, name: place.name, latitude: place.latitude!, longitude: place.longitude! }]}
            />
          ) : (
            <p className="rounded-xl bg-stone-50 px-4 py-3 text-center text-sm text-stone-400">
              {pd.noCoords}
            </p>
          )}

          {place.address && (
            <div className="flex items-start gap-2">
              <PinIcon className="mt-0.5 h-4 w-4 flex-shrink-0 text-sky-400" />
              <div className="min-w-0 flex-1">
                <p className="text-sm text-stone-700">{place.address}</p>
              </div>
              <button
                type="button"
                onClick={copyAddress}
                className="flex-shrink-0 whitespace-nowrap rounded-full border border-stone-200 px-3 py-1 text-xs font-medium text-stone-500 transition-colors hover:border-sky-300 hover:text-sky-600"
              >
                {copied ? `✓ ${pd.addressCopied}` : pd.copyAddress}
              </button>
            </div>
          )}

          {place.phone && (
            <a
              href={`tel:${place.phone}`}
              className="flex items-center gap-2 text-sm font-medium text-sky-600 hover:underline"
            >
              📞 {place.phone}
            </a>
          )}

          {place.distanceMeters != null && (
            <p className="text-sm text-stone-500">
              {pd.distance}: <span className="font-semibold text-sky-500">{(place.distanceMeters / 1000).toFixed(1)}km</span>
            </p>
          )}

          {spot && (
            <Link
              href={`/spots/${spot.slug}`}
              className="btn-primary w-full text-center text-sm"
            >
              🏛️ {pd.viewSpot}
            </Link>
          )}

          {onAdd && (
            <button type="button" onClick={onAdd} className="btn-secondary w-full text-sm">
              {addLabel}
            </button>
          )}

          {place.placeUrl && (
            <a
              href={place.placeUrl}
              target="_blank"
              rel="noreferrer"
              className="btn-ghost w-full text-center text-sm"
            >
              {pd.openInKakao}
            </a>
          )}
        </div>
      </div>
    </div>
  );
}
