import { AlertCircle, ArrowRight, Check, Eye, EyeOff, Lock, Mail, User } from "lucide-react";
import { useCallback, useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthLayout } from "@/components/auth/AuthLayout";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { ApiError, register } from "@/lib/api";

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

export function SignupPage() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [sent, setSent] = useState(false);
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  const { strength, checks } = usePasswordStrength(password);

  const validateField = useCallback(
    (field: string, value: string): string | null => {
      if (field === "name" && !value.trim()) return "Name is required";
      if (field === "email") {
        if (!value.trim()) return "Email is required";
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return "Invalid email format";
      }
      if (field === "password") {
        if (!value) return "Password is required";
        if (value.length < 8) return "At least 8 characters required";
        if (!/[A-Z]/.test(value)) return "Include an uppercase letter";
        if (!/[a-z]/.test(value)) return "Include a lowercase letter";
        if (!/\d/.test(value)) return "Include a number";
      }
      return null;
    },
    [],
  );

  const handleBlur = useCallback(
    (field: string, value: string) => {
      setTouched((prev) => ({ ...prev, [field]: true }));
      const errorMsg = validateField(field, value);
      setFieldErrors((prev) => ({ ...prev, [field]: errorMsg ?? "" }));
    },
    [validateField],
  );

  const isFormValid = useMemo(() => {
    return (
      name.trim() &&
      email.trim() &&
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) &&
      password.length >= 8 &&
      /[A-Z]/.test(password) &&
      /[a-z]/.test(password) &&
      /\d/.test(password)
    );
  }, [name, email, password]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!isFormValid) return;
    setSubmitting(true);
    setError(null);
    try {
      await register(email.trim(), password, name.trim());
      setSent(true);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Could not create account. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="Create account"
      description="Register for Pravah — we'll email you a verification link before you can sign in."
      heroTitle="Start orchestrating"
      heroDescription="Create an account to build workflows, run pipelines, and monitor execution from one control plane."
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
            We sent a verification link to <strong className="font-medium">{email}</strong>.
          </p>
          <p className="mt-1 text-[13px] text-emerald-700/70 dark:text-emerald-300/70">
            The link expires in 24 hours.
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
            className="mt-6 h-11 w-full gap-2 rounded-lg text-[13px] font-medium"
            onClick={() => navigate("/login")}
          >
            Go to sign in
            <ArrowRight className="h-4 w-4" aria-hidden />
          </Button>
        </section>
      ) : (
        <form onSubmit={onSubmit} className="space-y-5" noValidate>
          <fieldset className="space-y-5" disabled={submitting}>
            <legend className="sr-only">Account details</legend>

            <div>
              <Label htmlFor="name" className="mb-1.5 block">
                Full name
              </Label>
              <div className="relative">
                <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <Input
                  id="name"
                  name="name"
                  autoComplete="name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  onBlur={(e) => handleBlur("name", e.target.value)}
                  placeholder="Your name"
                  className="py-2.5 pl-10"
                  invalid={touched.name && !!fieldErrors.name}
                  aria-describedby={fieldErrors.name ? "name-error" : undefined}
                />
              </div>
              {touched.name && fieldErrors.name && (
                <p id="name-error" className="mt-1.5 flex items-center gap-1 text-xs text-rose-600 dark:text-rose-400">
                  <AlertCircle className="h-3.5 w-3.5" aria-hidden />
                  {fieldErrors.name}
                </p>
              )}
            </div>

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
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onBlur={(e) => handleBlur("email", e.target.value)}
                  placeholder="you@company.com"
                  className="py-2.5 pl-10"
                  invalid={touched.email && !!fieldErrors.email}
                  aria-describedby={fieldErrors.email ? "email-error" : undefined}
                />
              </div>
              {touched.email && fieldErrors.email && (
                <p id="email-error" className="mt-1.5 flex items-center gap-1 text-xs text-rose-600 dark:text-rose-400">
                  <AlertCircle className="h-3.5 w-3.5" aria-hidden />
                  {fieldErrors.email}
                </p>
              )}
            </div>

            <div>
              <Label htmlFor="password" className="mb-1.5 block">
                Password
              </Label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <Input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onBlur={(e) => handleBlur("password", e.target.value)}
                  placeholder="Create a strong password"
                  className="py-2.5 pl-10 pr-10"
                  invalid={touched.password && !!fieldErrors.password}
                  aria-describedby="password-requirements"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 transition-colors hover:text-neutral-600 dark:hover:text-neutral-300"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" aria-hidden />
                  ) : (
                    <Eye className="h-4 w-4" aria-hidden />
                  )}
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
                  <ul id="password-requirements" className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
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
                          aria-hidden
                        />
                        {check.label}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </fieldset>

          {error && (
            <div
              className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2.5 text-[13px] text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300"
              role="alert"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{error}</span>
            </div>
          )}

          <Button
            type="submit"
            className="h-11 w-full rounded-lg text-[13px] font-medium transition-all duration-200"
            disabled={submitting || !isFormValid}
          >
            {submitting ? (
              <>
                <span className="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                Creating account…
              </>
            ) : (
              "Create account"
            )}
          </Button>

          <p className="text-center text-[13px] text-neutral-600 dark:text-neutral-400">
            Already have an account?{" "}
            <Link
              to="/login"
              className="font-medium text-blue-600 transition-colors hover:text-blue-700 hover:underline dark:text-blue-400 dark:hover:text-blue-300"
            >
              Sign in
            </Link>
          </p>
        </form>
      )}
    </AuthLayout>
  );
}
