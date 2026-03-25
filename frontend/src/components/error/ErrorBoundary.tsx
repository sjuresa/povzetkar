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

  public static getDerivedStateFromError(_: Error): State {
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
        <div className="flex flex-col items-center justify-center min-h-screen bg-slate-950 text-white p-6 text-center">
          <h1 className="text-3xl font-bold mb-4">Something went wrong</h1>
          <p className="text-slate-400 mb-8">An unexpected error has occurred and the application was unable to continue.</p>
          <button
            onClick={() => window.location.href = '/'}
            className="px-6 py-2 bg-blue-600 hover:bg-blue-500 rounded-xl transition-colors font-semibold"
          >
            Go to Home
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
