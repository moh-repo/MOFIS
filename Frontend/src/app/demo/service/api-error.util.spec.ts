import { HttpErrorResponse } from '@angular/common/http';
import { extractApiErrorMessage } from './api-error.util';

function httpError(body: unknown, status = 400): HttpErrorResponse {
  return new HttpErrorResponse({ error: body, status, statusText: 'Bad Request' });
}

describe('extractApiErrorMessage', () => {

  /*
   * The shape that was being dropped. StudentFinanceController and four other
   * controllers catch broadly and return ResponseEntity.badRequest().body(
   * Map.of("error", e.getMessage())), so the reason lives under `error`, not
   * `message`. Reading only `message` turned an actionable rejection into an
   * opaque "update failed".
   */
  it('reads the { error } shape returned by the finance controller', () => {
    const message = 'There is already a pending approval request for this entity';
    expect(extractApiErrorMessage(httpError({ error: message }))).toBe(message);
  });

  it('reads the { message } shape from GlobalExceptionHandler', () => {
    expect(extractApiErrorMessage(httpError({ message: 'Record not found' })))
      .toBe('Record not found');
  });

  it('prefers field errors, which are the most specific', () => {
    const detail = extractApiErrorMessage(httpError({
      message: 'Validation failed',
      fieldErrors: [
        { field: 'studentFinId', message: 'must not be blank' },
        { field: 'amount', message: 'must be positive' }
      ]
    }));
    expect(detail).toBe('studentFinId: must not be blank\namount: must be positive');
  });

  it('handles a plain string body', () => {
    expect(extractApiErrorMessage(httpError('Something broke'))).toBe('Something broke');
  });

  it('returns null when there is nothing usable, so callers fall back', () => {
    expect(extractApiErrorMessage(httpError({}))).toBeNull();
    expect(extractApiErrorMessage(httpError({ error: '   ' }))).toBeNull();
    expect(extractApiErrorMessage(null)).toBeNull();
  });

  it('ignores a non-string error field rather than stringifying it', () => {
    expect(extractApiErrorMessage(httpError({ error: { nested: true } }))).toBeNull();
  });
});
