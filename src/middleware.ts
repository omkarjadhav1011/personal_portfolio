import { NextRequest, NextResponse } from "next/server";

const COOKIE_NAME = "admin_token";

function decodeBase64Url(input: string): Uint8Array {
  const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);

  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }

  return bytes;
}

async function verifyHs256Jwt(token: string, secretValue: string): Promise<boolean> {
  const parts = token.split(".");
  if (parts.length !== 3) return false;

  const [headerPart, payloadPart, signaturePart] = parts;

  try {
    const header = JSON.parse(new TextDecoder().decode(decodeBase64Url(headerPart))) as {
      alg?: string;
      typ?: string;
    };
    const payload = JSON.parse(new TextDecoder().decode(decodeBase64Url(payloadPart))) as {
      exp?: number;
    };

    if (header.alg !== "HS256") return false;
    if (typeof payload.exp === "number" && payload.exp <= Math.floor(Date.now() / 1000)) {
      return false;
    }

    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(secretValue),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    );

    const signature = decodeBase64Url(signaturePart);
    const data = new TextEncoder().encode(`${headerPart}.${payloadPart}`);

    return crypto.subtle.verify("HMAC", key, signature, data);
  } catch {
    return false;
  }
}

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Only apply to /admin routes
  if (!pathname.startsWith("/admin")) return NextResponse.next();

  const isLoginPage = pathname === "/admin/login";
  const token = request.cookies.get(COOKIE_NAME)?.value;

  // Try to verify JWT
  let isValid = false;
  const secret = process.env.JWT_SECRET;
  if (token && secret) {
    isValid = await verifyHs256Jwt(token, secret);
  }

  // Already logged in → redirect away from login page
  if (isLoginPage && isValid) {
    return NextResponse.redirect(new URL("/admin", request.url));
  }

  // Not logged in → redirect to login page
  if (!isLoginPage && !isValid) {
    return NextResponse.redirect(new URL("/admin/login", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/admin", "/admin/:path*"],
};
