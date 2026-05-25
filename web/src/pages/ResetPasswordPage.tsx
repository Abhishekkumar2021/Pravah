import { AlertCircle, ArrowRight, Check, CheckCircle2, Eye, EyeOff, Lock } from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AuthLayout } from "@/components/auth/AuthLayout";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { ApiError, confirmPasswordReset } from "@/lib/api";

type PasswordStrength = "weak" | "medium" | "strong";

function usePasswordStrength(password: string): {
  strength: PasswordStrength;
  checks: { label: string; met: boolean }[];
} {
  return useMemo(() => {
    const checks = [
      { label: "8+ characters", met: password.length >= 8 },
      { label: "Uppercase letter", met: /[A-Z]/.test(password) },
      { label: "Lowercase letter", met: /[a-z]/.test(password) },
      { label: "Number", met: /\d/.test(password) },
    ];
    const metCount = checks.filter((c) => c.met).length;
    const strength: PasswordStrength =
      metCount <= 2 ? "weak" : metCount === 3 ? "medium" : "strong";
    return { strength, checks };
  }, [password]);
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = (searchParams.get("token") ?? "").trim();

  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const { strength, checks } = usePasswordStrength(password);

  const passwordsMatch = password && confirm && password === confirm;
  const isValid = useMemo(() => {
    return (
      password.length >= 8 &&
      /[A-Z]/.test(password) &&
      /[a-z]/.test(password) &&
      /\d/.test(password) &&
      password === confirm &&
      token
    );
  }, [password, confirm, token]);

  useEffect(() => {
    if (!done) return;
    const timeoutId = window.setTimeout(() => navigate("/login"), 3000);
    return () => window.clearTimeout(timeoutId);
  }, [done, navigate]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!isValid) return;
    if (!token) {
      setError("Missing reset token. Please use the link from your email.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await confirmPasswordReset(token, password);
      setDone(true);
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : "Could not reset password. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <AuthLayout
        title="Invalid link"
        description="The password reset link is missing or malformed."
        heroTitle="Reset your password"
        heroDescription="Create a new secure password for your Pravah account."
        backTo={{ label: "Back to sign in", href: "/login" }}
      >
        <section className="rounded-xl border border-rose-200/80 bg-rose-50/90 px-6 py-8 text-center shadow-sm dark:border-rose-900/60 dark:bg-rose-950/40">
          <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rose-600/10 ring-1 ring-rose-600/20">
            <AlertCircle className="h-8 w-8 text-rose-600 dark:text-rose-400" />
          </span>
          <h2 className="mt-6 text-lg font-semibold tracking-tight text-rose-900 dark:text-rose-100">
            Missing reset token
          </h2>
          <p className="mt-2 text-[13px] leading-relaxed text-rose-800 dark:text-rose-200/90">
            Please use the link from your email to reset your password.
          </p>
          <Button
            type="button"
            className="mt-6 h-11 w-full gap-2 rounded-xl text-[13px] font-medium"
            onClick={() => navigate("/forgot-password")}
          >
            Request a new link
            <ArrowRight className="h-4 w-4" aria-hidden />
          </Button>
        </section>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Create new password"
      description="Choose a strong password for your Pravah account."
      heroTitle="Reset your password"
      heroDescription="Create a new secure password. We recommend using a mix of letters, numbers, and symbols."
      backTo={{ label: "Back to sign in", href: "/login" }}
    >
      {done ? (
        <section className="rounded-xl border border-emerald-200/80 bg-emerald-50/90 px-6 py-8 text-center shadow-sm dark:border-emerald-900/60 dark:bg-emerald-950/40">
          <span className="relative mx-auto flex h-14 w-14 items-center justify-center">
            <span
              className="absolute inset-0 animate-ping rounded-full bg-emerald-500/20"
              style={{ animationDuration: "1.5s", animationIterationCount: "2" }}
              aria-hidden
            />
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-600/15 ring-1 ring-emerald-600/25">
              <CheckCircle2 className="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
            </span>
          </span>
          <h2 className="mt-6 text-lg font-semibold tracking-tight text-emerald-900 dark:text-emerald-100">
            Password updated
          </h2>
          <p className="mt-2 text-[13px] leading-relaxed text-emerald-800/90 dark:text-emerald-200/90">
            Your password has been changed. Redirecting to sign in…
          </p>
          <div className="mt-3 flex items-center justify-center gap-1.5">
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-emerald-500 [animation-delay:0ms]" />
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-emerald-500 [animation-delay:150ms]" />
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-emerald-500 [animation-delay:300ms]" />
          </div>
          <Button
            type="button"
            className="mt-6 h-11 w-full gap-2 rounded-xl text-[13px] font-medium"
            onClick={() => navigate("/login")}
          >
            Continue to sign in
            <ArrowRight className="h-4 w-4" aria-hidden />
          </Button>
        </section>
      ) : (
        <form onSubmit={onSubmit} className="space-y-5" noValidate>
          <fieldset className="space-y-5" disabled={submitting}>
            <div>
              <Label htmlFor="password" className="mb-1.5 block">
                New password
              </Label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Create a strong password"
                  className="h-11 py-2.5 pl-10 pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 transition-colors hover:text-neutral-600 dark:hover:text-neutral-300"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>

              {password && (
                <div className="mt-3 space-y-2">
                  <div className="flex gap-1">
                    {[0, 1, 2, 3].map((i) => (
                      <div
                        key={i}
                        className={`h-1 flex-1 rounded-full transition-colors duration-200 ${
                          i < checks.filter((c) => c.met).length
                            ? strength === "weak"
                              ? "bg-rose-500"
                              : strength === "medium"
                                ? "bg-amber-500"
                                : "bg-emerald-500"
                            : "bg-neutral-200 dark:bg-neutral-700"
                        }`}
                      />
                    ))}
                  </div>
                  <ul className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    {checks.map((check) => (
                      <li
                        key={check.label}
                        className={`flex items-center gap-1.5 transition-colors duration-150 ${
                          check.met
                            ? "text-emerald-600 dark:text-emerald-400"
                            : "text-neutral-400 dark:text-neutral-500"
                        }`}
                      >
                        <Check
                          className={`h-3 w-3 transition-transform duration-150 ${check.met ? "scale-100" : "scale-75 opacity-50"}`}
                        />
                        {check.label}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            <div>
              <Label htmlFor="confirm" className="mb-1.5 block">
                Confirm password
              </Label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <Input
                  id="confirm"
                  type={showConfirm ? "text" : "password"}
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  placeholder="Confirm your password"
                  className="h-11 py-2.5 pl-10 pr-10"
                  invalid={confirm.length > 0 && !passwordsMatch}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirm(!showConfirm)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 transition-colors hover:text-neutral-600 dark:hover:text-neutral-300"
                  aria-label={showConfirm ? "Hide password" : "Show password"}
                >
                  {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {confirm && !passwordsMatch && (
                <p className="mt-1.5 flex items-center gap-1 text-xs text-rose-600 dark:text-rose-400">
                  <AlertCircle className="h-3.5 w-3.5" />
                  Passwords don't match
                </p>
              )}
              {passwordsMatch && (
                <p className="mt-1.5 flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  Passwords match
                </p>
              )}
            </div>
          </fieldset>

          {error && (
            <div
              className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50/80 px-3 py-2.5 text-[13px] text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300"
              role="alert"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <Button
            type="submit"
            className="h-11 w-full rounded-xl text-[13px] font-semibold transition-all duration-200"
            disabled={submitting || !isValid}
          >
            {submitting ? (
              <>
                <span className="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                Updating password…
              </>
            ) : (
              "Update password"
            )}
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
