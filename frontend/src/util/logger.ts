/**
 * Simple logger utility to provide a consistent interface for logging.
 * Can be easily extended to use Sentry, Pino, or other logging services.
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogContext {
  [key: string]: unknown;
}

class Logger {
  private log(level: LogLevel, message: string, context?: LogContext) {
    const timestamp = new Date().toISOString();
    const formattedContext = context ? JSON.stringify(context) : '';
    
    // In development, we use console.* 
    // In production, this can be channeled to a logging service.
    switch (level) {
      case 'debug':
        if (import.meta.env.DEV) {
          console.debug(`[${timestamp}] DEBUG: ${message}`, formattedContext);
        }
        break;
      case 'info':
        console.info(`[${timestamp}] INFO: ${message}`, formattedContext);
        break;
      case 'warn':
        console.warn(`[${timestamp}] WARN: ${message}`, formattedContext);
        break;
      case 'error':
        console.error(`[${timestamp}] ERROR: ${message}`, formattedContext);
        break;
    }
  }

  debug(message: string, context?: LogContext) {
    this.log('debug', message, context);
  }

  info(message: string, context?: LogContext) {
    this.log('info', message, context);
  }

  warn(message: string, context?: LogContext) {
    this.log('warn', message, context);
  }

  error(message: string, context?: LogContext) {
    this.log('error', message, context);
  }
}

const logger = new Logger();
export default logger;
