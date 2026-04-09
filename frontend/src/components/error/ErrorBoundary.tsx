import { Component, type ErrorInfo, type ReactNode } from 'react';
import logger from '../../util/logger';

interface Props {
  children?: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Global Error Boundary to catch any unhandled JavaScript exceptions in the render tree.
 * Provides a generic fallback UI and logs the error to the internal logger.
 */
export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
  };

  public static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    logger.error('[ErrorBoundary] Uncaught exception in React tree', { 
      error: error.message, 
      stack: error.stack,
      componentStack: errorInfo.componentStack 
    });
  }

  public render() {
    if (this.state.hasError) {
      return this.props.fallback || (
        <div className="flex flex-col items-center justify-center min-h-screen bg-[var(--color-bg-primary)] text-[var(--color-text-primary)] p-6 text-center">
          <h1 className="text-3xl font-bold mb-4 italic uppercase tracking-tighter">Something went wrong</h1>
          <p className="text-[var(--color-text-secondary)] mb-8 max-w-md font-medium">An unexpected error has occurred and the application was unable to continue.</p>
          <button
            onClick={() => { (window as Window).location.href = '/'; }}
            className="px-8 py-3 bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] hover:bg-[var(--color-action-hover)] rounded-xl transition-all active:scale-95 font-black uppercase tracking-widest text-xs"
          >
            Go to Home
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
