/** @type {import('next').NextConfig} */
const nextConfig = {
  // Spring 백엔드 API 주소 (개발용). 나중에 .env로 분리.
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
    // Kakao Maps JS SDK 키 (지도 시각화용, REST 키와 다름). 없으면 지도 대신 안내 문구.
    NEXT_PUBLIC_KAKAO_JS_KEY: process.env.NEXT_PUBLIC_KAKAO_JS_KEY ?? "",
  },
};

export default nextConfig;
