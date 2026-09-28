import { Injectable } from '@angular/core';
import { UmsAuthService } from './ums-auth.service';

/** What is kept of an unfinished Add Student wizard. */
export interface StudentDraft {
    /** The wizard's model, as it stood. */
    student: unknown;
    /** The step the user was on, so they come back to where they left. */
    step: number;
    /** When it was written, so a stale draft is not offered forever. */
    savedAt: number;
}

/**
 * The unfinished Add Student wizard, kept in the browser (QA C7 / N1,
 * decision DEC-13 (a)).
 *
 * <p>The wizard is long — seven steps of a student's life — and everything in
 * it lived in memory alone. A reload, a mis-click on a menu item, a session
 * that dropped, and it was all gone with nothing to say so. The reviewer asked
 * for a draft; option (a) was chosen: the browser holds it, so it survives a
 * reload or a wrong turn on the same computer, without a table, an API and a
 * cleanup job for something that is not a record yet.
 *
 * <h2>What it deliberately is not</h2>
 *
 * <ul>
 *   <li><b>Not a server draft.</b> It does not follow the user to another
 *       computer, and it is not a record anybody else can see. A half-entered
 *       student is not ministry data until it is submitted for approval.</li>
 *   <li><b>Not a place for documents.</b> Only the typed fields are kept. The
 *       photo and the uploaded files are {@code File} objects, which do not
 *       survive being written down; offering to restore a draft that silently
 *       dropped them would be worse than not offering one.</li>
 *   <li><b>Not shared between accounts.</b> The key carries the signed-in user,
 *       so a shared workstation does not hand one clerk's half-finished student
 *       to the next person who sits down.</li>
 * </ul>
 *
 * <p>Every access is wrapped: storage throws in a private window, and a draft
 * is a convenience — losing it must never break the page that was working
 * without one.
 */
@Injectable({ providedIn: 'root' })
export class StudentDraftService {

    /** Older than this and it is not offered; it is cleared instead. */
    static readonly MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

    private static readonly PREFIX = 'mohe.studentDraft.';

    constructor(private auth: UmsAuthService) { }

    /** The key for the signed-in user; anonymous gets its own so it cannot collide. */
    private key(): string {
        const id = this.auth.currentUserValue?.id ?? 'anonymous';
        return `${StudentDraftService.PREFIX}${id}`;
    }

    /** Writes the draft, or quietly does nothing if the browser will not have it. */
    save(student: unknown, step: number): void {
        try {
            const draft: StudentDraft = { student, step, savedAt: Date.now() };
            localStorage.setItem(this.key(), JSON.stringify(draft));
        } catch {
            // A full or blocked store is not worth a message: the wizard works.
        }
    }

    /** The stored draft, or null when there is none, it is unreadable, or it is stale. */
    load(): StudentDraft | null {
        try {
            const raw = localStorage.getItem(this.key());
            if (!raw) { return null; }
            const draft = JSON.parse(raw) as StudentDraft;
            if (!draft?.student || typeof draft.savedAt !== 'number') { return null; }
            if (Date.now() - draft.savedAt > StudentDraftService.MAX_AGE_MS) {
                this.clear();
                return null;
            }
            return draft;
        } catch {
            return null;
        }
    }

    /** True when there is a draft worth offering. */
    has(): boolean {
        return this.load() !== null;
    }

    clear(): void {
        try {
            localStorage.removeItem(this.key());
        } catch {
            // As above.
        }
    }
}
