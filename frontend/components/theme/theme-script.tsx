// Runs before paint so a stored theme preference never flashes the wrong
// palette. Reads localStorage only; the CSS itself falls back to
// prefers-color-scheme when nothing is stored, so this script is an
// enhancement, not a requirement.
const THEME_INIT_SCRIPT = `
(function () {
  try {
    var stored = localStorage.getItem("rs-theme");
    if (stored === "light" || stored === "dark") {
      document.documentElement.setAttribute("data-theme", stored);
    }
  } catch (e) {}
})();
`;

export function ThemeScript() {
  return <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />;
}
