import { TestBed } from '@angular/core/testing';
import { ROUTER_CONFIGURATION } from '@angular/router';
import { AppRoutingModule } from './app-routing.module';

/**
 * How the router positions a page when you arrive on it.
 *
 * <p>This is one word in one file and no compiler can see it, but it decides
 * whether every page in the app opens at the top or somewhere in its middle.
 *
 * <p>'enabled' — the value this started on — means "put the reader back where
 * they were". That only makes sense when a page is the same height coming back
 * as it was going out, and these are not: the lists are lazy and paginated, and
 * the student profile builds all twelve of its tab panels before any of their
 * data has arrived. The router sets the offset the moment routing finishes, the
 * content grows underneath it, and the reader lands somewhere that is neither
 * the top nor where they left off.
 *
 * <p>The real {@link AppRoutingModule} is imported on purpose. Restating the
 * options here would only assert that the test agrees with itself.
 */
describe('router scroll configuration', () => {

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [AppRoutingModule] });
    });

    it('opens every page at the top rather than restoring an offset', () => {
        const config = TestBed.inject(ROUTER_CONFIGURATION);

        expect(config.scrollPositionRestoration)
            .withContext('"enabled" restores the previous offset and lands mid-page')
            .toBe('top');
    });

    it('still reloads on a same-url navigation', () => {
        // Re-selecting the page you are already on has to re-run its resolvers,
        // or the menu item does nothing.
        expect(TestBed.inject(ROUTER_CONFIGURATION).onSameUrlNavigation).toBe('reload');
    });
});
