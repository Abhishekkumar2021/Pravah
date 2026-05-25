import { AlertCircle, ArrowRight, Mail, RefreshCw } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { AuthLayout } from "@/components/auth/AuthLayout";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { ApiError, requestPasswordReset } from "@/lib/api";

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!email.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email.trim());
      setSent(true);
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : "Request failed. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="Reset your password"
      description="Enter your email and we'll send you a link to create a new password."
      heroTitle="Forgot your password?"
      heroDescription="No worries — we'll help you get back into your account securely."
      backTo={{ label: "Back to sign in", href: "/login" }}
    >
      {sent ? (
        <section className="rounded-xl border border-emerald-200/80 bg-emerald-50/90 px-6 py-8 text-center shadow-sm dark:border-emerald-900/60 dark:bg-emerald-950/40">
          <span className="relative mx-auto flex h-14 w-14 items-center justify-center">
            <span
              className="absolute inset-0 animate-ping rounded-full bg-emerald-500/20"
              style={{ animationDuration: "1.5s", animationIterationCount: "2" }}
              aria-hidden
            />
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-600/15 ring-1 ring-emerald-600/25">
              <Mail className="h-7 w-7 text-emerald-600 dark:text-emerald-400" />
            </span>
          </span>
          <h2 className="mt-6 text-lg font-semibold tracking-tight text-emerald-900 dark:text-emerald-100">
            Check your inbox
          </h2>
          <p className="mt-2 text-[13px] leading-relaxed text-emerald-800/90 dark:text-emerald-200/90">
            If an account exists for <strong className="font-medium">{email}</strong>, you'll receive a password reset link shortly.
          </p>
          <p className="mt-1 text-[13px] text-emerald-700/70 dark:text-emerald-300/70">
            The link expires in 1 hour.
          </p>
          <p className="mt-4 text-xs text-emerald-700/80 dark:text-emerald-300/80">
            Local dev: open{" "}
            <a
              href="http://localhost:8025"
              className="font-medium underline underline-offset-2 transition-colors hover:text-emerald-900 dark:hover:text-emerald-100"
              target="_blank"
              rel="noreferrer"
            >
              Mailhog
            </a>
          </p>
          <Button
            type="button"
            className="mt-6 h-11 w-full gap-2 rounded-xl text-[13px] font-medium"
            onClick={() => {
              setSent(false);
              setEmail("");
            }}
          >
            Send to a different email
          </Button>
          <Link
            to="/login"
            className="mt-4 inline-flex items-center gap-1.5 text-[13px] font-medium text-emerald-700 transition-colors hover:text-emerald-900 dark:text-emerald-300 dark:hover:text-emerald-100"
          >
            Return to sign in
            <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </section>
      ) : (
        <form onSubmit={onSubmit} className="space-y-5" noValidate>
          <fieldset className="space-y-5" disabled={submitting}>
            <div>
              <Label htmlFor="email" className="mb-1.5 block">
                Email address
              </Label>
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <Input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@company.com"
                  className="h-11 py-2.5 pl-10"
                  required
                />
              </div>
              <p className="mt-2 text-xs text-neutral-500 dark:text-neutral-400">
                We'll send a secure link to reset your password.
              </p>
            </div>
          </fieldset>

          {error && (
            <div
              className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50/80 px-3 py-2.5 text-[13px] text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300"
              role="alert"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{error}</span>
            </div>
          )}

          <Button
            type="submit"
            className="h-11 w-full rounded-xl text-[13px] font-semibold transition-all duration-200"
            disabled={submitting || !email.trim()}
          >
            {submitting ? (
              <>
                <RefreshCw className="mr-2 h-4 w-4 animate-spin" aria-hidden />
                Sending…
              </>
            ) : (
              "Send reset link"
            )}
          </Button>

          <p className="text-center text-[13px] text-neutral-600 dark:text-neutral-400">
            Remember your password?{" "}
            <Link
              to="/login"
              className="font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
            >
              Sign in
            </Link>
          </p>
        </form>
      )}
    </AuthLayout>
  );
}
