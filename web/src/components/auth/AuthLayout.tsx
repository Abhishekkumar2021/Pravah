import { Link } from "react-router-dom";
import type { ReactNode } from "react";

const HERO_PATTERN =
  "data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20width=%2240%22%20height=%2240%22%20viewBox=%220%200%2040%2040%22%3E%3Cg%20fill=%22none%22%20fill-rule=%22evenodd%22%3E%3Cg%20fill=%22%23ffffff%22%20fill-opacity=%220.04%22%3E%3Cpath%20d=%22M0%2038.59l2.83-2.83%201.41%201.41L1.41%2040H0v-1.41zM0%201.4l2.83%202.83%201.41-1.41L1.41%200H0v1.41zM38.59%2040l-2.83-2.83%201.41-1.41L40%2038.59V40h-1.41zM40%201.41l-2.83%202.83-1.41-1.41L38.59%200H40v1.41zM20%2018.6l2.83-2.83%201.41%201.41L21.41%2020l2.83%202.83-1.41%201.41L20%2021.41l-2.83%202.83-1.41-1.41L18.59%2020l-2.83-2.83%201.41-1.41L20%2018.59z%22/%3E%3C/g%3E%3C/g%3E%3C/svg%3E";

export type AuthLayoutProps = {
  title: string;
  description?: string;
  heroTitle: string;
  heroDescription: string;
  backTo?: { label: string; href: string };
  children: ReactNode;
};

export function AuthLayout({
  title,
  description,
  heroTitle,
  heroDescription,
  backTo = { label: "Back to sign in", href: "/login" },
  children,
}: AuthLayoutProps) {
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
            {heroTitle}
          </h2>
          <p className="mt-4 text-[14px] leading-relaxed text-neutral-400">{heroDescription}</p>
          <div className="mt-8 flex items-center justify-center gap-6 text-[12px] text-neutral-500">
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              Secure
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-500" />
              Fast
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
              Reliable
            </span>
          </div>
        </header>
      </aside>

      {/* Form panel */}
      <main className="flex min-h-dvh flex-col justify-center border-l border-neutral-200 bg-white px-6 py-12 dark:border-neutral-800 dark:bg-neutral-950">
        <article className="mx-auto w-full max-w-md">
          {/* Mobile header */}
          <header className="mb-8 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 shadow-lg shadow-blue-500/25">
              <svg className="h-5 w-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
              </svg>
            </div>
            <h1 className="page-title mt-4">{title}</h1>
            {description && <p className="page-desc mt-1">{description}</p>}
          </header>

          {/* Back link */}
          <Link
            to={backTo.href}
            className="mb-6 inline-flex items-center gap-1.5 text-[13px] font-medium text-neutral-500 transition-colors hover:text-neutral-800 dark:hover:text-neutral-200"
          >
            <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M17 10a.75.75 0 01-.75.75H5.612l4.158 3.96a.75.75 0 11-1.04 1.08l-5.5-5.25a.75.75 0 010-1.08l5.5-5.25a.75.75 0 111.04 1.08L5.612 9.25H16.25A.75.75 0 0117 10z" clipRule="evenodd" />
            </svg>
            {backTo.label}
          </Link>

          {/* Desktop header */}
          <header className="hidden lg:block">
            <h1 className="page-title">{title}</h1>
            {description && <p className="page-desc mt-2">{description}</p>}
          </header>

          <section className="mt-8">{children}</section>
        </article>
      </main>
    </section>
  );
}
