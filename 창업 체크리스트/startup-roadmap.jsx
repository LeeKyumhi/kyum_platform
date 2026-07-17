import { useState, useEffect } from "react";
import { Check, ChevronDown, MapPin } from "lucide-react";

// ---------------------------------------------
// 창업 로드맵 데이터
// 6개 단계(Phase), 총 42개 체크 항목
// 각 항목: id(저장용 고유값), title(할 일), desc(용어 설명 포함 상세 설명)
// ---------------------------------------------
const PHASES = [
  {
    id: "p0",
    num: "00",
    title: "시작 전 준비",
    duration: "1주",
    tasks: [
      {
        id: "p0-1",
        title: "투입 가능 시간 확정",
        desc: "주당 몇 시간을 쓸 수 있는지 현실적으로 정합니다. 직장·학업과 병행이라면 최소 주 15시간 확보를 권장해요. 시간이 정해져야 일정 계산이 가능합니다.",
      },
      {
        id: "p0-2",
        title: "데드라인과 실패 기준 정의",
        desc: "예: \"3개월 안에 MVP 출시, 6개월 안에 유료 고객 10명 없으면 피벗(방향 전환)\". 끝내는 기준이 없으면 끝나지 않는 좀비 프로젝트가 됩니다.",
      },
      {
        id: "p0-3",
        title: "학습·의사결정 기록 체계 만들기",
        desc: "개발하며 배운 것, 왜 그렇게 결정했는지를 기록합니다. Notion이나 GitHub 위키 추천. 나중에 같은 고민을 반복하지 않게 해줍니다.",
      },
    ],
  },
  {
    id: "p1",
    num: "01",
    title: "문제 검증",
    duration: "2~4주",
    tasks: [
      {
        id: "p1-1",
        title: "문제를 한 문장으로 정의",
        desc: "\"[누구]가 [상황]에서 겪는 [문제]\" 형식으로 적습니다. 솔루션(기능)이 아니라 문제 자체를 적는 것이 핵심이에요.",
      },
      {
        id: "p1-2",
        title: "타겟 고객 페르소나 작성",
        desc: "페르소나(persona)란 대표 고객을 구체적 인물로 그린 가상 프로필입니다. 나이, 직업, 하루 일과, 가장 불편한 순간까지 적어보세요.",
      },
      {
        id: "p1-3",
        title: "잠재 고객 인터뷰 10명 이상",
        desc: "\"이런 거 만들면 쓰실래요?\" 같은 유도질문은 금지. \"지금 그 문제를 어떻게 해결하세요? 뭐가 제일 불편하세요?\"처럼 과거 행동을 묻습니다.",
      },
      {
        id: "p1-4",
        title: "경쟁 서비스와 대안 조사",
        desc: "직접 경쟁 앱뿐 아니라 엑셀, 수작업, 카톡 같은 '대안'도 경쟁자입니다. 사람들이 현재 대안에 만족하고 있다면 전환시키기 어렵습니다.",
      },
      {
        id: "p1-5",
        title: "지불 의향 검증",
        desc: "말로 하는 \"쓸게요\"는 믿을 수 없습니다. 대기자 명단 이메일 수집, 사전 예약금처럼 실제 '행동'으로 확인하세요.",
      },
      {
        id: "p1-6",
        title: "Go / No-Go 결정",
        desc: "인터뷰 대상 중 30% 이상이 적극적 반응(먼저 연락, 결제 의사)을 보이면 진행. 아니면 문제를 다시 정의합니다. 여기서 접는 것도 성공입니다.",
      },
      {
        id: "p1-7",
        title: "규제·인허가 해당 여부 확인",
        desc: "아이템이 인허가 업종인지 개발 전에 확인합니다. 예: 외국인 대상 유상 관광안내는 관광통역안내사 자격, GPS 위치정보 수집은 위치기반서비스 신고, 결제·송금은 전자금융업 인허가. 늦게 발견할수록 되돌리는 비용이 커집니다.",
      },
    ],
  },
  {
    id: "p2",
    num: "02",
    title: "MVP 설계",
    duration: "1~2주",
    tasks: [
      {
        id: "p2-1",
        title: "핵심 가치 한 문장 정의",
        desc: "\"우리 서비스는 [고객]이 [문제]를 [기존 대비 이런 이점]으로 해결하게 한다\". 앞으로 모든 기능 추가·삭제 판단의 기준이 됩니다.",
      },
      {
        id: "p2-2",
        title: "기능 목록 작성 후 80% 삭제",
        desc: "떠오르는 기능을 전부 적은 뒤, 핵심 가치에 꼭 필요한 것만 남깁니다. MVP(Minimum Viable Product)는 '최소 기능 제품'이라는 뜻입니다.",
      },
      {
        id: "p2-3",
        title: "와이어프레임 그리기",
        desc: "와이어프레임(wireframe)은 색상·디자인 없이 화면 배치만 그린 설계도입니다. 종이나 Figma(무료 디자인 툴)로 전체 화면 흐름을 그려보세요.",
      },
      {
        id: "p2-4",
        title: "기술 스택 확정",
        desc: "추천 조합: Next.js(React 기반 풀스택 프레임워크) + Supabase(DB와 인증을 제공하는 서비스) + Vercel(클릭 몇 번으로 배포). 자료가 많아 막혔을 때 해결이 빠르고, 무료로 시작할 수 있습니다.",
      },
      {
        id: "p2-5",
        title: "DB 스키마 설계",
        desc: "스키마(schema)는 어떤 테이블에 어떤 컬럼을 두고 서로 어떻게 연결할지 그린 데이터 설계도입니다. 코드 작성 전에 종이에 먼저 그려보세요.",
      },
      {
        id: "p2-6",
        title: "API 명세 초안 작성",
        desc: "API는 프론트엔드와 서버가 데이터를 주고받는 약속된 통로입니다. \"GET /posts → 글 목록 반환\"처럼 필요한 엔드포인트 목록을 적습니다.",
      },
      {
        id: "p2-7",
        title: "주 단위 개발 일정 수립",
        desc: "기능별 마일스톤을 주 단위로 잡습니다. 예상 시간의 1.5~2배로 계획하세요. 처음에는 누구나 예상보다 오래 걸립니다.",
      },
      {
        id: "p2-8",
        title: "개인정보·위치정보 수집 항목 설계",
        desc: "스키마 단계에서 최소 수집 원칙(꼭 필요한 것만)을 반영합니다. 비밀번호는 해시(bcrypt 등) 저장이 법적 의무. GPS 등 위치정보를 받는다면 위치기반서비스 신고와 별도 위치정보 이용약관이 필요한지 함께 확인하세요.",
      },
    ],
  },
  {
    id: "p3",
    num: "03",
    title: "MVP 개발",
    duration: "4~8주",
    tasks: [
      {
        id: "p3-1",
        title: "Git 저장소 + 자동 배포 연결",
        desc: "GitHub에 코드를 올리고 Vercel과 연동하면 push할 때마다 자동 배포됩니다(CI/CD의 기본). 개발 첫날에 세팅하는 것이 정석입니다.",
      },
      {
        id: "p3-2",
        title: "DB 테이블 생성",
        desc: "설계한 스키마를 Supabase에서 실제 테이블로 만듭니다. RLS(Row Level Security, 사용자별로 자기 데이터만 접근하게 하는 행 단위 보안)도 함께 설정하세요.",
      },
      {
        id: "p3-3",
        title: "인증(로그인) 구현",
        desc: "비밀번호 처리를 절대 직접 만들지 마세요(보안 사고 위험). Supabase Auth나 Clerk 같은 인증 서비스로 이메일·소셜 로그인을 붙입니다.",
      },
      {
        id: "p3-4",
        title: "핵심 기능 API 개발",
        desc: "CRUD(Create·Read·Update·Delete, 생성·조회·수정·삭제)부터 만듭니다. 한 기능을 끝까지 완성한 뒤 다음 기능으로 넘어가세요.",
      },
      {
        id: "p3-5",
        title: "프론트엔드 핵심 화면 구현",
        desc: "와이어프레임의 화면을 실제로 만듭니다. 디자인 욕심은 금지, 동작이 우선입니다. 스타일링은 Tailwind CSS(유틸리티 클래스 방식 CSS 도구) 추천.",
      },
      {
        id: "p3-6",
        title: "결제 연동 (필요시)",
        desc: "토스페이먼츠(국내)나 Stripe(해외)를 사용합니다. PG(Payment Gateway, 결제 대행사)가 카드 처리를 대신하므로 카드 정보를 직접 다루지 않아도 됩니다.",
      },
      {
        id: "p3-7",
        title: "지인 테스트 5명",
        desc: "설명 없이 써보게 하고 어깨 너머로 관찰합니다. 설명해줘야만 쓸 수 있는 부분이 바로 고쳐야 할 부분입니다.",
      },
      {
        id: "p3-8",
        title: "배포 + 도메인 연결",
        desc: "도메인을 구입(가비아, Namecheap 등)해 Vercel에 연결합니다. HTTPS(암호화 통신)는 자동 적용됩니다.",
      },
      {
        id: "p3-9",
        title: "에러 추적·모니터링·백업 세팅",
        desc: "Sentry(에러 자동 수집 도구)로 오류 알림, UptimeRobot(무료)으로 서버 다운 감지, DB 자동 백업 활성화까지. 사용자가 알려주기 전에 장애를 먼저 아는 것이 1인 운영의 생명줄입니다.",
      },
    ],
  },
  {
    id: "p4",
    num: "04",
    title: "출시와 검증",
    duration: "4주~",
    tasks: [
      {
        id: "p4-1",
        title: "랜딩 페이지 제작",
        desc: "서비스 가치 한 문장 + 스크린샷 + 가입 버튼으로 구성된 소개 페이지입니다. 광고와 공유 링크가 도착하는 곳이 됩니다.",
      },
      {
        id: "p4-2",
        title: "분석 도구 설치",
        desc: "GA4(구글 애널리틱스)나 Mixpanel로 방문·클릭·이탈을 측정합니다. '감'이 아니라 데이터로 판단하기 위한 필수 도구입니다.",
      },
      {
        id: "p4-3",
        title: "첫 사용자 10~100명 확보",
        desc: "관련 커뮤니티, 오픈채팅, SNS, 지인에게 직접 알립니다. 초기에는 한 명 한 명 손으로 모으는 것이 정상입니다.",
      },
      {
        id: "p4-4",
        title: "피드백 채널 운영",
        desc: "카톡 오픈채팅, 인앱 피드백 버튼 등을 만듭니다. 초기 유저와의 직접 대화가 최고의 기획 자료입니다.",
      },
      {
        id: "p4-5",
        title: "리텐션 측정",
        desc: "리텐션(retention)은 재방문율입니다. 가입 1주 후 다시 돌아오는 비율이 핵심 지표예요. 이게 낮으면 마케팅을 해도 밑 빠진 독에 물 붓기입니다.",
      },
      {
        id: "p4-6",
        title: "주간 개선 사이클 확립",
        desc: "매주 피드백 정리 → 우선순위 결정 → 개발 → 배포를 반복합니다. 이 반복 속도가 대기업을 이기는 스타트업의 무기입니다.",
      },
      {
        id: "p4-7",
        title: "개인정보처리방침·이용약관 게시",
        desc: "회원가입을 받는 순간부터 개인정보보호법상 필수 — 유료화 때가 아니라 출시 시점입니다. 개인정보포털(privacy.go.kr) 작성 도구로 만들어 푸터에 게시하세요. 위치정보를 쓰면 위치정보 이용약관은 별도 문서입니다.",
      },
      {
        id: "p4-8",
        title: "서비스명 상표 검색·출원",
        desc: "한국은 먼저 쓴 사람이 아니라 먼저 출원한 사람이 이기는 선출원주의입니다. 키프리스(kipris.re.kr)에서 무료 검색 후, 서비스가 알려지기 전에 출원하세요(류당 5~6만원, 심사 약 1년).",
      },
    ],
  },
  {
    id: "p5",
    num: "05",
    title: "사업화",
    duration: "검증 후",
    tasks: [
      {
        id: "p5-1",
        title: "사업자등록",
        desc: "홈택스에서 무료, 하루면 완료됩니다. 개인사업자로 시작하세요(법인은 설립 비용과 관리 부담이 큼). 업종은 보통 정보통신업. 매출이 나기 전에 미리 해두면 가산세를 피하고 준비 지출의 부가세 공제도 받을 수 있습니다.",
      },
      {
        id: "p5-2",
        title: "통신판매업 신고 + 사업자정보 표시",
        desc: "온라인 유료 판매의 필수 절차. 순서: PG(결제대행사) 가입 → 구매안전서비스 이용확인증 발급 → 정부24 신고. 신고번호·상호·사업자번호 등을 푸터에 표시합니다(간이과세자는 신고 면제).",
      },
      {
        id: "p5-3",
        title: "유료화 실험",
        desc: "열성 유저 소수에게 먼저 유료 플랜을 제안합니다. 너무 낮게 시작해서 올리는 것보다, 적정가로 시작해 조정하는 편이 낫습니다.",
      },
      {
        id: "p5-4",
        title: "핵심 지표 대시보드 운영",
        desc: "MAU(월간 활성 사용자 수), 매출, 이탈률, CAC(고객 한 명을 데려오는 데 드는 비용)를 주간 단위로 추적합니다.",
      },
      {
        id: "p5-5",
        title: "법인 전환 검토",
        desc: "투자 유치, 공동창업자 지분 배분, 매출 성장이 생기면 법인이 필요해집니다. 그 전까지는 개인사업자로 충분합니다.",
      },
      {
        id: "p5-6",
        title: "투자 vs 자력 성장 결정",
        desc: "투자 유치(빠른 성장, 대신 지분 희석)와 부트스트래핑(bootstrapping, 매출만으로 성장) 중 아이템 성격에 맞게 선택합니다.",
      },
      {
        id: "p5-7",
        title: "중개 플랫폼이라면 정산·세무 구조 확정",
        desc: "판매자-구매자를 잇는 C2C라면: '통신판매중개자' 고지 문구, 판매자 정산은 PG 지급대행으로(대금이 내 계좌를 거치면 무등록 금융업 위험), 원천징수·소득자료 제출 의무를 세무사와 확정하세요.",
      },
    ],
  },
];

