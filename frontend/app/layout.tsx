import type { Metadata } from "next";
import { inter } from "@/app/fonts";
import { ThemeScript } from "@/components/theme/theme-script";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "Remote Support Platform",
    template: "%s · Remote Support Platform",
  },
  description:
    "Shared system of record for Fleet resources, support Requests and monthly invoicing across Manager, Agent and Client roles.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable} suppressHydrationWarning>
      <head>
        <ThemeScript />
      </head>
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
