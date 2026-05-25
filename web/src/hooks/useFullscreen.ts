import { useEffect, useState } from "react";

type UseFullscreenOptions = {
  /** When true, Escape exits fullscreen before other handlers run. */
  enabled?: boolean;
};

export function useFullscreen({ enabled = true }: UseFullscreenOptions = {}) {
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    if (!enabled || !isFullscreen) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        setIsFullscreen(false);
      }
    };

    window.addEventListener("keydown", onKeyDown, true);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown, true);
    };
  }, [enabled, isFullscreen]);

  return {
    isFullscreen,
    setIsFullscreen,
    toggleFullscreen: () => setIsFullscreen((value) => !value),
  };
}
