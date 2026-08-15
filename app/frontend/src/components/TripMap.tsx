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
          // ★ 휠은 페이지 것이다. 카카오 기본값(scrollwheel=true)은 휠을 확대/축소로 가로채고
          //   preventDefault까지 해서, 이 지도 위에 커서가 있으면 <b>페이지가 스크롤되지 않는다</b>.
          //   이 지도는 페이지 한가운데 박혀 있는 256px 미리보기라(/trips/[id]에선 시간표 바로 아래,
          //   모바일에선 화면 폭 전체) 내려가다 여기 걸리면 화면이 멈춘 것처럼 보인다.
          //   확대/축소는 아래 ZoomControl과 더블클릭이 대신한다.
          scrollwheel: false,
        });
        // 휠 확대를 껐으니 조작 수단을 대신 준다 — 없으면 확대할 방법이 더블클릭뿐이다.
        mapRef.current.addControl(
          new kakao.maps.ZoomControl(),
          kakao.maps.ControlPosition.RIGHT,
        );
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

      // 핀이 1개뿐이면 bounds가 면적 0인 사각형이 돼 setBounds가 최대줌(건물 단위)으로 스냅해버림
      // (PlaceDetailModal의 단일 핀 지도에서 처음 발생 — 기존 다중 지점 경로는 항상 2개 이상이라 문제 없었음).
      // 점 1개일 땐 중심 이동 + 고정 레벨로 대체, 2개 이상은 기존 setBounds 동작 그대로 유지.
      if (path.length >= 2) {
        map.setBounds(bounds);
      } else {
        map.setCenter(path[0]);
        map.setLevel(4);
      }
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
      <p className={`text-center text-stone-400 text-sm py-6 ${className}`}>{lm.noLocated}</p>
    );
  }

  return <div ref={boxRef} className={`w-full h-64 rounded-xl overflow-hidden border border-stone-100 ${className}`} />;
}
