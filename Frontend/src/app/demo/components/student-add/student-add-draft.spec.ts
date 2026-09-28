import { StudentAddComponent } from './student-add.component';

/**
 * Add Student keeps a draft only of what the user changed (round-6 QA R6-07).
 *
 * <p>Opening Add Student and leaving without typing anything saved a draft, because
 * the model is never blank (defaults such as the currency are filled in on open), so
 * "Unfinished student" was offered on the next visit. Leaving after a submit wrote the
 * submitted student back as a draft too.
 */
describe('Add Student — a draft only of what was changed (R6-07)', () => {

    let saved: number;
    let cleared: number;

    function wizard(): StudentAddComponent {
        saved = 0;
        cleared = 0;
        const draftService = { save: () => saved++, clear: () => cleared++, load: () => null };
        const router = { navigate: () => Promise.resolve(true) };
        return new StudentAddComponent(
            {} as any, {} as any, { add: () => undefined } as any, router as any, {} as any, {} as any,
            {} as any, {} as any, {} as any, {} as any, {} as any, {} as any, {} as any, draftService as any);
    }

    /** The wizard's fields on the current step, by whether the user has changed each. */
    const fields = (c: StudentAddComponent, ...dirty: boolean[]) => {
        (c as any).fieldModels = dirty.map((d) => ({ dirty: d }));
    };

    it('the QA case: opening and leaving without typing keeps no draft', () => {
        const c = wizard();
        fields(c, false, false, false);
        c.ngOnDestroy();

        expect(saved).toBe(0);
    });

    it('a changed field is kept when the user leaves', () => {
        const c = wizard();
        fields(c, false, true);
        c.ngOnDestroy();

        expect(saved).toBe(1);
    });

    it('a change on an earlier step is still kept once that step\'s fields are gone', () => {
        const c = wizard();
        c.activeIndex = 1;
        fields(c, true);
        c.prevStep();
        fields(c, false, false);
        c.ngOnDestroy();

        expect(saved).toBe(2);
    });

    it('after the student is submitted, leaving writes nothing back', () => {
        jasmine.clock().install();
        try {
            const c = wizard();
            fields(c, true);
            c.afterSuccessfulSave(0);
            c.ngOnDestroy();

            expect(cleared).toBe(1);
            expect(saved).toBe(0);
            jasmine.clock().tick(2000);
        } finally {
            jasmine.clock().uninstall();
        }
    });
});
