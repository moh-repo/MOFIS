import { TestBed } from '@angular/core/testing';
import { StudentDraftService } from './student-draft.service';
import { UmsAuthService } from './ums-auth.service';

/**
 * The unfinished Add Student wizard (QA C7 / N1, decision DEC-13 (a)).
 *
 * <p>The wizard is seven steps long and kept everything in memory, so a reload
 * or a mis-clicked menu item threw it all away without a word.
 */
describe('StudentDraftService', () => {
    let service: StudentDraftService;
    let user: { id: number } | null;

    beforeEach(() => {
        user = { id: 7 };
        localStorage.clear();
        TestBed.configureTestingModule({
            providers: [
                StudentDraftService,
                { provide: UmsAuthService, useValue: { get currentUserValue() { return user; } } }
            ]
        });
        service = TestBed.inject(StudentDraftService);
    });

    afterEach(() => localStorage.clear());

    it('keeps what was typed, and the step it was typed on', () => {
        service.save({ studentName: 'Layla' }, 3);

        const draft = service.load();
        expect(draft).withContext('a draft was written').not.toBeNull();
        expect((draft!.student as { studentName: string }).studentName).toBe('Layla');
        expect(draft!.step).toBe(3);
    });

    it('has nothing to offer before anything is typed', () => {
        expect(service.has()).toBeFalse();
        expect(service.load()).toBeNull();
    });

    it('forgets it when told to', () => {
        service.save({ studentName: 'Layla' }, 1);
        expect(service.has()).toBeTrue();

        service.clear();
        expect(service.has()).toBeFalse();
    });

    it('does not hand one clerk the draft of another', () => {
        service.save({ studentName: 'Layla' }, 2);

        user = { id: 8 };
        expect(service.has())
            .withContext('a shared workstation must not leak a half-entered student')
            .toBeFalse();

        user = { id: 7 };
        expect(service.has()).toBeTrue();
    });

    it('drops a draft that has gone stale rather than offering it', () => {
        service.save({ studentName: 'Layla' }, 1);

        // Reach in and age it past the limit.
        const key = Object.keys(localStorage).find(k => k.startsWith('mohe.studentDraft.'))!;
        const stored = JSON.parse(localStorage.getItem(key)!);
        stored.savedAt = Date.now() - StudentDraftService.MAX_AGE_MS - 1;
        localStorage.setItem(key, JSON.stringify(stored));

        expect(service.load()).toBeNull();
        expect(localStorage.getItem(key)).withContext('and clears it').toBeNull();
    });

    it('treats unreadable storage as no draft rather than failing the page', () => {
        const key = Object.keys(localStorage).find(k => k.startsWith('mohe.studentDraft.'));
        localStorage.setItem(key ?? 'mohe.studentDraft.7', 'not json');

        expect(() => service.load()).not.toThrow();
        expect(service.load()).toBeNull();
    });
});
