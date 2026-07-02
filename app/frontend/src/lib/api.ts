// 백엔드(Spring) API와 통신하는 공통 도우미.
// 매번 fetch를 직접 쓰면 중복이 많아지므로 한 곳에 모아둔다.

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

const TOKEN_KEY    = "accessToken";
const USER_NAME_KEY = "userName";

export function saveToken(token: string) {
  if (typeof window !== "undefined") localStorage.setItem(TOKEN_KEY, token);
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  if (typeof window !== "undefined") {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_NAME_KEY);
  }
}

export function saveUserName(name: string) {
  if (typeof window !== "undefined") localStorage.setItem(USER_NAME_KEY, name);
}

export function getUserName(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(USER_NAME_KEY);
}

type ApiOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: boolean; // true면 Authorization 헤더에 토큰을 붙인다
};

export async function api<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { method = "GET", body, auth = false } = options;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (auth) {
    const token = getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    // 백엔드가 보내는 {"error": "..."} 메시지를 꺼내 예외로 던진다.
    let message = "요청에 실패했습니다.";
    try {
      const data = await res.json();
      if (data?.error) message = data.error;
    } catch {
      // 본문이 비어있거나 JSON이 아니면 기본 메시지 사용
    }
    throw new Error(message);
  }

  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// 파일 업로드(multipart) 전용. FormData를 보낼 땐 Content-Type을 직접 지정하지 않는다
// (브라우저가 경계 문자열을 포함해 자동 설정해야 하기 때문).
export async function apiUpload<T>(
  path: string,
  formData: FormData,
  options: { auth?: boolean } = {}
): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.auth) {
    const token = getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers,
    body: formData,
  });

  if (!res.ok) {
    let message = "업로드에 실패했습니다.";
    try {
      const data = await res.json();
      if (data?.error) message = data.error;
    } catch {
      // 무시
    }
    throw new Error(message);
  }

  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
