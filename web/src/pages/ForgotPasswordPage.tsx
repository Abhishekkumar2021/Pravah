import { ArrowLeft, Mail } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
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
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email.trim());
      setSent(true);
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : "Request failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-white px-4 dark:bg-neutral-950">
      <div className="w-full max-w-md">
        <Link
          to="/login"
          className="mb-6 inline-flex items-center gap-1 text-sm text-neutral-500 hover:text-neutral-700 dark:hover:text-neutral-300"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to sign in
        </Link>

        <h1 className="page-title">Reset password</h1>
        <p className="page-desc mt-2">
          Enter your email address and we'll send you a link to reset your password.
        </p>

        {sent ? (
          <div className="mt-8 rounded-lg border border-emerald-200 bg-emerald-50 p-4 dark:border-emerald-900 dark:bg-emerald-950/50">
            <p className="text-sm text-emerald-800 dark:text-emerald-200">
              If an account exists for <strong>{email}</strong>, you'll receive a password reset
              email shortly.
            </p>
            <p className="mt-3 text-xs text-emerald-600 dark:text-emerald-400">
              Check Mailhog at{" "}
              <a href="http://localhost:8025" className="underline" target="_blank" rel="noreferrer">
                localhost:8025
              </a>{" "}
              when running local docker-compose.
            </p>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="mt-8 space-y-5">
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
                  className="py-2.5 pl-10"
                  required
                />
              </div>
            </div>
            {error && (
              <p className="text-[13px] text-rose-600 dark:text-rose-400" role="alert">
                {error}
              </p>
            )}
            <Button
              type="submit"
              className="h-11 w-full rounded-lg text-[13px] font-medium"
              disabled={submitting || !email.trim()}
            >
              {submitting ? "Sending…" : "Send reset link"}
            </Button>
          </form>
        )}
      </div>
    </div>
  );
}
