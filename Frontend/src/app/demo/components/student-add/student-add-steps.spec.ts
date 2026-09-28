import { StudentAddComponent } from './student-add.component';

/**
 * While editing, the wizard's sidebar steps are pickable, not only "Next"/"Back".
 *
 * Editing a student meant paging through all ten steps to reach the one that
 * needed a change. Any step can now be opened from the sidebar in edit mode.
 * Adding a student is unchanged: the sidebar only shows progress there.
 */
describe('StudentAddComponent step selection', () => {

    /** The component without Angular: only what goToStep touches. */
    function wizard(edit: boolean): StudentAddComponent {
        const c = Object.create(StudentAddComponent.prototype) as StudentAddComponent;
        c.activeIndex = 0;
        c.loading = false;
        c.isEditMode = edit;
        c.steps = Array.from({ length: 10 }, (_, i) => ({ label: 'step ' + i }));
        return c;
    }

    it('editing: a later step opens directly', () => {
        const c = wizard(true);
        c.goToStep(7);
        expect(c.activeIndex).toBe(7);
    });

    it('editing: an earlier step opens directly', () => {
        const c = wizard(true);
        c.activeIndex = 9;
        c.goToStep(3);
        expect(c.activeIndex).toBe(3);
    });

    it('adding: the sidebar does not navigate', () => {
        const c = wizard(false);
        c.activeIndex = 5;
        c.goToStep(8);
        expect(c.activeIndex).toBe(5);
        c.goToStep(1);
        expect(c.activeIndex).toBe(5);
    });

    it('ignores the current step, steps that do not exist, and clicks while loading', () => {
        const c = wizard(true);
        c.activeIndex = 4;
        c.goToStep(4);
        c.goToStep(-1);
        c.goToStep(10);
        expect(c.activeIndex).toBe(4);
        c.loading = true;
        c.goToStep(2);
        expect(c.activeIndex).toBe(4);
    });
});
