"use client";

import { useEffect, useState } from "react";

/**
 * Demonstrates the Operate-mode loading convention (skeletons, not spinners,
 * in the middle of content) against data that is otherwise available
 * instantly because it's local demo data. Real data-fetching screens will
 * replace this with actual request state in a later ticket.
 */
export function useSimulatedLoad(delayMs = 550): boolean {
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const id = window.setTimeout(() => setLoading(false), delayMs);
    return () => window.clearTimeout(id);
  }, [delayMs]);

  return loading;
}
