import type { NextConfig } from "next";

const platformBackendUrl =
  process.env.PLATFORM_BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${platformBackendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
