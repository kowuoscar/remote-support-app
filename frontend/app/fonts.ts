import localFont from "next/font/local";

/**
 * Inter, self-hosted as a variable font (weights 100–900, roman + italic).
 * Files live in ./fonts and are committed to the repo — no Google Fonts CDN,
 * no runtime fetch. One family carries headings, labels, body and data
 * across all three surfaces, per the approved design direction.
 */
export const inter = localFont({
  src: [
    {
      path: "./fonts/InterVariable.woff2",
      weight: "100 900",
      style: "normal",
    },
    {
      path: "./fonts/InterVariable-Italic.woff2",
      weight: "100 900",
      style: "italic",
    },
  ],
  variable: "--font-inter",
  display: "swap",
});
