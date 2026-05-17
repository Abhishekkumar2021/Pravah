import { ArrowLeft, Lock } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { ApiError, confirmPasswordReset } from "@/lib/api";

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (!done) return;
    const timeoutId = window.setTimeout(() => navigate("/login"), 2000);
    return () => window.clearTimeout(timeoutId);
  }, [done, navigate]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError("Passwords do not match");
      return;
    }
    if (!token) {
      setError("Missing reset token. Use the link from your email.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await confirmPasswordReset(token, password);
      setDone(true);
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : "Could not reset password");
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

        <h1 className="page-title">Choose a new password</h1>
        <p className="page-desc mt-2">Enter a new password for your Pravah account.</p>

        {done ? (
          <div className="mt-8 rounded-lg border border-emerald-200 bg-emerald-50 p-4 dark:border-emerald-900 dark:bg-emerald-950/50">
            <p className="text-sm text-emerald-800 dark:text-emerald-200">
              Password updated. Redirecting to sign in…
            </p>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="mt-8 space-y-5">
            <div>
              <Label htmlFor="password" className="mb-1.5 block">
                New password
              </Label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <Input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="py-2.5 pl-10"
                  minLength={8}
                  required
                />
              </div>
            </div>
            <div>
              <Label htmlFor="confirm" className="mb-1.5 block">
                Confirm password
              </Label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <Input
                  id="confirm"
                  type="password"
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  className="py-2.5 pl-10"
                  minLength={8}
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
              className="h-11 w-full"
              disabled={submitting || !password || !confirm}
            >
              {submitting ? "Saving…" : "Update password"}
            </Button>
          </form>
        )}
      </div>
    </div>
  );
}
