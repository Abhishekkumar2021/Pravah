import { Github, Lock, Mail } from "lucide-react";
import { useId, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { GoogleIcon } from "@/components/icons/GoogleIcon";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";

export function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const rememberId = useId();

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    navigate("/app/dashboard");
  }

  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="relative hidden overflow-hidden bg-neutral-950 lg:flex">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-blue-600/35 via-neutral-950 to-neutral-950" />
        <div className="absolute inset-0 bg-[url('data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20width=%2240%22%20height=%2240%22%20viewBox=%220%200%2040%2040%22%3E%3Cg%20fill=%22none%22%20fill-rule=%22evenodd%22%3E%3Cg%20fill=%22%23ffffff%22%20fill-opacity=%220.04%22%3E%3Cpath%20d=%22M0%2038.59l2.83-2.83%201.41%201.41L1.41%2040H0v-1.41zM0%201.4l2.83%202.83%201.41-1.41L1.41%200H0v1.41zM38.59%2040l-2.83-2.83%201.41-1.41L40%2038.59V40h-1.41zM40%201.41l-2.83%202.83-1.41-1.41L38.59%200H40v1.41zM20%2018.6l2.83-2.83%201.41%201.41L21.41%2020l2.83%202.83-1.41%201.41L20%2021.41l-2.83%202.83-1.41-1.41L18.59%2020l-2.83-2.83%201.41-1.41L20%2018.59z%22/%3E%3C/g%3E%3C/g%3E%3C/svg%3E')] opacity-80" />
        <div className="relative z-10 m-auto max-w-md px-8 text-center">
          <p className="text-sm font-medium uppercase tracking-[0.2em] text-blue-300/90">Pravah</p>
          <h2 className="mt-4 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
            Pipelines you can trust
          </h2>
          <p className="mt-4 text-[13px] leading-relaxed text-neutral-400">
            Manual runs, live status, and cancellation—wired to the same execution engine as the API.
            This shell tracks{" "}
            <a
              className="text-blue-300 underline-offset-4 hover:underline"
              href="https://github.com/Abhishekkumar2021/Pravah/blob/develop/docs/product/epics/EPIC-12-ui-ux.md"
            >
              EPIC-12
            </a>
            .
          </p>
        </div>
      </div>

      <div className="flex flex-col justify-center border-l border-neutral-200 bg-white px-6 py-12 dark:border-neutral-800 dark:bg-neutral-950">
        <div className="mx-auto w-full max-w-md">
          <div className="mb-10 lg:hidden">
            <p className="text-sm font-semibold text-blue-600 dark:text-blue-400">Pravah</p>
            <h1 className="page-title mt-1">Sign in</h1>
          </div>
          <div className="hidden lg:block">
            <h1 className="page-title">Sign in</h1>
            <p className="page-desc mt-2">
              Welcome back. Auth is not wired yet—this is the US-12.01 layout shell.
            </p>
          </div>

          <form onSubmit={onSubmit} className="mt-10 space-y-5">
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
                  className="py-2.5 pl-10"
                />
              </div>
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
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="py-2.5 pl-10"
                />
              </div>
            </div>
            <div className="flex items-center justify-between gap-4 text-[13px]">
              <div className="flex items-center gap-2">
                <Checkbox id={rememberId} name="remember" />
                <Label
                  htmlFor={rememberId}
                  className="cursor-pointer font-normal text-neutral-600 dark:text-neutral-400"
                >
                  Remember me
                </Label>
              </div>
              <span className="text-neutral-400">Forgot password</span>
            </div>
            <Button type="submit" className="h-11 w-full rounded-lg text-[13px] font-medium">
              Continue
            </Button>
          </form>

          <div className="relative my-10">
            <div className="absolute inset-0 flex items-center" aria-hidden>
              <div className="w-full border-t border-neutral-200 dark:border-neutral-800" />
            </div>
            <div className="relative flex justify-center text-[11px] font-medium uppercase tracking-wider text-neutral-500">
              <span className="bg-white px-3 dark:bg-neutral-950">Or continue with</span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Button type="button" variant="secondary" disabled className="h-10 w-full text-neutral-400">
              <Github className="h-4 w-4" aria-hidden />
              GitHub
            </Button>
            <Button type="button" variant="secondary" disabled className="h-10 w-full text-neutral-400">
              <GoogleIcon />
              Google
            </Button>
          </div>
          <p className="mt-8 text-center text-[12px] text-neutral-500 dark:text-neutral-500">
            SSO and OAuth are placeholders per{" "}
            <span className="font-medium text-neutral-600 dark:text-neutral-400">US-12.01</span>.
          </p>
          <p className="mt-4 text-center text-[13px] text-neutral-500">
            <Link to="/app/dashboard" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
              Skip to app shell
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