const TOTAL = PHASES.reduce((n, p) => n + p.tasks.length, 0);
const STORAGE_KEY = "startup-roadmap-progress";

// 색상 토큰: 종이 위의 짙은 소나무색 잉크 + 진행을 뜻하는 초록 하나만 사용
const C = {
  paper: "#F3F5F2",
  card: "#FFFFFF",
  ink: "#182420",
  muted: "#6C7A72",
  line: "#DCE3DD",
  accent: "#17805A",
  accentSoft: "#E4F0EA",
};

export default function StartupRoadmap() {
  const [checked, setChecked] = useState({});
  const [expanded, setExpanded] = useState(null);
  const [loading, setLoading] = useState(true);
  const [confirmReset, setConfirmReset] = useState(false);

  // 최초 1회: 저장된 진행상황 불러오기
  useEffect(() => {
    (async () => {
      let saved = {};
      try {
        if (window.storage) {
          const result = await window.storage.get(STORAGE_KEY, false);
          if (result && result.value) saved = JSON.parse(result.value);
        }
      } catch (e) {
        // 저장된 데이터가 아직 없는 경우 — 정상
      }
      setChecked(saved);
      // 완료되지 않은 첫 단계를 자동으로 펼침
      const firstOpen = PHASES.find((p) => p.tasks.some((t) => !saved[t.id]));
      setExpanded(firstOpen ? firstOpen.id : PHASES[0].id);
      setLoading(false);
    })();
  }, []);

  // 체크 토글 + 즉시 저장
  const toggle = async (taskId) => {
    const next = { ...checked, [taskId]: !checked[taskId] };
    setChecked(next);
    try {
      if (window.storage) {
        await window.storage.set(STORAGE_KEY, JSON.stringify(next), false);
      }
    } catch (e) {
      console.error("저장 실패:", e);
    }
  };

  const resetAll = async () => {
    if (!confirmReset) {
      setConfirmReset(true);
      setTimeout(() => setConfirmReset(false), 3000);
      return;
    }
    setChecked({});
    setConfirmReset(false);
    setExpanded(PHASES[0].id);
    try {
      if (window.storage) await window.storage.delete(STORAGE_KEY, false);
    } catch (e) {
      // 삭제할 데이터가 없어도 무시
    }
  };

  const doneCount = Object.values(checked).filter(Boolean).length;
  const pct = Math.round((doneCount / TOTAL) * 100);
  const phaseDone = (p) => p.tasks.filter((t) => checked[t.id]).length;
  const currentPhase = PHASES.find((p) => phaseDone(p) < p.tasks.length);

  if (loading) {
    return (
      <div
        className="min-h-screen flex items-center justify-center"
        style={{ background: C.paper, color: C.muted, fontFamily: "'IBM Plex Sans KR', sans-serif" }}
      >
        진행상황을 불러오는 중…
      </div>
    );
  }

  return (
    <div className="min-h-screen" style={{ background: C.paper, color: C.ink }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Gowun+Batang:wght@400;700&family=IBM+Plex+Sans+KR:wght@400;500;700&family=IBM+Plex+Mono:wght@400;500&display=swap');
        * { -webkit-tap-highlight-color: transparent; }
        @media (prefers-reduced-motion: reduce) {
          * { transition: none !important; }
        }
      `}</style>

      <div
        className="max-w-xl mx-auto px-5 pt-10 pb-16"
        style={{ fontFamily: "'IBM Plex Sans KR', sans-serif" }}
      >
        {/* ---------- 헤더 ---------- */}
        <header className="mb-10">
          <p
            className="text-xs tracking-widest mb-3"
            style={{ color: C.accent, fontFamily: "'IBM Plex Mono', monospace" }}
          >
            STARTUP ROADMAP
          </p>
          <h1
            className="text-3xl leading-snug mb-1"
            style={{ fontFamily: "'Gowun Batang', serif", fontWeight: 700 }}
          >
            아이디어에서
            <br />
            사업이 되기까지
          </h1>
          <p className="text-sm mt-3 leading-relaxed" style={{ color: C.muted }}>
            6개 단계, {TOTAL}개의 관문. 순서대로 하나씩 통과하면 됩니다.
          </p>

          {/* 전체 진행률 */}
          <div className="mt-6">
            <div className="flex items-baseline justify-between mb-2">
              <span
                className="text-2xl"
                style={{ fontFamily: "'IBM Plex Mono', monospace", color: C.accent }}
              >
                {pct}%
              </span>
              <span className="text-xs" style={{ color: C.muted, fontFamily: "'IBM Plex Mono', monospace" }}>
                {doneCount} / {TOTAL} 완료
              </span>
            </div>
            <div className="h-1.5 rounded-full overflow-hidden" style={{ background: C.line }}>
              <div
                className="h-full rounded-full"
                style={{ width: `${pct}%`, background: C.accent, transition: "width 0.4s ease" }}
              />
            </div>
            {pct === 100 && (
              <p className="text-sm mt-3" style={{ color: C.accent }}>
                모든 단계 완료 — 이제 당신은 창업자입니다.
              </p>
            )}
          </div>
        </header>

        {/* ---------- 단계 목록 (루트 라인) ---------- */}
        <div className="relative">
          {/* 세로 루트 라인 */}
          <div
            className="absolute left-4 top-4 bottom-4 w-px"
            style={{ background: C.line }}
          />

          <div className="space-y-4">
            {PHASES.map((phase) => {
              const done = phaseDone(phase);
              const total = phase.tasks.length;
              const complete = done === total;
              const isOpen = expanded === phase.id;
              const isCurrent = currentPhase && currentPhase.id === phase.id;

              return (
                <section key={phase.id} className="relative pl-12">
                  {/* 스테이션 노드 */}
                  <div
                    className="absolute left-0 top-3 w-8 h-8 rounded-full flex items-center justify-center text-xs"
                    style={{
                      fontFamily: "'IBM Plex Mono', monospace",
                      background: complete ? C.accent : C.card,
                      color: complete ? "#fff" : isCurrent ? C.accent : C.muted,
                      border: `1.5px solid ${complete || isCurrent ? C.accent : C.line}`,
                      transition: "all 0.3s ease",
                    }}
                  >
                    {complete ? <Check size={15} strokeWidth={3} /> : phase.num}
                  </div>

                  {/* 단계 카드 */}
                  <div
                    className="rounded-xl overflow-hidden"
                    style={{
                      background: C.card,
                      border: `1px solid ${isCurrent && !complete ? C.accent : C.line}`,
                    }}
                  >
                    <button
                      onClick={() => setExpanded(isOpen ? null : phase.id)}
                      className="w-full text-left px-4 py-4 flex items-center gap-3"
                    >
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h2
                            className="text-lg"
                            style={{ fontFamily: "'Gowun Batang', serif", fontWeight: 700 }}
                          >
                            {phase.title}
                          </h2>
                          {isCurrent && !complete && (
                            <span
                              className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full"
                              style={{ background: C.accentSoft, color: C.accent }}
                            >
                              <MapPin size={11} /> 지금 여기
                            </span>
                          )}
                        </div>
                        <p
                          className="text-xs mt-1"
                          style={{ color: C.muted, fontFamily: "'IBM Plex Mono', monospace" }}
                        >
                          {phase.duration} · {done}/{total}
                        </p>
                      </div>
                      <ChevronDown
                        size={18}
                        style={{
                          color: C.muted,
                          transform: isOpen ? "rotate(180deg)" : "none",
                          transition: "transform 0.25s ease",
                          flexShrink: 0,
                        }}
                      />
                    </button>

                    {/* 할 일 목록 */}
                    {isOpen && (
                      <ul style={{ borderTop: `1px solid ${C.line}` }}>
                        {phase.tasks.map((task) => {
                          const isDone = !!checked[task.id];
                          return (
                            <li key={task.id} style={{ borderBottom: `1px solid ${C.line}` }}>
                              <button
                                onClick={() => toggle(task.id)}
                                className="w-full text-left px-4 py-3.5 flex gap-3 items-start"
                              >
                                <span
                                  className="mt-0.5 w-5 h-5 rounded flex items-center justify-center flex-shrink-0"
                                  style={{
                                    background: isDone ? C.accent : "transparent",
                                    border: `1.5px solid ${isDone ? C.accent : C.line}`,
                                    transition: "all 0.2s ease",
                                  }}
                                >
                                  {isDone && <Check size={13} strokeWidth={3} color="#fff" />}
                                </span>
                                <span className="min-w-0">
                                  <span
                                    className="block text-sm font-medium leading-snug"
                                    style={{
                                      color: isDone ? C.muted : C.ink,
                                      textDecoration: isDone ? "line-through" : "none",
                                    }}
                                  >
                                    {task.title}
                                  </span>
                                  <span
                                    className="block text-xs mt-1 leading-relaxed"
                                    style={{ color: C.muted }}
                                  >
                                    {task.desc}
                                  </span>
                                </span>
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>
                </section>
              );
            })}
          </div>
        </div>

        {/* ---------- 초기화 ---------- */}
        <div className="mt-10 text-center">
          <button
            onClick={resetAll}
            className="text-xs px-3 py-2 rounded-lg"
            style={{
              color: confirmReset ? "#fff" : C.muted,
              background: confirmReset ? "#B4482E" : "transparent",
              border: `1px solid ${confirmReset ? "#B4482E" : C.line}`,
              transition: "all 0.2s ease",
            }}
          >
            {confirmReset ? "한 번 더 누르면 전체 초기화됩니다" : "진행상황 초기화"}
          </button>
        </div>
      </div>
    </div>
  );
}
