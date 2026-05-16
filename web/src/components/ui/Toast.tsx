import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
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

type ToastContextValue = {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, "id">) => void;
  removeToast: (id: string) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((toast: Omit<Toast, "id">) => {
    const id = crypto.randomUUID();
    const duration = toast.duration ?? 5000;

    setToasts((prev) => [...prev, { ...toast, id }]);

    if (duration > 0) {
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, duration);
    }
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, addToast, removeToast }}>
      {children}
      <ToastContainer toasts={toasts} onDismiss={removeToast} />
    </ToastContext.Provider>
  );
}

const toastIcons: Record<ToastType, ReactNode> = {
  success: <CheckCircle2 className="h-5 w-5 text-emerald-500" />,
  error: <XCircle className="h-5 w-5 text-rose-500" />,
  warning: <AlertCircle className="h-5 w-5 text-amber-500" />,
  info: <Info className="h-5 w-5 text-blue-500" />,
};

const toastStyles: Record<ToastType, string> = {
  success: "border-emerald-200 bg-emerald-50 dark:border-emerald-900/50 dark:bg-emerald-950/50",
  error: "border-rose-200 bg-rose-50 dark:border-rose-900/50 dark:bg-rose-950/50",
  warning: "border-amber-200 bg-amber-50 dark:border-amber-900/50 dark:bg-amber-950/50",
  info: "border-blue-200 bg-blue-50 dark:border-blue-900/50 dark:bg-blue-950/50",
};

function ToastContainer({
  toasts,
  onDismiss,
}: {
  toasts: Toast[];
  onDismiss: (id: string) => void;
}) {
  if (toasts.length === 0) return null;

  return (
    <div
      className="pointer-events-none fixed bottom-0 right-0 z-50 flex max-h-screen w-full flex-col gap-2 p-4 sm:max-w-md"
      role="region"
      aria-label="Notifications"
    >
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={cn(
            "pointer-events-auto flex w-full items-start gap-3 rounded-xl border p-4 shadow-lg backdrop-blur-sm",
            "animate-in slide-in-from-bottom-5 fade-in duration-200",
            toastStyles[toast.type],
          )}
          role="alert"
        >
          <div className="shrink-0 pt-0.5">{toastIcons[toast.type]}</div>
          <div className="min-w-0 flex-1">
            <p className="text-[13px] font-semibold text-neutral-900 dark:text-neutral-100">
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
            className="shrink-0 rounded-md p-1 text-neutral-400 transition-colors hover:bg-neutral-200/50 hover:text-neutral-600 dark:hover:bg-neutral-700/50 dark:hover:text-neutral-300"
            aria-label="Dismiss notification"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  );
}

export function toast(_options: Omit<Toast, "id">) {
  console.warn("toast() called outside ToastProvider context");
}
