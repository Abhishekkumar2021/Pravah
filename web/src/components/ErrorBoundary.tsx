import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertCircle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/Button";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * React Error Boundary for catching runtime errors in the component tree.
 *
 * Displays a user-friendly error page and provides retry functionality.
 */
export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo);
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex min-h-[400px] flex-col items-center justify-center p-8 text-center">
          <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/20">
            <AlertCircle className="h-8 w-8 text-red-600 dark:text-red-400" />
          </div>
          <h2 className="mb-2 text-xl font-semibold text-neutral-900 dark:text-neutral-100">
            Something went wrong
          </h2>
          <p className="mb-6 max-w-md text-sm text-neutral-600 dark:text-neutral-400">
            An unexpected error occurred. You can try again or refresh the page.
          </p>
          {import.meta.env.DEV && this.state.error && (
            <pre className="mb-6 max-w-lg overflow-auto rounded-md bg-neutral-100 p-4 text-left text-xs text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300">
              {this.state.error.message}
            </pre>
          )}
          <div className="flex gap-3">
            <Button variant="secondary" onClick={this.handleRetry}>
              Try again
            </Button>
            <Button variant="primary" onClick={this.handleReload}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Refresh page
            </Button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
