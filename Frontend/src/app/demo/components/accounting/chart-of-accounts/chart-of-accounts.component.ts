import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { TranslationService } from '../../../service/translation.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { CodeListService } from '../../../service/code-list.service';
import { sortTreeNodes } from '../../../service/table-sort.util';
import { ConfirmationService, MessageService, SortEvent, TreeNode } from 'primeng/api';
import {
    AccountingApiService, ChartSummary, CoaAccount, CoaLevel, CoaSegment, FiscalYear
} from '../accounting-api.service';

/**
 * The chart of accounts (الدليل المحاسبي).
 *
 * The hierarchy — أبواب، بنود، أنواع، مجموعات، حسابات — is fixed by the Executive
 * Regulation of the Financial Law, but the Regulation also defines the level
 * names, the code widths and every code value. So this screen renders whatever
 * levels the server returns and holds no code values of its own.
 *
 * On a fresh install the tree is empty, and that is correct: the codes come from
 * the official الدليل المحاسبي الموحد, not from the application.
 */
@Component({
    selector: 'app-chart-of-accounts',
    templateUrl: './chart-of-accounts.component.html',
    providers: [MessageService, ConfirmationService],
    // Declared by AccountingModule, matching the wafar module pattern.
    // Angular 21 defaults components to standalone, so this must be explicit.
    standalone: false
})
export class ChartOfAccountsComponent implements OnInit {

    loading = false;
    saving = false;

    fiscalYears: FiscalYear[] = [];
    segments: CoaSegment[] = [];
    levels: CoaLevel[] = [];

    selectedFiscalYearId: number | null = null;
    selectedSegmentId: number | null = null;

    tree: TreeNode<CoaAccount>[] = [];
    summary: ChartSummary | null = null;

    dialogVisible = false;
    editing: Partial<CoaAccount> = {};
    parentForNew: CoaAccount | null = null;

    /**
     * Set when Save is pressed, so the dialog can say which field is missing.
     * A code or a name left empty used to be reported by one toast that named
     * neither.
     */
    submitted = false;

    viewVisible = false;
    viewing: CoaAccount | null = null;
    /** The account the one being viewed hangs from; the tree shows it, the row does not. */
    viewParent: CoaAccount | null = null;

    /**
     * Attachés this user may file an account under. A user with one attaché needs
     * no choice — the server stamps theirs — and only the ministry may leave it
     * empty for the unified chart, so the field appears only when there is a real
     * decision to make.
     */
    countryOptions: LookupItem[] = [];

    /** Account natures, from the account-nature list (its extra column is the normal balance). */
    natures: Array<{ label: string; value: string }> = [];

    constructor(
        private api: AccountingApiService,
        private messages: MessageService,
        private confirmation: ConfirmationService,
        private translate: TranslationService,
        private lookupService: LookupService,
        private codeLists: CodeListService
    ) {}

    ngOnInit(): void {
        this.natures = this.codeLists.options('account-nature');
        this.translate.currentLanguage$.subscribe(() => { this.natures = this.codeLists.options('account-nature'); });
        this.loading = true;
        // The attaché is a study country; the home-country list reuses its ids for other countries (live QA H6).
        this.lookupService.getStudyCountriesIn().subscribe({
            next: countries => this.countryOptions = countries,
            error: () => { this.countryOptions = []; }
        });
        this.api.fiscalYears().subscribe({
            next: years => {
                this.fiscalYears = years;
                const open = years.find(y => y.status === 'OPEN') ?? years[0];
                this.selectedFiscalYearId = open?.id ?? null;
                this.loadStructure();
            },
            error: () => { this.loading = false; this.toastError('ACCOUNTING.LOAD_FAILED'); }
        });
    }

