import { NZ_ICONS } from 'ng-zorro-antd/icon';
import {
    ApiOutline, ArrowLeftOutline, ArrowRightOutline, CheckOutline, ClearOutline, ClockCircleOutline, DeleteOutline,
    DownOutline, EditOutline, EllipsisOutline, EyeOutline, FileTextOutline, FilterOutline, GlobalOutline, HistoryOutline,
    IdcardOutline, KeyOutline, LockOutline, MailOutline, PlusOutline, ProfileOutline, ReloadOutline,
    SafetyCertificateOutline, SafetyOutline, SaveOutline, SearchOutline, SettingOutline, SolutionOutline, StopOutline,
    SwapOutline, TeamOutline, TranslationOutline, UnlockOutline, UserAddOutline, UserOutline, UsergroupAddOutline,
    WarningOutline
} from '@ant-design/icons-angular/icons';

/**
 * The ng-zorro icons the Staff-access pages render, registered up front.
 *
 * <p>The application registers its icons in app.module.ts. A spec that builds
 * one of these pages without that provider makes ng-zorro fetch each icon's
 * SVG over HttpClient, which a stubbed client cannot answer; the icon
 * directive then throws "does not exist or is not registered" from inside a
 * promise and the fixture never settles.
 */
export function provideUmsTestIcons() {
    return {
        provide: NZ_ICONS,
        useValue: [
            ApiOutline, ArrowLeftOutline, ArrowRightOutline, CheckOutline, ClearOutline, ClockCircleOutline, DeleteOutline,
            DownOutline, EditOutline, EllipsisOutline, EyeOutline, FileTextOutline, FilterOutline, GlobalOutline, HistoryOutline,
            IdcardOutline, KeyOutline, LockOutline, MailOutline, PlusOutline, ProfileOutline, ReloadOutline,
            SafetyCertificateOutline, SafetyOutline, SaveOutline, SearchOutline, SettingOutline, SolutionOutline, StopOutline,
            SwapOutline, TeamOutline, TranslationOutline, UnlockOutline, UserAddOutline, UserOutline, UsergroupAddOutline,
            WarningOutline
        ]
    };
}
