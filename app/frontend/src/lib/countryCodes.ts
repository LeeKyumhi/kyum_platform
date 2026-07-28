// 국가별 전화 국가번호(ITU E.164). 결제용 연락처 입력에서 쓴다.
//
// 국가 '이름'은 데이터로 갖고 있지 않다 — 브라우저 내장 Intl.DisplayNames로 현재 언어(ko/en/zh)에 맞춰
// 만든다. 새 패키지 없이 3개 언어를 다 커버하고, i18n.ts에 200개 × 3언어를 넣지 않아도 된다.
// 국기 이모지도 ISO 코드에서 계산한다(regional indicator symbol).

import type { Lang } from "./i18n";

// "ISO2:국가번호" 목록. 배열 리터럴보다 훨씬 짧아 파일이 가볍다.
const RAW =
  "AF:93,AL:355,DZ:213,AD:376,AO:244,AR:54,AM:374,AU:61,AT:43,AZ:994,BS:1,BH:973,BD:880,BB:1," +
  "BY:375,BE:32,BZ:501,BJ:229,BT:975,BO:591,BA:387,BW:267,BR:55,BN:673,BG:359,BF:226,BI:257," +
  "KH:855,CM:237,CA:1,CV:238,CF:236,TD:235,CL:56,CN:86,CO:57,KM:269,CG:242,CD:243,CR:506,CI:225," +
  "HR:385,CU:53,CY:357,CZ:420,DK:45,DJ:253,DM:1,DO:1,EC:593,EG:20,SV:503,GQ:240,ER:291,EE:372," +
  "ET:251,FJ:679,FI:358,FR:33,GA:241,GM:220,GE:995,DE:49,GH:233,GR:30,GD:1,GT:502,GN:224,GW:245," +
  "GY:592,HT:509,HN:504,HK:852,HU:36,IS:354,IN:91,ID:62,IR:98,IQ:964,IE:353,IL:972,IT:39,JM:1," +
  "JP:81,JO:962,KZ:7,KE:254,KI:686,KW:965,KG:996,LA:856,LV:371,LB:961,LS:266,LR:231,LY:218," +
  "LI:423,LT:370,LU:352,MO:853,MG:261,MW:265,MY:60,MV:960,ML:223,MT:356,MH:692,MR:222,MU:230," +
  "MX:52,FM:691,MD:373,MC:377,MN:976,ME:382,MA:212,MZ:258,MM:95,NA:264,NR:674,NP:977,NL:31," +
  "NZ:64,NI:505,NE:227,NG:234,KP:850,MK:389,NO:47,OM:968,PK:92,PW:680,PS:970,PA:507,PG:675," +
  "PY:595,PE:51,PH:63,PL:48,PT:351,PR:1,QA:974,RO:40,RU:7,RW:250,WS:685,SM:378,SA:966,SN:221," +
  "RS:381,SC:248,SL:232,SG:65,SK:421,SI:386,SB:677,SO:252,ZA:27,KR:82,SS:211,ES:34,LK:94,SD:249," +
  "SR:597,SE:46,CH:41,SY:963,TW:886,TJ:992,TZ:255,TH:66,TL:670,TG:228,TO:676,TT:1,TN:216,TR:90," +
  "TM:993,TV:688,UG:256,UA:380,AE:971,GB:44,US:1,UY:598,UZ:998,VU:678,VA:39,VE:58,VN:84,YE:967," +
  "ZM:260,ZW:263";

export type Country = { iso: string; dial: string };

export const COUNTRIES: Country[] = RAW.split(",").map((pair) => {
  const [iso, dial] = pair.split(":");
  return { iso, dial };
});

/** 방한 여행자가 많은 나라 — 목록 맨 위에 따로 보여준다. */
export const POPULAR_ISO = [
  "KR", "US", "JP", "CN", "TW", "HK", "SG", "TH", "VN", "PH", "MY", "ID", "GB", "DE", "FR", "AU", "CA",
];

const LOCALE: Record<Lang, string> = { ko: "ko", en: "en", zh: "zh" };

/** ISO2 → 국기 이모지. "KR" → 🇰🇷 (regional indicator symbol 2개) */
export function flagOf(iso: string): string {
  return String.fromCodePoint(...[...iso].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}

/** 현재 언어의 국가명. Intl.DisplayNames를 못 쓰는 환경이면 ISO 코드로 폴백한다. */
export function countryName(iso: string, lang: Lang): string {
  try {
    return new Intl.DisplayNames([LOCALE[lang]], { type: "region" }).of(iso) ?? iso;
  } catch {
    return iso;
  }
}

/** 인기 국가 먼저, 나머지는 현재 언어 기준 가나다/알파벳순. */
export function groupedCountries(lang: Lang): { popular: Country[]; rest: Country[] } {
  // POPULAR_ISO에 있는데 RAW에 없으면 목록에서 조용히 사라진다 — 실제로 KR이 그렇게 빠져
  // "한국을 고를 수 없다"는 버그가 됐다. 다시 생기면 콘솔에 드러나게 한다.
  const missing = POPULAR_ISO.filter((iso) => !COUNTRIES.some((c) => c.iso === iso));
  if (missing.length) console.warn("countryCodes: POPULAR_ISO에 있으나 국가번호 데이터가 없음", missing);

  const popular = POPULAR_ISO
    .map((iso) => COUNTRIES.find((c) => c.iso === iso))
    .filter((c): c is Country => !!c);
  const collator = new Intl.Collator(LOCALE[lang]);
  const rest = COUNTRIES
    .filter((c) => !POPULAR_ISO.includes(c.iso))
    .sort((a, b) => collator.compare(countryName(a.iso, lang), countryName(b.iso, lang)));
  return { popular, rest };
}

/** 국가번호 + 국내 입력분을 E.164로 합친다. 앞자리 0(국내 통화용 트렁크 프리픽스)은 떨어뜨린다. */
export function toE164(dial: string, localNumber: string): string {
  const digits = localNumber.replace(/\D/g, "").replace(/^0+/, "");
  return `+${dial}${digits}`;
}

/** E.164 형식인지 — 서버(UserController)와 같은 규칙. */
export function isValidE164(value: string): boolean {
  return /^\+[1-9]\d{6,14}$/.test(value);
}
