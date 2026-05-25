import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ComponentType,
  type CSSProperties,
  type ReactNode,
} from "react";
import { AlertCircle, CheckCircle2, Info, X, XCircle } from "lucide-react";
import { cn } from "@/lib/cn";

type ToastType = "success" | "error" | "warning" | "info";

type Toast = {
  id: string;
  type: ToastType;
  title: string;
  description?: string;
  duration?: number;
};

type ToastView = Toast & {
  exiting?: boolean;
  paused?: boolean;
};

type ToastContextValue = {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, "id">) => void;
  removeToast: (id: string) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

const EXIT_MS = 220;
const MAX_VISIBLE = 5;

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastView[]>([]);
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const clearTimer = useCallback((id: string) => {
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
  }, []);

  const dismiss = useCallback(
    (id: string) => {
      clearTimer(id);
      setToasts((prev) =>
        prev.map((t) => (t.id === id ? { ...t, exiting: true } : t)),
      );
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, EXIT_MS);
    },
    [clearTimer],
  );

  const scheduleDismiss = useCallback(
    (id: string, duration: number) => {
      clearTimer(id);
      if (duration <= 0) return;
      const timer = setTimeout(() => dismiss(id), duration);
      timersRef.current.set(id, timer);
    },
    [clearTimer, dismiss],
  );

  const addToast = useCallback(
    (toast: Omit<Toast, "id">) => {
      const id = crypto.randomUUID();
      const duration = toast.duration ?? 5000;
      setToasts((prev) => [{ ...toast, id, duration }, ...prev].slice(0, MAX_VISIBLE));
      scheduleDismiss(id, duration);
    },
    [scheduleDismiss],
  );

  const setPaused = useCallback(
    (id: string, paused: boolean) => {
      setToasts((prev) => {
        const target = prev.find((t) => t.id === id);
        if (!target || target.exiting) return prev;

        if (paused) {
          clearTimer(id);
        } else {
          scheduleDismiss(id, target.duration ?? 5000);
        }

        return prev.map((t) => (t.id === id ? { ...t, paused } : t));
      });
    },
    [clearTimer, scheduleDismiss],
  );

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach(clearTimeout);
      timers.clear();
    };
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, addToast, removeToast: dismiss }}>
      {children}
      <ToastContainer toasts={toasts} onDismiss={dismiss} onPauseChange={setPaused} />
    </ToastContext.Provider>
  );
}

type ToastVisual = {
  Icon: ComponentType<{ className?: string }>;
  accent: string;
  iconWrap: string;
  icon: string;
  progress: string;
};

const toastVisuals: Record<ToastType, ToastVisual> = {
  success: {
    Icon: CheckCircle2,
    accent: "bg-emerald-500",
    iconWrap: "bg-emerald-500/10 ring-emerald-500/20",
    icon: "text-emerald-600 dark:text-emerald-400",
    progress: "bg-emerald-500/80",
  },
  error: {
    Icon: XCircle,
    accent: "bg-rose-500",
    iconWrap: "bg-rose-500/10 ring-rose-500/20",
    icon: "text-rose-600 dark:text-rose-400",
    progress: "bg-rose-500/80",
  },
  warning: {
    Icon: AlertCircle,
    accent: "bg-amber-500",
    iconWrap: "bg-amber-500/10 ring-amber-500/20",
    icon: "text-amber-600 dark:text-amber-400",
    progress: "bg-amber-500/80",
  },
  info: {
    Icon: Info,
    accent: "bg-blue-500",
    iconWrap: "bg-blue-500/10 ring-blue-500/20",
    icon: "text-blue-600 dark:text-blue-400",
    progress: "bg-blue-500/80",
  },
};

function ToastContainer({
  toasts,
  onDismiss,
  onPauseChange,
}: {
  toasts: ToastView[];
  onDismiss: (id: string) => void;
  onPauseChange: (id: string, paused: boolean) => void;
}) {
  if (toasts.length === 0) return null;

  return (
    <ToastViewport>
      {toasts.map((toast) => (
        <ToastItem
          key={toast.id}
          toast={toast}
          onDismiss={onDismiss}
          onPauseChange={onPauseChange}
        />
      ))}
    </ToastViewport>
  );
}

