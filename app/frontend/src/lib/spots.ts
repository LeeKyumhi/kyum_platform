// 한국 대표 명소 정적 데이터.
// 랜딩("/") 셸프와 명소 상세("/spots/[slug]") 페이지에서 공유한다.
// 이미지: Wikimedia Commons. 좌표: 실제 좌표(카카오 주변 검색 기준점으로 사용).

export type LocalizedText = { ko: string; en: string; zh: string };

export type Spot = {
  slug: string;
  img: string;
  name: LocalizedText;
  city: LocalizedText;
  latitude: number;
  longitude: number;
  description: LocalizedText;
};

const WM = "https://upload.wikimedia.org/wikipedia/commons/thumb";

export const SPOTS: Spot[] = [
  {
    slug: "gyeongbokgung",
    img: `${WM}/5/5d/Gyeonghoeru_%28Royal_Banquet_Hall%29_at_Gyeongbokgung_Palace%2C_Seoul.jpg/960px-Gyeonghoeru_%28Royal_Banquet_Hall%29_at_Gyeongbokgung_Palace%2C_Seoul.jpg`,
    name: { ko: "경복궁", en: "Gyeongbokgung", zh: "景福宫" },
    city: { ko: "서울", en: "Seoul", zh: "首尔" },
    latitude: 37.579617,
    longitude: 126.977041,
    description: {
      ko: "1395년 태조 이성계가 세운 조선왕조의 법궁으로, 서울 5대 궁궐 중 가장 큰 규모를 자랑합니다. 광화문 앞에서 열리는 수문장 교대의식과 연못 위에 떠 있는 듯한 경회루의 풍경이 특히 유명합니다. 경내에 국립고궁박물관과 국립민속박물관이 있어 함께 둘러보기 좋고, 한복을 입고 방문하면 무료로 입장할 수 있습니다.",
      en: "Built in 1395 by King Taejo, founder of the Joseon dynasty, Gyeongbokgung is the largest and most iconic of Seoul's five grand palaces. Don't miss the royal guard changing ceremony at Gwanghwamun Gate and the picture-perfect Gyeonghoeru Pavilion rising over its lotus pond. The National Palace Museum and National Folk Museum sit on the palace grounds, and admission is free if you visit wearing hanbok.",
      zh: "景福宫建于1395年，由朝鲜王朝开国之君太祖李成桂所建，是首尔五大宫阙中规模最大、最具代表性的正宫。光化门前的守门将换岗仪式和莲池上的庆会楼景色尤为著名。宫内还设有国立古宫博物馆和国立民俗博物馆，穿韩服参观可免费入场。",
    },
  },
  {
    slug: "n-seoul-tower",
    img: `${WM}/b/b4/N_Seoul_Tower_complex_staircase.jpg/960px-N_Seoul_Tower_complex_staircase.jpg`,
    name: { ko: "N서울타워", en: "N Seoul Tower", zh: "N首尔塔" },
    city: { ko: "서울", en: "Seoul", zh: "首尔" },
    latitude: 37.5512,
    longitude: 126.9882,
    description: {
      ko: "남산 정상에 자리한 서울의 상징적인 전망 타워로, 해발 약 480m 높이에서 서울 전경을 360도로 감상할 수 있습니다. 남산 케이블카를 타고 올라가는 길과 전망 테라스 난간에 빼곡히 걸린 '사랑의 자물쇠'가 유명합니다. 해질 무렵 방문하면 노을과 야경을 한 번에 즐길 수 있어 데이트 코스로도 인기가 높습니다.",
      en: "Perched on top of Namsan Mountain, N Seoul Tower is the city's landmark observation tower, offering sweeping 360-degree views of Seoul from nearly 480 meters above sea level. Ride the Namsan cable car up and look for the thousands of 'love locks' fastened along the terrace railings. Come around sunset to catch golden hour and the city lights in a single visit — it's one of Seoul's favorite date spots.",
      zh: "N首尔塔矗立在南山山顶，是首尔的地标性观景塔，可在海拔近480米的高度360度俯瞰首尔全景。乘坐南山缆车上山，观景台护栏上挂满的“爱情锁”是这里的著名景观。日落时分前来，可以一次欣赏晚霞与夜景，是首尔最受欢迎的约会胜地之一。",
    },
  },
  {
    slug: "bukchon-hanok-village",
    img: `${WM}/4/41/Bukchon-ro_11-gil_street_with_hanok_houses_at_blue_hour_in_Bukchon_Hanok_Village_Seoul.jpg/960px-Bukchon-ro_11-gil_street_with_hanok_houses_at_blue_hour_in_Bukchon_Hanok_Village_Seoul.jpg`,
    name: { ko: "북촌 한옥마을", en: "Bukchon Hanok Village", zh: "北村韩屋村" },
    city: { ko: "서울", en: "Seoul", zh: "首尔" },
    latitude: 37.5826,
    longitude: 126.983,
    description: {
      ko: "경복궁과 창덕궁 사이 언덕에 자리한 600년 역사의 전통 한옥 마을로, 지금도 주민들이 실제로 거주하는 동네입니다. 기와지붕 너머로 서울 도심이 내려다보이는 '북촌 8경' 골목길은 서울에서 가장 사랑받는 사진 명소 중 하나입니다. 한복을 빌려 입고 골목을 거닐거나 전통 공방 체험을 해보세요. 주거 지역인 만큼 조용히 둘러보는 매너가 필요합니다.",
      en: "Nestled on the hills between Gyeongbokgung and Changdeokgung palaces, Bukchon is a 600-year-old neighborhood of traditional hanok houses where people still live today. Its famous 'Eight Views' — narrow lanes where tiled rooftops frame the modern Seoul skyline — are among the city's most photographed scenes. Rent a hanbok, wander the alleys, or try a traditional craft workshop. Since it's a residential area, please explore quietly.",
      zh: "北村韩屋村坐落在景福宫与昌德宫之间的山坡上，是一处拥有600年历史的传统韩屋街区，至今仍有居民居住。著名的“北村八景”——瓦片屋顶与现代首尔天际线交相辉映的小巷——是首尔最受欢迎的拍照胜地之一。可以租一套韩服漫步小巷，或体验传统手工艺工坊。由于这里是居民区，参观时请保持安静。",
    },
  },
  {
    slug: "haeundae",
    img: `${WM}/a/a2/Haeundae_Beach_in_Busan.jpg/960px-Haeundae_Beach_in_Busan.jpg`,
    name: { ko: "해운대", en: "Haeundae Beach", zh: "海云台" },
    city: { ko: "부산", en: "Busan", zh: "釜山" },
    latitude: 35.1587,
    longitude: 129.1604,
    description: {
      ko: "부산을 대표하는 해변으로, 약 1.5km의 백사장을 따라 고층 빌딩 스카이라인이 펼쳐지는 이국적인 풍경이 매력입니다. 여름 성수기에는 파라솔이 백사장을 가득 채우고, 모래축제 등 다양한 행사가 일 년 내내 열립니다. 해변 서쪽 끝 동백섬 산책로를 걷고, 근처 시장에서 신선한 해산물도 꼭 맛보세요.",
      en: "Haeundae is Korea's most famous beach — 1.5 kilometers of white sand set against Busan's dramatic high-rise skyline. In summer the beach fills with parasols and hosts events like the Haeundae Sand Festival, while the waterfront stays lively year-round. Walk the trail around Dongbaek Island at the beach's western end, and don't leave without trying fresh seafood at the nearby markets.",
      zh: "海云台是韩国最著名的海滩，1.5公里的白沙滩与釜山壮观的高楼天际线相映成趣。夏季沙滩上遮阳伞林立，还会举办海云台沙雕节等各种活动，海滨全年热闹非凡。可以沿着海滩西端的冬柏岛步道散步，也别错过附近市场的新鲜海产。",
    },
  },
  {
    slug: "seongsan-ilchulbong",
    img: `${WM}/1/19/Hydrangea_macrophylla_in_front_of_Seongsan_Ilchulbong_volcano_at_blue_hour_in_Jeju_Island_South_Korea.jpg/960px-Hydrangea_macrophylla_in_front_of_Seongsan_Ilchulbong_volcano_at_blue_hour_in_Jeju_Island_South_Korea.jpg`,
    name: { ko: "성산일출봉", en: "Seongsan Ilchulbong", zh: "城山日出峰" },
    city: { ko: "제주", en: "Jeju", zh: "济州" },
    latitude: 33.4587,
    longitude: 126.9425,
    description: {
      ko: "제주 동쪽 해안에 우뚝 솟은 수성화산체로, 약 5천 년 전 바닷속 화산 분출로 만들어진 유네스코 세계자연유산입니다. 해발 182m 정상까지는 30분 정도면 오를 수 있으며, 거대한 사발 모양의 분화구와 탁 트인 바다 풍경이 장관입니다. '성산 일출'은 제주 최고의 절경으로 꼽히며, 봉우리 아래에서는 제주 해녀의 물질 공연도 볼 수 있습니다.",
      en: "Rising dramatically from the sea on Jeju's eastern coast, Seongsan Ilchulbong ('Sunrise Peak') is a UNESCO World Heritage tuff cone formed by an underwater volcanic eruption around 5,000 years ago. A 30-minute climb brings you to the 182-meter summit, where a vast bowl-shaped crater opens toward the horizon. It's Jeju's most celebrated sunrise spot, and at the base you can watch Jeju's famous haenyeo free divers perform.",
      zh: "城山日出峰耸立在济州岛东海岸，是约5000年前海底火山喷发形成的凝灰岩锥，已被列入联合国教科文组织世界自然遗产。攀登约30分钟即可到达182米高的山顶，眺望巨大的碗状火山口和辽阔海景。这里是济州观赏日出的最佳地点，山脚下还能欣赏济州海女的潜水表演。",
    },
  },
  {
    slug: "jeonju-hanok-village",
    img: `${WM}/2/22/Jeonju_Hanok_Maeul_02.jpg/960px-Jeonju_Hanok_Maeul_02.jpg`,
    name: { ko: "전주 한옥마을", en: "Jeonju Hanok Village", zh: "全州韩屋村" },
    city: { ko: "전주", en: "Jeonju", zh: "全州" },
    latitude: 35.8151,
    longitude: 127.1535,
    description: {
      ko: "700여 채의 한옥이 밀집한 국내 최대 규모의 한옥마을로, '맛의 고장' 전주의 중심지입니다. 전주비빔밥과 콩나물국밥, 길거리 간식까지 — 유네스코 음식창의도시로 선정된 미식 여행지이기도 합니다. 태조 이성계의 어진을 모신 경기전과 로마네스크 양식의 전동성당이 마을 바로 옆에 있어 한복을 입고 함께 둘러보기 좋습니다.",
      en: "With over 700 traditional houses packed into its lanes, Jeonju Hanok Village is Korea's largest hanok district and the heart of the country's food capital. Come hungry: Jeonju bibimbap, bean-sprout soup and endless street snacks earned the city its UNESCO City of Gastronomy title. Right next to the village stand Gyeonggijeon Shrine, home to the royal portrait of King Taejo, and the Romanesque Jeondong Cathedral — best explored in a rented hanbok.",
      zh: "全州韩屋村汇集了700多座传统韩屋，是韩国规模最大的韩屋街区，也是“美食之都”全州的中心。全州拌饭、豆芽汤饭和各式街头小吃，让这里成为联合国教科文组织认定的美食创意城市。村旁的庆基殿供奉着太祖李成桂的御真，罗马式风格的殿洞圣堂也近在咫尺，很适合穿着韩服一同游览。",
    },
  },
];

export function getSpot(slug: string): Spot | undefined {
  return SPOTS.find((s) => s.slug === slug);
}

// Kakao 장소명 ↔ 큐레이션 명소(SPOTS) 매칭 — 대소문자 무시 양방향 부분 일치.
// 일반 식당/카페는 매칭되지 않는 것이 정상이며, 그 경우 그라디언트 타일로 폴백한다.
// /explore, /trips/[id], PlaceDetailModal 셋이 공유하는 단일 소스.
export function matchSpot(placeName: string): Spot | undefined {
  const n = placeName.trim().toLowerCase();
  if (!n) return undefined;
  return SPOTS.find((s) =>
    [s.name.ko, s.name.en, s.name.zh].some((raw) => {
      const v = raw.trim().toLowerCase();
      return n.includes(v) || v.includes(n);
    })
  );
}
