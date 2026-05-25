import { AlertCircle, Eye, EyeOff, Github, Lock, Mail } from "lucide-react";
import { useId, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { GoogleIcon } from "@/components/icons/GoogleIcon";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/Tooltip";
import { ApiError, login } from "@/lib/api";

const HERO_PATTERN =
  "data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20width=%2240%22%20height=%2240%22%20viewBox=%220%200%2040%2040%22%3E%3Cg%20fill=%22none%22%20fill-rule=%22evenodd%22%3E%3Cg%20fill=%22%23ffffff%22%20fill-opacity=%220.04%22%3E%3Cpath%20d=%22M0%2038.59l2.83-2.83%201.41%201.41L1.41%2040H0v-1.41zM0%201.4l2.83%202.83%201.41-1.41L1.41%200H0v1.41zM38.59%2040l-2.83-2.83%201.41-1.41L40%2038.59V40h-1.41zM40%201.41l-2.83%202.83-1.41-1.41L38.59%200H40v1.41zM20%2018.6l2.83-2.83%201.41%201.41L21.41%2020l2.83%202.83-1.41%201.41L20%2021.41l-2.83%202.83-1.41-1.41L18.59%2020l-2.83-2.83%201.41-1.41L20%2018.59z%22/%3E%3C/g%3E%3C/g%3E%3C/svg%3E";

function safeRedirectPath(raw: string | null): string {
  if (!raw) return "/app/dashboard";
  try {
    const path = decodeURIComponent(raw);
    if (path.startsWith("/app")) return path;
  } catch {
    /* ignore malformed redirect */
  }
  return "/app/dashboard";
}

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const verified = searchParams.get("verified") === "1";
  const [email, setEmail] = useState("dev@localhost.pravah");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showVerifyHint, setShowVerifyHint] = useState(false);
  const rememberId = useId();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    setShowVerifyHint(false);
    try {
      await login(email.trim(), password);
      navigate(safeRedirectPath(searchParams.get("redirect")), { replace: true });
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 500) {
        setError(
          "Auth service unavailable. Run: make local-services-stop && make local-services (in backend/), then make local-seed.",
        );
      } else if (err instanceof ApiError && err.errorCode === "EMAIL_NOT_VERIFIED") {
        setError(
          "Email not verified. Check your inbox for the verification link, or visit the sign up page to resend it.",
        );
        setShowVerifyHint(true);
      } else if (err instanceof ApiError && err.status === 401) {
        setError("Invalid email or password.");
      } else {
        setError(err instanceof ApiError ? err.message : "Sign in failed");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="grid min-h-dvh lg:grid-cols-2">
      {/* Hero panel */}
      <aside className="relative hidden overflow-hidden bg-neutral-950 lg:flex lg:flex-col lg:justify-center">
        <span
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-blue-600/35 via-neutral-950 to-neutral-950"
          aria-hidden
        />
        <span
          className="pointer-events-none absolute inset-0 opacity-80"
          style={{ backgroundImage: `url("${HERO_PATTERN}")` }}
          aria-hidden
        />
        <header className="relative z-10 mx-auto max-w-md px-8 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-blue-500 to-blue-600 shadow-xl shadow-blue-500/30">
            <svg className="h-7 w-7 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
            </svg>
          </div>
          <p className="mt-6 text-sm font-medium uppercase tracking-[0.2em] text-blue-300/90">Pravah</p>
          <h2 className="mt-4 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
            Pipelines you can trust
          </h2>
          <p className="mt-4 text-[14px] leading-relaxed text-neutral-400">
            Orchestrate data workflows with confidence. Real-time monitoring, intelligent retry, and seamless execution.
          </p>
          <div className="mt-8 flex items-center justify-center gap-6 text-[12px] text-neutral-500">
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              99.9% uptime
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-500" />
              Live monitoring
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
              Auto-retry
            </span>
          </div>
        </header>
      </aside>

      {/* Form panel */}
      <main className="flex min-h-dvh flex-col justify-center border-l border-neutral-200 bg-white px-6 py-12 dark:border-neutral-800 dark:bg-neutral-950">
        <article className="mx-auto w-full max-w-md">
          {/* Mobile header */}
          <header className="mb-10 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 shadow-lg shadow-blue-500/25">
              <svg className="h-5 w-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
              </svg>
            </div>
            <h1 className="page-title mt-4">Welcome back</h1>
            <p className="page-desc mt-1">Sign in to continue to Pravah</p>
          </header>

          {/* Desktop header */}
          <header className="hidden lg:block">
            <h1 className="page-title">Welcome back</h1>
            <p className="page-desc mt-2">
              Sign in with your Pravah account to continue.
            </p>
          </header>

          {verified && (
            <div
              className="mt-6 flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50/80 px-4 py-3 text-[13px] text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-200"
              role="status"
            >
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-900/50">
                <svg className="h-4 w-4 text-emerald-600 dark:text-emerald-400" viewBox="0 0 20 20" fill="currentColor">
                  <path
                    fillRule="evenodd"
                    d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z"
                    clipRule="evenodd"
                  />
                </svg>
              </span>
              <span>
                <strong className="font-semibold">Email verified!</strong> You can sign in now.
              </span>
            </div>
          )}

          <form onSubmit={onSubmit} className="mt-8 space-y-5" noValidate>
            <fieldset className="space-y-5" disabled={submitting}>
              <div>
                <Label htmlFor="email" className="mb-1.5 block">
                  Email
                </Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    autoComplete="username"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@company.com"
                    className="h-11 py-2.5 pl-10"
                  />
                </div>
              </div>

              <div>
                <div className="mb-1.5 flex items-center justify-between">
                  <Label htmlFor="password">Password</Label>
                  <Link
                    to="/forgot-password"
                    className="text-[12px] font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
                  >
                    Forgot password?
                  </Link>
                </div>
                <div className="relative">
                  <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                  <Input
                    id="password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
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
              </div>

              <div className="flex items-center gap-2">
                <Checkbox id={rememberId} name="remember" />
                <Label
                  htmlFor={rememberId}
                  className="cursor-pointer text-[13px] font-normal text-neutral-600 dark:text-neutral-400"
                >
                  Remember me for 30 days
                </Label>
              </div>
            </fieldset>

            {error && (
              <div
                className="flex items-start gap-3 rounded-xl border border-rose-200 bg-rose-50/80 px-4 py-3 text-[13px] text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300"
                role="alert"
              >
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                <div>
                  <p>{error}</p>
                  {showVerifyHint && (
                    <p className="mt-2">
                      <Link
                        to="/verify-email"
                        className="font-semibold underline transition-colors hover:text-rose-800 dark:hover:text-rose-200"
                      >
                        Resend verification email
                      </Link>
                    </p>
                  )}
                </div>
              </div>
            )}

            <Button
              type="submit"
              className="h-11 w-full rounded-xl text-[13px] font-semibold transition-all duration-200"
              disabled={submitting || !email.trim() || !password}
            >
              {submitting ? (
                <>
                  <span className="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                  Signing in…
                </>
              ) : (
                "Sign in"
              )}
            </Button>
          </form>

          <div className="relative my-8">
            <div className="absolute inset-0 flex items-center" aria-hidden>
              <div className="w-full border-t border-neutral-200 dark:border-neutral-800" />
            </div>
            <div className="relative flex justify-center text-[11px] font-medium uppercase tracking-wider text-neutral-400">
              <span className="bg-white px-3 dark:bg-neutral-950">Or continue with</span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-block w-full">
                  <Button
                    type="button"
                    variant="secondary"
                    disabled
                    className="h-11 w-full rounded-xl text-neutral-500 transition-all hover:bg-neutral-100 dark:hover:bg-neutral-900"
                  >
                    <Github className="h-4 w-4" aria-hidden />
                    GitHub
                  </Button>
                </span>
              </TooltipTrigger>
              <TooltipContent>OAuth login coming soon in a future release</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-block w-full">
                  <Button
                    type="button"
                    variant="secondary"
                    disabled
                    className="h-11 w-full rounded-xl text-neutral-500 transition-all hover:bg-neutral-100 dark:hover:bg-neutral-900"
                  >
                    <GoogleIcon />
                    Google
                  </Button>
                </span>
              </TooltipTrigger>
              <TooltipContent>OAuth login coming soon in a future release</TooltipContent>
            </Tooltip>
          </div>

          <p className="mt-8 text-center text-[13px] text-neutral-600 dark:text-neutral-400">
            Don't have an account?{" "}
            <Link
              to="/signup"
              className="font-semibold text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
            >
              Sign up for free
            </Link>
          </p>

          <p className="mt-6 text-center text-[11px] text-neutral-400">
            By signing in, you agree to our{" "}
            <a href="#" className="underline hover:text-neutral-600 dark:hover:text-neutral-300">
              Terms of Service
            </a>{" "}
            and{" "}
            <a href="#" className="underline hover:text-neutral-600 dark:hover:text-neutral-300">
              Privacy Policy
            </a>
          </p>
        </article>
      </main>
    </section>
  );
}
