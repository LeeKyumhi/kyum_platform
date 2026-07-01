/** @type {import('next').NextConfig} */
const nextConfig = {
  // Spring 백엔드 API 주소 (개발용). 나중에 .env로 분리.
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
  },
};

export default nextConfig;
