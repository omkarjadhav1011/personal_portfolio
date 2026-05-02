import { jwtVerify } from "jose";
import { NextRequest, NextResponse } from "next/server";

const COOKIE_NAME = "admin_token";

async function verifyHs256Jwt(token: string, secretValue: string): Promise<boolean> {
  try {
    await jwtVerify(token, new TextEncoder().encode(secretValue), {
      algorithms: ["HS256"],
    });
    return true;
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
