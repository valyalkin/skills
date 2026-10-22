'use client';

import { useReportWebVitals } from 'next/web-vitals';

/** Mounted once in the root layout; reports LCP, CLS, INP and friends. */
export default function WebVitals() {
  useReportWebVitals((metric) => {
    // Swap for the analytics endpoint; keep it non-blocking.
    console.info(
      JSON.stringify({
        level: 'info',
        metric: metric.name,
        value: metric.value,
      }),
    );
  });

  return null;
}