    /**
     * Segments and levels, then the accounts. On success reload() takes over the
     * spinner (it sets and clears `loading` itself); the error path clears it here,
     * otherwise a failed structure call left the spinner running forever.
     */
    private loadStructure(): void {
        forkJoin({ segments: this.api.segments(), levels: this.api.levels() }).subscribe({
            next: ({ segments, levels }) => {
                this.segments = segments.filter(s => s.active).sort((a, b) => a.ordinal - b.ordinal);
                this.selectedSegmentId = this.segments[0]?.id ?? null;
                this.levels = levels;
                this.reload();
            },
            error: () => { this.loading = false; this.toastError('ACCOUNTING.LOAD_FAILED'); }
        });
    }

    reload(): void {
        if (this.selectedFiscalYearId == null) { return; }
        this.loading = true;
        this.api.accounts(this.selectedFiscalYearId, this.selectedSegmentId ?? undefined)
            .pipe(finalize(() => this.loading = false))
            .subscribe({
                next: accounts => { this.tree = this.buildTree(accounts); },
                error: () => this.toastError('ACCOUNTING.LOAD_FAILED')
            });
        this.api.chartSummary(this.selectedFiscalYearId).subscribe({
            next: s => this.summary = s,
            error: () => this.toastError('ACCOUNTING.LOAD_FAILED')
        });
    }

    /**
     * Assembles the flat account list into a tree by parent id.
     *
     * Any account whose parent is not in the current result set is treated as a
     * root, so filtering by segment cannot make rows disappear entirely.
     */
    private buildTree(accounts: CoaAccount[]): TreeNode<CoaAccount>[] {
        const nodes = new Map<number, TreeNode<CoaAccount>>();
        accounts.forEach(a => nodes.set(a.id, { data: a, children: [], expanded: true }));

        const roots: TreeNode<CoaAccount>[] = [];
        accounts.forEach(a => {
            const node = nodes.get(a.id)!;
            const parent = a.parentAccountId != null ? nodes.get(a.parentAccountId) : undefined;
            if (parent) {
                parent.children!.push(node);
            } else {
                roots.push(node);
            }
        });
        return roots;
    }

    /** The level's name in the current language — from the server, never hard-coded. */
    /**
     * Sorts the accounts within each branch, so the tree keeps its shape: a
     * child never leaves the parent it hangs from, and the code column — the
     * one people order by — reads 1, 2, 10 rather than 1, 10, 2.
     */
    sortTree(event: SortEvent): void {
        sortTreeNodes(event, {
            name: (account: CoaAccount) => this.accountName(account),
            level: (account: CoaAccount) => this.levelName(account),
            nature: (account: CoaAccount) => this.translate.translate('ACCOUNTING.NATURE_' + account.accountNature),
            belongsTo: (account: CoaAccount) => this.countryName(account.studyCountryInId)
        }, this.translate.getCurrentLanguage());
    }

    levelName(account: CoaAccount): string {
        const level = this.levels.find(
            l => l.segmentId === account.segmentId && l.levelNo === account.levelNo);
        if (!level) { return `${account.levelNo}`; }
        return this.isArabic() ? level.nameAr : level.nameEn;
    }

    accountName(account: CoaAccount): string {
        return this.isArabic() ? account.nameAr : (account.nameEn || account.nameAr);
    }

    segmentName(segment: CoaSegment): string {
        return this.isArabic() ? segment.nameAr : segment.nameEn;
    }

    private isArabic(): boolean {
        return (this.translate.getCurrentLanguage() || 'en').startsWith('ar');
    }

    // ------------------------------------------------------------- editing

    openNewRoot(): void {
        this.submitted = false;
        this.parentForNew = null;
        this.editing = { accountNature: 'EXPENSE', postable: false, budgetary: false };
        this.dialogVisible = true;
    }

    openNewChild(parent: CoaAccount): void {
        this.submitted = false;
        this.parentForNew = parent;
        this.editing = {
            parentAccountId: parent.id,
            segmentId: parent.segmentId,
            fiscalYearId: parent.fiscalYearId,
            accountNature: parent.accountNature,
            postable: false,
            budgetary: false,
            // Defaults to the parent's attaché rather than to nothing: a child of
            // an attaché's own account belongs to that attaché, and a child of the
            // unified chart is shared unless the user says otherwise.
            studyCountryInId: parent.studyCountryInId
        };
        this.dialogVisible = true;
    }

