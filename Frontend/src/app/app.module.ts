import { APP_INITIALIZER, NgModule } from '@angular/core';
import { HashLocationStrategy, LocationStrategy } from '@angular/common';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { APP_HTTP_INTERCEPTORS } from './demo/interceptors/http-interceptors';
import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { AppLayoutModule } from './layout/app.layout.module';
import { NotfoundComponent } from './demo/components/notfound/notfound.component';
import { TranslationService } from './demo/service/translation.service';
import { UmsAuthService } from './demo/service/ums-auth.service';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { IconDefinition } from '@ant-design/icons-angular';
import {
    ApiOutline, AppstoreOutline, ArrowLeftOutline, ArrowRightOutline, BarChartOutline, BellFill, BellOutline,
    BookOutline, CalendarOutline, CarOutline, CheckCircleFill, CheckCircleOutline, CheckOutline, ClearOutline,
    ClockCircleFill, ClockCircleOutline, CloseCircleFill, CloseCircleOutline, CloseOutline, DashboardOutline,
    DatabaseOutline, DeleteOutline, DollarCircleFill, DollarCircleOutline, DollarOutline, DownloadOutline,
    EditOutline, EnvironmentOutline, ExclamationCircleOutline, EyeInvisibleOutline, EyeOutline, FieldTimeOutline,
    FileDoneOutline, FileExcelOutline, FileOutline, FilePdfOutline, FilePdfTwoTone, FileTextOutline, FileTwoTone, FilterOutline,
    FlagOutline, FolderOpenOutline, FormOutline, GlobalOutline, IdcardOutline, ImportOutline, InboxOutline,
    InfoCircleOutline, KeyOutline, LeftOutline, LockOutline, LoginOutline, LogoutOutline, MailOutline, MenuOutline,
    MessageOutline, MinusCircleFill, NodeIndexOutline, PlusOutline, ProfileOutline, QuestionCircleOutline, ReadOutline,
    ReloadOutline, RightOutline, RiseOutline, SafetyOutline, SearchOutline, SettingOutline, StopOutline,
    TableOutline, TeamOutline, ThunderboltOutline, TranslationOutline, UndoOutline, UploadOutline,
    UserAddOutline, UserOutline, UsergroupAddOutline,
    // The staff-account pages (users list, account, add/edit) and their dialogs.
    DownOutline, EllipsisOutline, HistoryOutline, PhoneOutline, SafetyCertificateOutline, SaveOutline, SolutionOutline,
    SwapOutline, UnlockOutline, WarningOutline
} from '@ant-design/icons-angular/icons';
import { NZ_I18N, en_US, ar_EG } from 'ng-zorro-antd/i18n';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';
import { ToastModule } from 'primeng/toast';
import { SharedModule } from './demo/shared/shared.module';

/**
 * Aura ships an emerald primary. The app ran on lara-light-indigo before the
 * upgrade, so the primary is pointed back at indigo to keep buttons, links and
 * highlights the colour users know. The shades are PrimeNG 17's indigo scale,
 * matching --primary-* in assets/layout/styles/_legacy-theme-vars.scss.
 */
const MohePreset = definePreset(Aura, {
    semantic: {
        primary: {
            50: '#eef2ff',
            100: '#e0e7ff',
            200: '#c7d2fe',
            300: '#a5b4fc',
            400: '#818cf8',
            500: '#6366f1',
            600: '#4f46e5',
            700: '#4338ca',
            800: '#3730a3',
            900: '#312e81',
            950: '#1e1b4b'
        }
    }
});

/**
 * Only the icons the templates name. `import * as AllIcons` put the entire
 * Ant Design set (~800 SVGs) in the initial bundle for the 80-odd this app uses.
 * ng-zorro registers the icons its own widgets need (NZ_ICONS_USED_BY_ZORRO)
 * on its own; this list is the app's. When a template gains a new nzType, add
 * it here — an unregistered icon is fetched from the Ant CDN at runtime, which
 * fails on the ministry network. Themes: Outline unless the template says
 * nzTheme="fill" / "twotone".
 */