function ToastViewport({ children }: { children: ReactNode }) {
  return (
    <div
      className="pointer-events-none fixed bottom-0 right-0 z-[100] flex w-full flex-col gap-2.5 p-4 sm:max-w-[420px] sm:p-5"
      role="region"
      aria-label="Notifications"
      aria-live="polite"
    >
      {children}
    </div>
  );
}

function ToastItem({
  toast,
  onDismiss,
  onPauseChange,
}: {
  toast: ToastView;
  onDismiss: (id: string) => void;
  onPauseChange: (id: string, paused: boolean) => void;
}) {
  const visual = toastVisuals[toast.type];
  const { Icon } = visual;
  const duration = toast.duration ?? 5000;

  const progressStyle: CSSProperties = {
    animationDuration: `${duration}ms`,
    animationPlayState: toast.paused ? "paused" : "running",
  };

  return (
    <div
      className={cn(
        "group/toast pointer-events-auto relative overflow-hidden rounded-xl",
        "border border-neutral-200/90 bg-white/95 shadow-lg shadow-neutral-900/8 ring-1 ring-black/[0.03]",
        "backdrop-blur-md dark:border-neutral-800/90 dark:bg-neutral-900/95 dark:shadow-black/40 dark:ring-white/[0.04]",
        toast.exiting ? "animate-toast-exit" : "animate-toast-enter",
      )}
      role="alert"
      tabIndex={0}
      onMouseEnter={() => onPauseChange(toast.id, true)}
      onMouseLeave={() => onPauseChange(toast.id, false)}
      onFocus={() => onPauseChange(toast.id, true)}
      onBlur={() => onPauseChange(toast.id, false)}
    >
      <ToastAccentBar className={visual.accent} />

      <div className="flex items-start gap-3 py-3.5 pl-4 pr-3">
        <div
          className={cn(
            "mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ring-1 ring-inset",
            visual.iconWrap,
          )}
        >
          <Icon className={cn("h-4 w-4", visual.icon)} aria-hidden />
        </div>

        <div className="min-w-0 flex-1 pt-0.5">
          <p className="text-[13px] font-semibold leading-snug tracking-[-0.01em] text-neutral-900 dark:text-neutral-50">
            {toast.title}
          </p>
          {toast.description && (
            <p className="mt-1 text-[12px] leading-relaxed text-neutral-600 dark:text-neutral-400">
              {toast.description}
            </p>
          )}
        </div>

        <button
          type="button"
          onClick={() => onDismiss(toast.id)}
          className={cn(
            "shrink-0 rounded-lg p-1.5 text-neutral-400 transition-colors",
            "hover:bg-neutral-100 hover:text-neutral-700",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/30",
            "dark:hover:bg-neutral-800 dark:hover:text-neutral-200",
          )}
          aria-label="Dismiss notification"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>

      {duration > 0 && !toast.exiting && (
        <div
          className="absolute inset-x-0 bottom-0 h-0.5 origin-left bg-neutral-200/80 dark:bg-neutral-700/80"
          aria-hidden
        >
          <ToastProgressBar className={visual.progress} style={progressStyle} />
        </div>
      )}
    </div>
  );
}

function ToastAccentBar({ className }: { className: string }) {
  return <div className={cn("absolute inset-y-0 left-0 w-1", className)} aria-hidden />;
}

function ToastProgressBar({
  className,
  style,
}: {
  className: string;
  style: CSSProperties;
}) {
  return (
    <div
      className={cn(
        "h-full origin-left animate-toast-progress motion-reduce:animate-none",
        className,
      )}
      style={style}
    />
  );
}

export function toast(_options: Omit<Toast, "id">) {
  console.warn("toast() called outside ToastProvider context");
}
