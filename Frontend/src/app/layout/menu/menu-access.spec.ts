import { buildClassicMenu } from './menu-classic';
import { buildProposedMenu } from './menu-proposed';
import { MenuAccess, firstPermittedLink, gatesForRoute, visibleMenu } from './menu-access';

/**
 * Live QA M5 (25 Sep 2026): twelve roles with no dashboard section landed on Access
 * Denied after signing in. The route guard now sends them to the first row their own
 * sidebar shows, computed by the same filter the sidebar uses.
 */
describe('menu access — the first page a person may open', () => {

    const holding = (...pages: string[]): MenuAccess => ({
        canView: code => pages.includes(code),
        hasPermission: code => pages.includes(code.replace(/\.VIEW$/, '')),
        isSuperAdmin: () => false,
        isMinistryAdminOrAbove: () => false
    });
    const t = (key: string) => key;

    it('is the reports hub for a reports-only role, in both sidebars', () => {
        const access = holding('REPORTS', 'REPORT_ACADEMIC');
        expect(firstPermittedLink(visibleMenu(buildProposedMenu(t), access))?.routerLink).toEqual(['/reports/list']);
        expect(firstPermittedLink(visibleMenu(buildClassicMenu(t), access))?.routerLink).toEqual(['/reports/list']);
    });

    it('is Finance Records for a finance officer', () => {
        const access = holding('FINANCE', 'FINANCE_ARCH', 'PAYMENT_AUTH');
        expect(firstPermittedLink(visibleMenu(buildProposedMenu(t), access))?.routerLink).toEqual(['/finance']);
    });

    it('is never the home page, even for someone who may open it', () => {
        const access = holding('DASHBOARD', 'STUDENTS');
        expect(firstPermittedLink(visibleMenu(buildProposedMenu(t), access))?.routerLink).toEqual(['/students']);
    });

    it('is nothing when the sidebar is empty', () => {
        expect(firstPermittedLink(visibleMenu(buildProposedMenu(t), holding()))).toBeNull();
    });

    it('hides a row the person may not open and keeps its visible siblings', () => {
        const tree = visibleMenu(buildProposedMenu(t), holding('WAFAR'));
        const links = JSON.stringify(tree);
        expect(links).toContain('/student-finance-wafar');
        expect(links).not.toContain('/finance"');
        expect(links).not.toContain('/settings/system-settings');
    });
});

/**
 * Live QA R5-08 (27 Sep 2026): a Reports Viewer was offered Student Tracer and every read
 * behind it was refused, because its network is student records. The row now needs the
 * report code and a student-records code, as the route does.
 */
describe('menu access — Student Tracer follows its data', () => {

    const holding = (...pages: string[]): MenuAccess => ({
        canView: code => pages.includes(code),
        hasPermission: code => pages.includes(code.replace(/\.VIEW$/, '')),
        isSuperAdmin: () => false,
        isMinistryAdminOrAbove: () => false
    });
    const t = (key: string) => key;
    const TRACER = '/reports/statistical/student-tracer';
    const shows = (access: MenuAccess) => [buildClassicMenu, buildProposedMenu]
        .map(build => JSON.stringify(visibleMenu(build(t), access)).includes(TRACER));

    it('is hidden from a report reader without student records, in both sidebars', () => {
        expect(shows(holding('REPORTS', 'REPORT_OTHER'))).toEqual([false, false]);
    });

    it('is shown to a report reader who may read student records', () => {
        expect(shows(holding('REPORTS', 'REPORT_OTHER', 'STUDENTS'))).toEqual([true, true]);
        expect(shows(holding('REPORTS', 'REPORT_OTHER', 'STUDENT_HISTORY'))).toEqual([true, true]);
    });

    it('still needs the report code', () => {
        expect(shows(holding('STUDENTS'))).toEqual([false, false]);
    });
});

/**
 * R5-09 (QA round 5): Finance Officers were offered "Financial Reports", which opened Access
 * Denied: the /reports route asks REPORTS.VIEW before /reports/financial asks REPORT_FINANCE.VIEW,
 * and the role holds only the second.
 */
describe('menu access — Financial Reports (R5-09)', () => {
    const t = (key: string) => key;
    const person = (pages: string[], superAdmin = false): MenuAccess => ({
        canView: code => superAdmin || pages.includes(code),
        hasPermission: code => superAdmin || pages.includes(code.replace(/\.VIEW$/, '')),
        isSuperAdmin: () => superAdmin,
        isMinistryAdminOrAbove: () => superAdmin
    });
    const shows = (access: MenuAccess, link: string) =>
        JSON.stringify(visibleMenu(buildProposedMenu(t), access)).includes(`"${link}"`);

    // The FINANCE_OFFICER role's VIEW codes in the seeded database.
    const financeOfficer = person(['FINANCE', 'FINANCE_ARCH', 'PAYMENT_AUTH', 'REPORT_FINANCE', 'WAFAR']);
    const reportsViewer = person(['REPORTS', 'REPORT_FINANCE', 'REPORT_ACADEMIC', 'REPORT_OTHER']);

    it('is not offered to a Finance Officer without REPORTS.VIEW', () => {
        expect(shows(financeOfficer, '/reports/financial')).toBeFalse();
        expect(shows(financeOfficer, '/finance')).toBeTrue();
    });

    it('is offered to a role holding both route gates, and to the super admin', () => {
        expect(shows(reportsViewer, '/reports/financial')).toBeTrue();
        expect(shows(person([], true), '/reports/financial')).toBeTrue();
    });

    it('R5-16: a Finance Officer sees My requests but not Pending approvals', () => {
        expect(shows(financeOfficer, '/approvals/my-requests')).toBeTrue();
        expect(shows(financeOfficer, '/approvals/pending')).toBeFalse();
        expect(shows(financeOfficer, '/approvals/history')).toBeFalse();
        expect(shows(person(['REPORTS']), '/approvals/my-requests')).toBeFalse();
    });

    it('needs the gate of every route on the link, outermost first', () => {
        expect(gatesForRoute(['/reports/financial'])).toEqual([{ pageCode: 'REPORTS' }, { pageCode: 'REPORT_FINANCE' }]);
        expect(gatesForRoute(['/'])?.length).toBe(1);
        expect(gatesForRoute(['/not-in-the-map'])).toBeNull();
    });
});