    /** Shown only when the user could file the account in more than one place. */
    get needsAttacheChoice(): boolean {
        return !this.editing.id && this.countryOptions.length > 1;
    }

    countryName(id?: number | null): string {
        if (id == null) return this.translate.translate('ACCOUNTING.UNIFIED_CHART');
        return this.countryOptions.find(c => c.id === id)?.name ?? String(id);
    }

    openEdit(account: CoaAccount): void {
        this.submitted = false;
        this.parentForNew = null;
        this.editing = { ...account };
        this.dialogVisible = true;
    }

    /**
     * The whole account, including what the tree has no column for: the parent it
     * hangs from and the normal balance the server derived from its nature.
     */
    openView(account: CoaAccount): void {
        this.viewing = account;
        this.viewParent = account.parentAccountId == null
            ? null
            : this.flatten(this.tree).find(a => a.id === account.parentAccountId) ?? null;
        this.viewVisible = true;
    }

    private flatten(nodes: TreeNode<CoaAccount>[]): CoaAccount[] {
        return nodes.flatMap(n => [n.data as CoaAccount, ...this.flatten(n.children ?? [])]);
    }

    save(): void {
        this.submitted = true;
        if (!this.editing.code || !this.editing.nameAr) {
            this.toastError('ACCOUNTING.CODE_AND_NAME_REQUIRED');
            return;
        }
        this.saving = true;

        const done = {
            next: () => {
                this.saving = false;
                this.dialogVisible = false;
                this.reload();
                this.toastSuccess('ACCOUNTING.SAVED');
            },
            // The server's message is shown verbatim: it explains *why* — a parent
            // with children cannot be postable, a code width does not match its
            // level — and a generic "save failed" would throw that away.
            error: (err: any) => {
                this.saving = false;
                this.messages.add({
                    severity: 'error',
                    summary: this.translate.translate('ACCOUNTING.SAVE_FAILED'),
                    detail: err?.error?.message || err?.message || '',
                    life: 8000
                });
            }
        };

        if (this.editing.id) {
            this.api.updateAccount(this.editing.id, this.editing).subscribe(done);
        } else {
            this.api.createAccount({
                ...this.editing,
                segmentId: this.editing.segmentId ?? this.selectedSegmentId ?? undefined,
                fiscalYearId: this.editing.fiscalYearId ?? this.selectedFiscalYearId ?? undefined
            }).subscribe(done);
        }
    }

    /** Asks first: retiring an account hides it from every posting screen. */
    confirmDeactivate(account: CoaAccount): void {
        this.confirmation.confirm({
            header: this.translate.translate('ACCOUNTING.DEACTIVATE'),
            message: this.translate.translate('ACCOUNTING.CONFIRM_DEACTIVATE')
                .replace('{code}', account.fullCode)
                .replace('{name}', this.accountName(account)),
            icon: 'pi pi-exclamation-triangle',
            acceptButtonStyleClass: 'p-button-danger',
            accept: () => this.deactivate(account)
        });
    }

    /** Retires an account. There is no delete — see the service javadoc. */
    deactivate(account: CoaAccount): void {
        this.api.deactivateAccount(account.id).subscribe({
            next: () => { this.reload(); this.toastSuccess('ACCOUNTING.DEACTIVATED'); },
            error: () => this.toastError('ACCOUNTING.SAVE_FAILED')
        });
    }

    private toastSuccess(key: string): void {
        this.messages.add({ severity: 'success', summary: this.translate.translate(key) });
    }

    private toastError(key: string): void {
        this.messages.add({ severity: 'error', summary: this.translate.translate(key) });
    }
}
