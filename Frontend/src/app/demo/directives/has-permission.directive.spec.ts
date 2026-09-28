import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';
import { HasPermissionDirective } from './has-permission.directive';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';

@Component({
    template: `
        <span id="single" *hasPermission="'STUDENTS.EDIT'">single</span>
        <span id="legacy" *hasPermission="'STUDENTS|EDIT'">legacy</span>
        <span id="anyOf" *hasPermission="['DASHBOARD.VIEW', 'DASHBOARD_FINANCE.VIEW']">any-of</span>
        <span id="anyOfLegacy" *hasPermission="['DASHBOARD|VIEW', 'DASHBOARD_FINANCE|VIEW']">any-of legacy</span>
        <span id="adminTools" *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true">admin tools</span>
    `,
    imports: [HasPermissionDirective]
})
class HostComponent { }

/**
 * The directive took one code; the dashboard sections need "whole dashboard OR
 * this section", so it now takes an array as well. Single-string callers (the
 * 113 existing uses) must behave exactly as before.
 */
describe('HasPermissionDirective', () => {
    let fixture: ComponentFixture<HostComponent>;
    let granted: Set<string>;
    let permissions$: BehaviorSubject<string[]>;
    let profile$: Subject<unknown>;
    let ministryAdmin: boolean;

    const has = (id: string) => !!fixture.nativeElement.querySelector(`#${id}`);

    beforeEach(async () => {
        granted = new Set<string>();
        permissions$ = new BehaviorSubject<string[]>([]);
        profile$ = new Subject<unknown>();
        ministryAdmin = false;

        const permissionService = {
            hasPermission: (code: string) => granted.has(code),
            hasAnyPermission: (codes: string[]) => codes.some(code => granted.has(code))
        };

        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [
                { provide: UmsPermissionService, useValue: permissionService },
                { provide: UmsAuthService, useValue: {
                    permissions$: permissions$.asObservable(),
                    profile$: profile$.asObservable(),
                    isMinistryAdminOrAbove: () => ministryAdmin
                } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(HostComponent);
    });

    it('renders a single-code template only when that code is held (unchanged behaviour)', () => {
        granted.add('STUDENTS.EDIT');
        fixture.detectChanges();

        expect(has('single')).toBeTrue();
        expect(has('legacy')).withContext('PAGE|ACTION still normalises to PAGE.ACTION').toBeTrue();
        expect(has('anyOf')).toBeFalse();
    });

    it('renders an any-of template when at least one of the codes is held', () => {
        granted.add('DASHBOARD_FINANCE.VIEW');
        fixture.detectChanges();

        expect(has('anyOf')).toBeTrue();
        expect(has('anyOfLegacy')).withContext('the | separator is normalised per element').toBeTrue();
        expect(has('single')).toBeFalse();
    });

    it('hides an any-of template when none of the codes is held', () => {
        granted.add('STUDENTS.VIEW');
        fixture.detectChanges();

        expect(has('anyOf')).toBeFalse();
        expect(has('anyOfLegacy')).toBeFalse();
    });

    it('re-evaluates when the permission list changes', () => {
        fixture.detectChanges();
        expect(has('anyOf')).toBeFalse();

        granted.add('DASHBOARD.VIEW');
        permissions$.next(['DASHBOARD.VIEW']);
        fixture.detectChanges();

        expect(has('anyOf')).toBeTrue();

        granted.clear();
        permissions$.next([]);
        fixture.detectChanges();

        expect(has('anyOf')).toBeFalse();
    });

    // FE-11: role/group/permission writes are ADMIN_TOOLS_* on the server — the super or
    // ministry admin, and then only with the UMS_ADMIN code.
    describe('adminTools', () => {
        it('hides the element from a country admin that holds the code', () => {
            granted.add('UMS_ADMIN.EDIT');
            fixture.detectChanges();
            expect(has('single')).withContext('other elements unaffected').toBeFalse();
            expect(has('adminTools')).toBeFalse();
        });

        it('hides it from a ministry admin without the code', () => {
            ministryAdmin = true;
            fixture.detectChanges();
            expect(has('adminTools')).toBeFalse();
        });

        it('shows it to a ministry admin (or above) with the code', () => {
            ministryAdmin = true;
            granted.add('UMS_ADMIN.EDIT');
            fixture.detectChanges();
            expect(has('adminTools')).toBeTrue();
        });

        it('re-evaluates when the profile (and with it the tier) arrives after the permissions', () => {
            granted.add('UMS_ADMIN.EDIT');
            fixture.detectChanges();
            expect(has('adminTools')).toBeFalse();

            ministryAdmin = true;
            profile$.next({});
            fixture.detectChanges();
            expect(has('adminTools')).toBeTrue();
        });
    });
});
