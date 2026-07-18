"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useLanguage } from "@/context/LanguageContext";
import { getMode, type Mode } from "@/lib/mode";
import {
  getTrack, setTrack, clearTrack, getTourLegalAck, ackTourLegal,
  TRACK_CHANGED_EVENT, type Track,
} from "@/lib/track";

// page.tsx·LanguagePicker가 여행자/가이드 모드를 정하면 쏘는 이벤트
const MODE_CHANGED_EVENT = "peerup-mode-changed";

/**
 * 진입 트랙 게이트 — layout에 상시 마운트.
 *  1) 트랙 미선택이면 전체화면 선택 화면 (동행 세계 / 투어 세계)
 *  2) 투어 세계인데 법적 고지(관광진흥법 §38) 미동의면 동의 게이트
 * /companions*·/guides* 딥링크는 트랙을 암묵 설정해 마찰 없이 통과시킨다.
 * LanguagePicker(z-100)보다 아래(z-90)라 첫 방문 순서 = 언어 → 역할 → 세계.
 */

// 게이트를 씌우지 않는 라우트 — 법적 안내 자체·인증 플로우·모드 선택
const EXCLUDED = ["/legal", "/login", "/signup", "/forgot-password", "/reset-password", "/verify-email", "/select-mode"];

export default function TrackGate() {
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useLanguage();

  const [mounted, setMounted] = useState(false);
  const [track, setTrackState] = useState<Track | null>(null);
  const [mode, setModeState] = useState<Mode | null>(null);
  const [legalAck, setLegalAck] = useState(false);
  const [checked, setChecked] = useState(false);

  // localStorage가 단일 소스 — 마운트·트랙/모드 변경 이벤트 시 재읽기
  useEffect(() => {
    const sync = () => { setTrackState(getTrack()); setModeState(getMode()); setLegalAck(getTourLegalAck()); };
    sync();
    setMounted(true);
    window.addEventListener(TRACK_CHANGED_EVENT, sync);
    window.addEventListener(MODE_CHANGED_EVENT, sync);
    return () => {
      window.removeEventListener(TRACK_CHANGED_EVENT, sync);
      window.removeEventListener(MODE_CHANGED_EVENT, sync);
    };
  }, []);

  // 딥링크 암묵 동기화 + 경로 변경 시 모드/트랙 재읽기(외부 페이지에서 바뀐 값 반영)
  useEffect(() => {
    if (!mounted) return;
    setModeState(getMode());
    const cur = getTrack();
    if (pathname.startsWith("/companions") && cur !== "companion") setTrack("companion");
    else if (pathname.startsWith("/guides") && cur !== "tour") setTrack("tour");
    else setTrackState(cur);
  }, [pathname, mounted]);

  const excluded = EXCLUDED.some((p) => pathname === p || pathname.startsWith(p + "/"));
  // 세계 전용 경로에선 chooser를 띄우지 않는다 — 위 암묵 동기화가 트랙을 정하므로
  // (마운트 직후 1프레임 chooser 깜빡임 방지). 법적 게이트는 그대로 적용된다.
  const onWorldPath = pathname.startsWith("/companions") || pathname.startsWith("/guides");
  // 모드(여행자/가이드)를 먼저 고른 뒤에야 세계 선택을 띄운다 — 두 진입 선택이 겹치지 않게.
  // (여행자/가이드는 LanguagePicker 역할 스텝 또는 랜딩 ModeChooser가 먼저 처리)
  const showChooser = mounted && !excluded && !onWorldPath && track === null && mode !== null;
  const showLegal = mounted && !excluded && track === "tour" && !legalAck;
  const visible = showChooser || showLegal;

  // 게이트가 떠 있는 동안 뒤 페이지 스크롤 잠금
  useEffect(() => {
    if (!visible) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = prev; };
  }, [visible]);

  if (!visible) return null;

  const tr = t.tracks;
  const tc = t.trackChooser;
  const tl = t.tourLegal;

  return (
    <div className="fixed inset-0 z-[90] overflow-y-auto bg-stone-50">
      <div className="flex min-h-full items-center justify-center px-4 py-10">
        {showChooser ? (
          /* ── 1단계: 세계 선택 ── */
          <div className="w-full max-w-3xl animate-fade-up text-center">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/logo.png" alt="peerup" className="mx-auto mb-8 h-10 w-auto" />
            <h1 className="text-3xl font-extrabold tracking-tight text-stone-900 md:text-4xl">{tc.title}</h1>
            <p className="mt-2 text-stone-500">{tc.sub}</p>

            <div className="mt-8 grid gap-4 sm:grid-cols-2">
              <button
                onClick={() => setTrack("companion")}
                className="card-hover flex flex-col gap-2 border-2 border-sky-100 p-7 text-left active:scale-[0.98]"
              >
                <span className="text-4xl">🤝</span>
                <span className="mt-1 text-xl font-extrabold text-stone-900">{tr.companionTitle}</span>
                <span className="text-sm leading-relaxed text-stone-500">{tr.companionDesc}</span>
                <span className="mt-3 text-sm font-bold text-sky-600">{tr.companionCta} →</span>
              </button>
              <button
                onClick={() => setTrack("tour")}
                className="card-hover flex flex-col gap-2 border-2 border-emerald-100 p-7 text-left active:scale-[0.98]"
              >
                <span className="text-4xl">🎫</span>
                <span className="mt-1 text-xl font-extrabold text-stone-900">{tr.tourTitle}</span>
                <span className="text-sm leading-relaxed text-stone-500">{tr.tourDesc}</span>
                <span className="mt-3 text-sm font-bold text-emerald-600">{tr.tourCta} →</span>
              </button>
            </div>
          </div>
        ) : (
          /* ── 2단계: 투어 세계 법적 고지 동의 ── */
          <div className="w-full max-w-xl animate-fade-up">
            <div className="card overflow-hidden p-0">
              <div className="bg-gradient-to-br from-emerald-500 to-teal-600 px-7 py-8 text-white">
                <div className="mb-2 text-3xl">⚖️</div>
                <h1 className="text-xl font-extrabold md:text-2xl">{tl.gateTitle}</h1>
                <p className="mt-1.5 text-sm text-white/85">{tl.gateSub}</p>
              </div>

              <div className="flex flex-col gap-3 px-7 py-6">
                {[tl.point1, tl.point2, tl.point3].map((p, i) => (
                  <div key={i} className="flex gap-3 rounded-xl bg-stone-50 px-4 py-3">
                    <span className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-emerald-100 text-xs font-bold text-emerald-700">{i + 1}</span>
                    <p className="text-sm leading-relaxed text-stone-700">{p}</p>
                  </div>
                ))}

                <Link href="/legal" className="text-sm font-semibold text-emerald-600 hover:underline">
                  {tl.detailLink} →
                </Link>

                <label className="mt-2 flex cursor-pointer items-center gap-2.5 text-sm font-semibold text-stone-800">
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={(e) => setChecked(e.target.checked)}
                    className="h-5 w-5 accent-emerald-600"
                  />
                  {tl.checkLabel}
                </label>

                <div className="mt-2 flex items-center gap-3">
                  <button
                    // 뒤로 = 트랙 초기화 + 홈 (세계 경로 위에서 chooser가 억제되므로 홈에서 다시 고른다)
                    onClick={() => { setChecked(false); clearTrack(); router.push("/"); }}
                    className="btn-ghost text-sm"
                  >
                    {tl.backBtn}
                  </button>
                  <button
                    onClick={() => { if (checked) ackTourLegal(); }}
                    disabled={!checked}
                    className="btn-primary flex-1 py-3 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {tl.agreeBtn}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
