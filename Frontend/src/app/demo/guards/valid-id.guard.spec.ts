import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, convertToParamMap, Router, UrlTree } from '@angular/router';
import { isPositiveIntId, validIdGuard } from './valid-id.guard';

/** QA STD-003: a malformed id in the address bar lands on the not-found page. */
describe('validIdGuard', () => {

    function route(params: Record<string, string>, data: Record<string, unknown> = {}): ActivatedRouteSnapshot {
        return { paramMap: convertToParamMap(params), data } as unknown as ActivatedRouteSnapshot;
    }

    function run(r: ActivatedRouteSnapshot) {
        return TestBed.runInInjectionContext(() => validIdGuard(r, {} as any));
    }

    beforeEach(() => TestBed.configureTestingModule({
        providers: [{ provide: Router, useValue: { createUrlTree: (c: string[]) => ({ commands: c }) as unknown as UrlTree } }]
    }));

    it('accepts a positive whole number', () => {
        expect(isPositiveIntId('42')).toBeTrue();
        expect(run(route({ id: '42' }))).toBeTrue();
    });

    it('sends abc, 1.5, 0 and -3 to the not-found page', () => {
        for (const bad of ['abc', '1.5', '0', '-3', '', '01']) {
            expect(isPositiveIntId(bad)).withContext(bad).toBeFalse();
            expect((run(route({ id: bad })) as any).commands).withContext(bad).toEqual(['/notfound']);
        }
    });

    it('reads the parameter a route names in data.idParam', () => {
        expect(run(route({ studentId: '7' }, { idParam: 'studentId' }))).toBeTrue();
        expect((run(route({ studentId: '1.5' }, { idParam: 'studentId' })) as any).commands).toEqual(['/notfound']);
    });
});
