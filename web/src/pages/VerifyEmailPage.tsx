import { AlertCircle, ArrowRight, CheckCircle2, Loader2, Mail, MailCheck, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { AuthLayout } from "@/components/auth/AuthLayout";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { ApiError, resendVerificationEmail, verifyEmail } from "@/lib/api";

function StatusCard({
  children,
  variant = "neutral",
}: {
  children: ReactNode;
  variant?: "neutral" | "success" | "error";
}) {
  const baseStyles = "rounded-xl border px-6 py-8 text-center shadow-sm transition-all duration-300";
  const variantStyles = {
    neutral:
      "border-neutral-200/80 bg-neutral-50/80 dark:border-neutral-800 dark:bg-neutral-900/40",
    success:
      "border-emerald-200/80 bg-emerald-50/90 dark:border-emerald-900/60 dark:bg-emerald-950/40",
    error: "border-rose-200/80 bg-rose-50/90 dark:border-rose-900/60 dark:bg-rose-950/40",
  };
  return (
    <section className={`${baseStyles} ${variantStyles[variant]}`}>
      {children}
    </section>
  );
}

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = (searchParams.get("token") ?? "").trim();

  const [status, setStatus] = useState<"loading" | "success" | "error">(
    token ? "loading" : "error",
  );
  const [error, setError] = useState<string | null>(
    token ? null : "Missing verification token. Open the link from your email, or request a new one below.",
  );
  const [resendEmail, setResendEmail] = useState("");
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const verifyStarted = useRef(false);

  useEffect(() => {
    if (!token) return;
    if (verifyStarted.current) return;
    verifyStarted.current = true;

    let cancelled = false;
    void (async () => {
      try {
        await verifyEmail(token);
        if (!cancelled) setStatus("success");
      } catch (err: unknown) {
        if (!cancelled) {
          setStatus("error");
          const message =
            err instanceof ApiError
              ? err.message.includes("Validation failed") ||
                err.message.toLowerCase().includes("invalid or expired")
                ? "This link is invalid or has expired. Request a new one below, or try signing in if you already verified."
                : err.message
              : "Verification failed";
          setError(message);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (status !== "success") return;
    const timeoutId = window.setTimeout(() => navigate("/login?verified=1"), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [status, navigate]);

  async function onResend(e: FormEvent) {
    e.preventDefault();
    if (!resendEmail.trim()) return;
    setResending(true);
    setResent(false);
    setError(null);
    try {
      await resendVerificationEmail(resendEmail.trim());
      setResent(true);
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : "Could not resend email");
    } finally {
      setResending(false);
    }
  }

  return (
    <AuthLayout
      title="Verify email"
      description="We're confirming your email address so you can sign in."
      heroTitle="One quick step"
      heroDescription="Verify your email to activate your account and start orchestrating pipelines with confidence."
    >
      {status === "loading" && (
        <StatusCard>
          <span className="relative mx-auto flex h-14 w-14 items-center justify-center">
            <span
              className="absolute inset-0 animate-ping rounded-full bg-blue-500/20"
              aria-hidden
            />
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-blue-600/10 ring-1 ring-blue-600/20">
              <Loader2 className="h-7 w-7 animate-spin text-blue-600 dark:text-blue-400" />
            </span>
          </span>
          <h2 className="mt-6 text-lg font-semibold tracking-tight text-neutral-900 dark:text-neutral-100">
            Verifying your email
          </h2>
          <p className="mx-auto mt-2 max-w-xs text-[13px] text-neutral-500 dark:text-neutral-400">
            Hang tight — we're activating your account.
          </p>
        </StatusCard>
      )}

      {status === "success" && (
        <StatusCard variant="success">
          <span className="relative mx-auto flex h-14 w-14 items-center justify-center">
            <span
              className="absolute inset-0 scale-0 animate-[scale-in_0.3s_ease-out_forwards] rounded-full bg-emerald-500/20"
              aria-hidden
            />
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-600/15 ring-1 ring-emerald-600/25">
              <CheckCircle2 className="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
            </span>
          </span>
          <h2 className="mt-6 text-lg font-semibold tracking-tight text-emerald-900 dark:text-emerald-100">
            You're all set
          </h2>
          <p className="mt-2 text-[13px] leading-relaxed text-emerald-800/90 dark:text-emerald-200/90">
            Your email is verified. Redirecting to sign in in a few seconds.
          </p>
          <div className="mt-3 flex items-center justify-center gap-1.5">
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-emerald-500 [animation-delay:0ms]" />
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-emerald-500 [animation-delay:150ms]" />
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-emerald-500 [animation-delay:300ms]" />
          </div>
          <Button
            type="button"
            className="mt-6 h-11 w-full gap-2 rounded-lg text-[13px] font-medium transition-all duration-200"
            onClick={() => navigate("/login?verified=1")}
          >
            Continue to sign in
            <ArrowRight className="h-4 w-4" aria-hidden />
          </Button>
        </StatusCard>
      )}

      {status === "error" && (
        <section className="space-y-6">
          <StatusCard variant="error">
            <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rose-600/10 ring-1 ring-rose-600/20">
              <AlertCircle className="h-8 w-8 text-rose-600 dark:text-rose-400" />
            </span>
            <h2 className="mt-6 text-lg font-semibold tracking-tight text-rose-900 dark:text-rose-100">
              Couldn't verify
            </h2>
            <p className="mt-2 text-[13px] leading-relaxed text-rose-800 dark:text-rose-200/90" role="alert">
              {error}
            </p>
            <p className="mt-4 text-[13px] text-neutral-600 dark:text-neutral-400">
              Already verified?{" "}
              <Link
                to="/login"
                className="font-medium text-blue-600 transition-colors hover:text-blue-700 hover:underline dark:text-blue-400 dark:hover:text-blue-300"
              >
                Try signing in
              </Link>
            </p>
          </StatusCard>

          <section className="rounded-xl border border-neutral-200/80 bg-white p-6 shadow-sm dark:border-neutral-800 dark:bg-neutral-900/30">
            <header className="flex items-start gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-600/10 ring-1 ring-blue-600/15">
                <MailCheck className="h-5 w-5 text-blue-600 dark:text-blue-400" />
              </span>
              <div>
                <h3 className="text-[15px] font-semibold text-neutral-900 dark:text-neutral-100">
                  Resend verification email
                </h3>
                <p className="mt-1 text-[13px] text-neutral-500 dark:text-neutral-400">
                  We'll send a fresh link if your account is still pending verification.
                </p>
              </div>
            </header>

            <form onSubmit={onResend} className="mt-5 space-y-4">
              <div>
                <Label htmlFor="resend-email" className="mb-1.5 block">
                  Email address
                </Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                  <Input
                    id="resend-email"
                    type="email"
                    autoComplete="email"
                    value={resendEmail}
                    onChange={(e) => setResendEmail(e.target.value)}
                    placeholder="you@company.com"
                    className="py-2.5 pl-10"
                    required
                  />
                </div>
              </div>

              {resent && (
                <div
                  className="flex items-start gap-2 rounded-lg border border-emerald-200/80 bg-emerald-50/80 px-3 py-2.5 text-[13px] text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-200"
                  role="status"
                >
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                  <span>
                    If an account is pending, check your inbox (local dev:{" "}
                    <a
                      href="http://localhost:8025"
                      className="font-medium underline transition-colors hover:text-emerald-900 dark:hover:text-emerald-100"
                      target="_blank"
                      rel="noreferrer"
                    >
                      Mailhog
                    </a>
                    ).
                  </span>
                </div>
              )}

              <Button
                type="submit"
                className="h-11 w-full rounded-lg text-[13px] font-medium transition-all duration-200"
                disabled={resending || !resendEmail.trim()}
              >
                {resending ? (
                  <>
                    <RefreshCw className="mr-2 h-4 w-4 animate-spin" aria-hidden />
                    Sending…
                  </>
                ) : (
                  "Send verification link"
                )}
              </Button>
            </form>
          </section>
        </section>
      )}
    </AuthLayout>
  );
}
