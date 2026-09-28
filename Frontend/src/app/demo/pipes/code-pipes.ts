/** The three code-list pipes, for `imports: [...CODE_PIPES]` in standalone components. */
import { CodeIconPipe } from './code-icon.pipe';
import { CodeLabelPipe } from './code-label.pipe';
import { CodeSeverityPipe } from './code-severity.pipe';

export { CodeIconPipe, CodeLabelPipe, CodeSeverityPipe };

export const CODE_PIPES = [CodeLabelPipe, CodeSeverityPipe, CodeIconPipe] as const;
