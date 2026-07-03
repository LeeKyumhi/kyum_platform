"use client";

import { useEffect, useRef } from "react";
import { useLanguage } from "@/context/LanguageContext";

declare global {
  interface Window { kakao: any }
}

export type MapPoint = {
  key: string;
  name: string;
  latitude: number | null;
  longitude: number | null;
};

const JS_KEY = process.env.NEXT_PUBLIC_KAKAO_JS_KEY ?? "";

// SDK 스크립트는 한 번만 로드 (모듈 레벨 싱글턴 프라미스)
let sdkPromise: Promise<void> | null = null;
function loadKakaoSdk(): Promise<void> {
  if (typeof window === "undefined") return Promise.reject();
  if (window.kakao && window.kakao.maps) return Promise.resolve();
  if (sdkPromise) return sdkPromise;
  sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${JS_KEY}&autoload=false`;
    script.async = true;
    script.onload = () => window.kakao.maps.load(() => resolve());
    script.onerror = () => reject(new Error("kakao sdk load failed"));
    document.head.appendChild(script);
  });
  return sdkPromise;
}

/**
 * 여행 일정의 한 날 경로를 지도에 시각화.
 * 좌표(lat/lng)가 있는 장소만 번호 마커 + 순서대로 직선(polyline)으로 잇는다.
 * JS 키가 없으면 안내 문구만 보여주고 렌더 안 함(우아한 degradation).
 */
export default function TripMap({ points, className = "" }: { points: MapPoint[]; className?: string }) {
  const { t } = useLanguage();
  const lm = t.tripMap;
  const boxRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const overlaysRef = useRef<any[]>([]);
  const lineRef = useRef<any>(null);

  const located = points.filter(
    (p) => typeof p.latitude === "number" && typeof p.longitude === "number"
  );

  useEffect(() => {
    if (!JS_KEY || located.length === 0) return;
    let disposed = false;

    loadKakaoSdk().then(() => {
      if (disposed || !boxRef.current) return;
      const kakao = window.kakao;

      // 지도 최초 1회 생성
      if (!mapRef.current) {
        mapRef.current = new kakao.maps.Map(boxRef.current, {
          center: new kakao.maps.LatLng(located[0].latitude, located[0].longitude),
          level: 6,
        });
      }
      const map = mapRef.current;

      // 이전 마커/선 제거
      overlaysRef.current.forEach((o) => o.setMap(null));
      overlaysRef.current = [];
      if (lineRef.current) { lineRef.current.setMap(null); lineRef.current = null; }

      const bounds = new kakao.maps.LatLngBounds();
      const path: any[] = [];

      located.forEach((p, i) => {
        const pos = new kakao.maps.LatLng(p.latitude, p.longitude);
        path.push(pos);
        bounds.extend(pos);
        const content = document.createElement("div");
        content.className = "trip-map-pin";
        content.title = p.name;
        content.textContent = String(i + 1);
        const overlay = new kakao.maps.CustomOverlay({ position: pos, content, yAnchor: 0.5, xAnchor: 0.5, zIndex: 10 });
        overlay.setMap(map);
        overlaysRef.current.push(overlay);
      });

      if (path.length >= 2) {
        lineRef.current = new kakao.maps.Polyline({
          path,
          strokeWeight: 4,
          strokeColor: "#4f46e5",
          strokeOpacity: 0.8,
          strokeStyle: "solid",
        });
        lineRef.current.setMap(map);
      }

      map.setBounds(bounds);
    }).catch(() => { /* SDK 로드 실패 시 조용히 무시 */ });

    return () => { disposed = true; };
  }, [located.map((p) => `${p.key}:${p.latitude},${p.longitude}`).join("|")]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!JS_KEY) {
    return (
      <p className={`rounded-xl bg-amber-50 border border-amber-100 px-4 py-3 text-sm text-amber-700 ${className}`}>
        🗺️ {lm.needKey}
      </p>
    );
  }
  if (located.length === 0) {
    return (
      <p className={`text-center text-gray-400 text-sm py-6 ${className}`}>{lm.noLocated}</p>
    );
  }

  return <div ref={boxRef} className={`w-full h-64 rounded-xl overflow-hidden border border-gray-100 ${className}`} />;
}
