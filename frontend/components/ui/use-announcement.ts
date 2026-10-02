import { useCallback, useEffect, useRef, useState } from "react";

/** Long enough for a screen reader to see the live region go empty before the message returns. */
const GAP_MS = 50;

/**
 * Text for an always-mounted polite `<output>`. `announce` clears the region and then sets the
 * message, so saying the same thing twice (resetting the same Login again) is announced twice:
 * a live region only speaks when its text changes. `null` means nothing has been announced yet.
 */
export function useAnnouncement() {
  const [text, setText] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => () => clearTimeout(timer.current), []);

  const announce = useCallback((message: string) => {
    clearTimeout(timer.current);
    setText("");
    timer.current = setTimeout(() => setText(message), GAP_MS);
  }, []);

  return [text, announce] as const;
}