const icons: IconDefinition[] = [
    ApiOutline, AppstoreOutline, ArrowLeftOutline, ArrowRightOutline, BarChartOutline, BellFill, BellOutline,
    BookOutline, CalendarOutline, CarOutline, CheckCircleFill, CheckCircleOutline, CheckOutline, ClearOutline,
    ClockCircleFill, ClockCircleOutline, CloseCircleFill, CloseCircleOutline, CloseOutline, DashboardOutline,
    DatabaseOutline, DeleteOutline, DollarCircleFill, DollarCircleOutline, DollarOutline, DownloadOutline,
    EditOutline, EnvironmentOutline, ExclamationCircleOutline, EyeInvisibleOutline, EyeOutline, FieldTimeOutline,
    FileDoneOutline, FileExcelOutline, FileOutline, FilePdfOutline, FilePdfTwoTone, FileTextOutline, FileTwoTone, FilterOutline,
    FlagOutline, FolderOpenOutline, FormOutline, GlobalOutline, IdcardOutline, ImportOutline, InboxOutline,
    InfoCircleOutline, KeyOutline, LeftOutline, LockOutline, LoginOutline, LogoutOutline, MailOutline, MenuOutline,
    MessageOutline, MinusCircleFill, NodeIndexOutline, PlusOutline, ProfileOutline, QuestionCircleOutline, ReadOutline,
    ReloadOutline, RightOutline, RiseOutline, SafetyOutline, SearchOutline, SettingOutline, StopOutline,
    TableOutline, TeamOutline, ThunderboltOutline, TranslationOutline, UndoOutline, UploadOutline,
    UserAddOutline, UserOutline, UsergroupAddOutline,
    DownOutline, EllipsisOutline, HistoryOutline, PhoneOutline, SafetyCertificateOutline, SaveOutline, SolutionOutline,
    SwapOutline, UnlockOutline, WarningOutline
];

export function initializeTranslation(translationService: TranslationService) {
    return () => translationService.initialize();
}

/**
 * Turns a stored token back into a session before the router runs.
 *
 * <p>The account, its permissions and its attachés are no longer kept in browser
 * storage, so a page load starts knowing nothing but the token. `AuthGuard` and
 * `UmsPermissionGuard` both decide synchronously on what the session holds, and
 * an `APP_INITIALIZER` is the one hook that runs — and is waited for — before the
 * first navigation. Without it every refresh would land on the login page.
 *
 * <p>It never rejects: `restoreSession` resolves on failure too (see its
 * contract), because an initializer that rejects aborts the bootstrap and shows
 * a blank page. A session that could not be restored just starts signed out.
 */
export function initializeSession(umsAuthService: UmsAuthService) {
    return () => umsAuthService.restoreSession();
}

/**
 * Initial ng-zorro locale. Deliberately NOT derived from TranslationService:
 * that service injects NzI18nService, which reads this token, so a factory
 * depending on the service is a DI cycle (NG0200 at bootstrap, 28 Aug 2026).
 * The value here only covers the instant before APP_INITIALIZER runs;
 * TranslationService.setLanguage then calls NzI18nService.setLocale for the
 * saved language and for every later switch.
 */
export function nzI18nFactory() {
    let saved: string | null = null;
    try {
        saved = localStorage.getItem('selectedLanguage');
    } catch {
        // Storage unavailable (private mode, quota): English until the service runs.
    }
    return saved === 'ar' ? ar_EG : en_US;
}

@NgModule({ declarations: [
        AppComponent, NotfoundComponent
    ],
    bootstrap: [AppComponent], imports: [AppRoutingModule,
        AppLayoutModule,
        SharedModule,
        ToastModule], providers: [
        { provide: LocationStrategy, useClass: HashLocationStrategy },
        { provide: NZ_ICONS, useValue: icons },
        { provide: NZ_I18N, useFactory: nzI18nFactory },
        TranslationService, MessageService,
        // Order matters: ErrorInterceptor is outermost so AuthInterceptor's refresh runs first (FE-02).
        ...APP_HTTP_INTERCEPTORS,
        {
            provide: APP_INITIALIZER,
            useFactory: initializeTranslation,
            deps: [TranslationService],
            multi: true
        },
        {
            provide: APP_INITIALIZER,
            useFactory: initializeSession,
            deps: [UmsAuthService],
            multi: true
        },
        provideHttpClient(withInterceptorsFromDi()),
        provideAnimationsAsync(),
        providePrimeNG({
            theme: {
                preset: MohePreset,
                options: {
                    // Matches DARK_MODE_CLASS in LayoutService, which toggles it on <html>.
                    darkModeSelector: '.app-dark',
                    // Keep PrimeNG's generated rules beneath our own overrides in
                    // styles.scss, which are unlayered and would otherwise lose to them.
                    cssLayer: { name: 'primeng', order: 'primeng' }
                }
            },
            // Enabled globally, as AppComponent used to do via PrimeNGConfig. The layout
            // still suppresses it per-shell with the p-ripple-disabled class.
            ripple: true,
            // Dropdown, date and autocomplete panels open on <body> unless a component
            // says otherwise. PrimeNG 21 defaults to 'self', which clips a panel to any
            // dialog or card with overflow hidden (review M5: the bank and branch
            // country lists); some 330 p-selects set no appendTo of their own.
            overlayAppendTo: 'body'
        })
    ] })
export class AppModule { }
