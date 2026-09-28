import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, forkJoin } from 'rxjs';
import { map, tap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { PrimeNG } from 'primeng/config';
import { NzI18nService, ar_EG, en_US } from 'ng-zorro-antd/i18n';
import { NzConfigService } from 'ng-zorro-antd/core/config';
import { primengTranslation } from './primeng-translation';

export interface Language {
  code: string;
  name: string;
  flag: string;
  direction: 'ltr' | 'rtl';
}

@Injectable({
  providedIn: 'root'
})
export class TranslationService {
  private currentLanguageSubject = new BehaviorSubject<string>('en');
  public currentLanguage$ = this.currentLanguageSubject.asObservable();

  private initializedSubject = new BehaviorSubject<boolean>(false);
  public initialized$ = this.initializedSubject.asObservable();

  private translations: { [key: string]: { [key: string]: string } } = {};

  /**
   * The locale files are unhashed assets, so a browser that has cached one keeps serving
   * it after a deploy and any newly added key renders as its raw name. Forcing
   * revalidation costs one conditional request at startup and prevents that.
   */
  private static readonly REVALIDATE = {
    headers: { 'Cache-Control': 'no-cache', 'Pragma': 'no-cache' }
  };
  private isInitialized = false;

  private readonly availableLanguages: Language[] = [
    { code: 'en', name: 'English', flag: 'EN', direction: 'ltr' },
    { code: 'ar', name: 'العربية', flag: 'AR', direction: 'rtl' }
  ];

  constructor(
    private http: HttpClient,
    private primeng: PrimeNG,
    private nzI18n: NzI18nService,
    private nzConfig: NzConfigService
  ) {
    // Initialization is now handled via APP_INITIALIZER
  }

  initialize(): Promise<boolean> {
    return new Promise((resolve) => {
      // Load translations from JSON files
      forkJoin({
        en: this.http.get<any>('assets/i18n/en.json', TranslationService.REVALIDATE).pipe(
          catchError(err => { console.error('[TranslationService] Error loading en.json:', err); return of(null); })
        ),
        ar: this.http.get<any>('assets/i18n/ar.json', TranslationService.REVALIDATE).pipe(
          catchError(err => { console.error('[TranslationService] Error loading ar.json:', err); return of(null); })
        )
      }).pipe(
        map(translations => {
          const result: { [key: string]: { [key: string]: string } } = { en: {}, ar: {} };
          if (translations['en']) {
            result['en'] = this.flattenObject(translations['en']);
          }
          if (translations['ar']) {
            result['ar'] = this.flattenObject(translations['ar']);
          }
          return result;
        }),
        tap(flatTranslations => {
          const hardcoded = this.getHardcodedTranslations();
          this.translations = {
            en: { ...hardcoded['en'], ...(flatTranslations['en'] || {}) },
            ar: { ...hardcoded['ar'], ...(flatTranslations['ar'] || {}) }
          };
          this.isInitialized = true;
          this.initializedSubject.next(true);
          this.loadSavedLanguage();
        })
      ).subscribe({
        next: () => resolve(true),
        error: (error) => {
          console.error('[TranslationService] Init failed, using fallback:', error);
          // Fallback to hardcoded translations if JSON loading fails
          this.translations = this.getHardcodedTranslations();
          this.isInitialized = true;
          this.initializedSubject.next(true);
          // Load saved language after translations are initialized
          this.loadSavedLanguage();
          resolve(true);
        }
      });
    });
  }

  private flattenObject(obj: any, prefix: string = '', result: { [key: string]: string } = {}): { [key: string]: string } {
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        // Skip the "dir" property as it's metadata, not a translation key
        if (key === 'dir') {
          continue;
        }

        const newKey = prefix ? `${prefix}.${key}` : key;
        if (typeof obj[key] === 'object' && obj[key] !== null && !Array.isArray(obj[key])) {
          this.flattenObject(obj[key], newKey, result);
        } else {
          // Only add string values, skip null/undefined
          if (obj[key] != null) {
            result[newKey] = String(obj[key]);
          }
        }
      }
    }
    return result;
  }

  private getHardcodedTranslations(): { [key: string]: { [key: string]: string } } {
    return {
      en: {
        // Primary translations live in assets/i18n/*.json.
        // Below: chart/report fallbacks only (templates use top-level keys, JSON nests under REPORTS.*).

        // Student Extension actions (fallback)
        'STUDENT_EXTENSION.ACTIONS.ADD': 'Add Extension',
        'STUDENT_EXTENSION.ACTIONS.VIEW': 'View Extension',
        'STUDENT_EXTENSION.ACTIONS.EDIT': 'Edit Extension',
        'STUDENT_EXTENSION.ACTIONS.DELETE': 'Delete Extension',

        // Line charts (fallback)
        'LINE_CHARTS.TITLE': 'Line Charts',
        'LINE_CHARTS.SUBTITLE': 'Track enrollment, financial, and academic trends over time',
        'LINE_CHARTS.SUMMARY.ENROLLMENT_TOTAL': 'Total Students',
        'LINE_CHARTS.SUMMARY.ENROLLMENT_SPAN': 'Years Covered',
        'LINE_CHARTS.SUMMARY.ENROLLMENT_GROWTH': 'Growth Rate',
        'LINE_CHARTS.SUMMARY.PAYMENT_TOTAL': 'Total Disbursed',
        'LINE_CHARTS.SUMMARY.PAYMENT_AVG': 'Average Disbursement',
        'LINE_CHARTS.SUMMARY.PAYMENT_TRANSACTIONS': 'Payments Recorded',
        'LINE_CHARTS.SUMMARY.ACADEMIC_LEVEL': 'Average Level',
        'LINE_CHARTS.SUMMARY.ACADEMIC_ACTIVE': 'Active Students',
        'LINE_CHARTS.SUMMARY.ACADEMIC_NEW': 'New Students',
        'LINE_CHARTS.SUMMARY.QUARTERLY_TOTAL': 'Quarterly Disbursed',
        'LINE_CHARTS.SUMMARY.QUARTERLY_AVG': 'Avg. Disbursement',
        'LINE_CHARTS.SUMMARY.QUARTERLY_TRANSACTIONS': 'Quarter Payments',
        'LINE_CHARTS.SUMMARY.RETENTION_ACTIVE': 'Retained Students',
        'LINE_CHARTS.SUMMARY.RETENTION_SUSPENDED': 'Suspended',
        'LINE_CHARTS.SUMMARY.RETENTION_WITHDRAWN': 'Withdrawn',
        'LINE_CHARTS.SUMMARY.RETENTION_OTHER': 'At-risk Students',
        'LINE_CHARTS.CHARTS.ENROLLMENT_TITLE': 'Enrollment Growth Trend',
        'LINE_CHARTS.CHARTS.ENROLLMENT_DESCRIPTION': 'Student enrollment growth by delegation decision year with active and graduated student counts.',
        'LINE_CHARTS.CHARTS.PAYMENT_TITLE': 'Payment Patterns Trend',
        'LINE_CHARTS.CHARTS.PAYMENT_DESCRIPTION': 'Total and average disbursement amounts with payment volume across financial years.',
        'LINE_CHARTS.CHARTS.ACADEMIC_TITLE': 'Academic Progress Trend',
        'LINE_CHARTS.CHARTS.ACADEMIC_DESCRIPTION': 'Average academic level with active and graduated student distribution over time.',
        'LINE_CHARTS.CHARTS.QUARTERLY_TITLE': 'Quarterly Disbursement Trend',
        'LINE_CHARTS.CHARTS.QUARTERLY_DESCRIPTION': 'Quarter-over-quarter disbursement totals, averages, and payment volume.',
        'LINE_CHARTS.CHARTS.RETENTION_TITLE': 'Student Retention Trend',
        'LINE_CHARTS.CHARTS.RETENTION_DESCRIPTION': 'Active versus suspended and withdrawn students across delegation years.',
        'LINE_CHARTS.DATASETS.ENROLLMENT.STUDENT_COUNT': 'Student Count',
        'LINE_CHARTS.DATASETS.ENROLLMENT.ACTIVE_COUNT': 'Active Students',
        'LINE_CHARTS.DATASETS.ENROLLMENT.NEW_COUNT': 'New Students',
        'LINE_CHARTS.DATASETS.PAYMENT.TOTAL_AMOUNT': 'Total Disbursed',
        'LINE_CHARTS.DATASETS.PAYMENT.AVERAGE_AMOUNT': 'Average Disbursement',
        'LINE_CHARTS.DATASETS.PAYMENT.PAYMENT_COUNT': 'Payment Volume',
        'LINE_CHARTS.DATASETS.ACADEMIC.AVERAGE_LEVEL': 'Average Level',
        'LINE_CHARTS.DATASETS.ACADEMIC.ACTIVE_COUNT': 'Active Students',
        'LINE_CHARTS.DATASETS.ACADEMIC.NEW_COUNT': 'New Students',
        'LINE_CHARTS.DATASETS.QUARTERLY.TOTAL_AMOUNT': 'Total Disbursed',
        'LINE_CHARTS.DATASETS.QUARTERLY.AVERAGE_AMOUNT': 'Average Disbursement',
        'LINE_CHARTS.DATASETS.QUARTERLY.PAYMENT_COUNT': 'Payment Count',
        'LINE_CHARTS.DATASETS.RETENTION.ACTIVE_COUNT': 'Active Students',
        'LINE_CHARTS.DATASETS.RETENTION.SUSPENDED_COUNT': 'Suspended Students',
        'LINE_CHARTS.DATASETS.RETENTION.WITHDRAWN_COUNT': 'Withdrawn Students',
        'LINE_CHARTS.ERRORS.ENROLLMENT': 'Failed to load enrollment growth data',
        'LINE_CHARTS.ERRORS.PAYMENT': 'Failed to load payment pattern data',
        'LINE_CHARTS.ERRORS.ACADEMIC': 'Failed to load academic progress data',
        'LINE_CHARTS.ERRORS.QUARTERLY': 'Failed to load quarterly disbursement data',
        'LINE_CHARTS.ERRORS.RETENTION': 'Failed to load student retention data',

        // Pie charts (fallback)
        'PIE_CHART_PAGE.TITLE': 'Pie Charts',
        'PIE_CHART_PAGE.SUBTITLE': 'Visualize distribution of students by key categories',
        'PIE_CHART_PAGE.SUMMARY.MAJOR_TITLE': 'By Major',
        'PIE_CHART_PAGE.SUMMARY.GENDER_TITLE': 'By Gender',
        'PIE_CHART_PAGE.SUMMARY.UNIVERSITY_TITLE': 'By University Type',
        'PIE_CHART_PAGE.SUMMARY.STATUS_TITLE': 'By Study Status',
        'PIE_CHART_PAGE.SUMMARY.TOP_CATEGORY': 'Top category',
        'PIE_CHART_PAGE.CHARTS.MAJOR_TITLE': 'Students by Major',
        'PIE_CHART_PAGE.CHARTS.MAJOR_DESCRIPTION': 'Distribution of enrolled students across academic majors.',
        'PIE_CHART_PAGE.CHARTS.GENDER_TITLE': 'Students by Gender',
        'PIE_CHART_PAGE.CHARTS.GENDER_DESCRIPTION': 'Male vs female distribution and other recorded genders.',
        'PIE_CHART_PAGE.CHARTS.UNIVERSITY_TITLE': 'Students by University Type',
        'PIE_CHART_PAGE.CHARTS.UNIVERSITY_DESCRIPTION': 'Scholarship distribution across public, private, and other university types.',
        'PIE_CHART_PAGE.CHARTS.STATUS_TITLE': 'Students by Study Status',
        'PIE_CHART_PAGE.CHARTS.STATUS_DESCRIPTION': 'Breakdown of active, graduated, suspended, and withdrawn students.',
        'PIE_CHART_PAGE.DATASETS.MAJOR': 'Students',
        'PIE_CHART_PAGE.DATASETS.GENDER': 'Students',
        'PIE_CHART_PAGE.DATASETS.UNIVERSITY': 'Students',
        'PIE_CHART_PAGE.DATASETS.STATUS': 'Students',
        'PIE_CHART_PAGE.ERRORS.MAJOR': 'Failed to load major distribution data',
        'PIE_CHART_PAGE.ERRORS.GENDER': 'Failed to load gender distribution data',
        'PIE_CHART_PAGE.ERRORS.UNIVERSITY': 'Failed to load university type distribution data',
        'PIE_CHART_PAGE.ERRORS.STATUS': 'Failed to load study status distribution data',

        // Hierarchical charts (fallback)
        'HIERARCHY_CHARTS.TITLE': 'Hierarchical Charts',
        'HIERARCHY_CHARTS.SUBTITLE': 'Explore student distribution across layered structures',
        'HIERARCHY_CHARTS.SUMMARY.ORGANIZATION_TITLE': 'By Agency & Ministry',
        'HIERARCHY_CHARTS.SUMMARY.LOCATION_TITLE': 'By Country & Governorate',
        'HIERARCHY_CHARTS.SUMMARY.ACADEMIC_TITLE': 'By Major & Status',
        'HIERARCHY_CHARTS.SUMMARY.TOP_SEGMENT': 'Top segment',
        'HIERARCHY_CHARTS.SECTIONS.ORGANIZATION_TITLE': 'Organizational Structure',
        'HIERARCHY_CHARTS.SECTIONS.ORGANIZATION_DESCRIPTION': 'Agency and ministry hierarchy mapped to university types.',
        'HIERARCHY_CHARTS.SECTIONS.LOCATION_TITLE': 'Geographic Distribution',
        'HIERARCHY_CHARTS.SECTIONS.LOCATION_DESCRIPTION': 'Country to governorate breakdown of enrolled students.',
        'HIERARCHY_CHARTS.SECTIONS.ACADEMIC_TITLE': 'Academic Breakdown',
        'HIERARCHY_CHARTS.SECTIONS.ACADEMIC_DESCRIPTION': 'Major, study status, and gender hierarchy for students.',
        'HIERARCHY_CHARTS.ERRORS.ORGANIZATION': 'Failed to load organizational hierarchy data',
        'HIERARCHY_CHARTS.ERRORS.LOCATION': 'Failed to load location hierarchy data',
        'HIERARCHY_CHARTS.ERRORS.ACADEMIC': 'Failed to load academic hierarchy data',

        // Geographic maps (fallback)
        'GEOGRAPHIC_MAPS.TITLE': 'Geographic Maps',
        'GEOGRAPHIC_MAPS.SUBTITLE': 'Visualize student distribution across countries, governorates, and municipalities',
        'GEOGRAPHIC_MAPS.SUMMARY.TOTAL_STUDENTS': 'Total Students',
        'GEOGRAPHIC_MAPS.SUMMARY.COUNTRY_COUNT': 'Countries Covered',
        'GEOGRAPHIC_MAPS.SUMMARY.TOP_COUNTRY': 'Top Country',
        'GEOGRAPHIC_MAPS.SUMMARY.TOP_COUNTRY_STUDENTS': 'Students',
        'GEOGRAPHIC_MAPS.PANELS.COUNTRY_HEADER': 'Countries',
        'GEOGRAPHIC_MAPS.PANELS.GOVERNORATE_HEADER': 'Governorates',
        'GEOGRAPHIC_MAPS.PANELS.MUNICIPALITY_HEADER': 'Municipalities',
        'GEOGRAPHIC_MAPS.PANELS.SELECT_COUNTRY': 'Select country',
        'GEOGRAPHIC_MAPS.POPUP.STUDENTS': 'Students',
        'GEOGRAPHIC_MAPS.ERRORS.LOAD': 'Failed to load geographic distribution data',

        // Comprehensive dashboards (fallback)
        'COMPREHENSIVE_DASHBOARDS.TITLE': 'Comprehensive Dashboards',
        'COMPREHENSIVE_DASHBOARDS.SUBTITLE': 'Interactive dashboards combining student and financial intelligence',
        'COMPREHENSIVE_DASHBOARDS.METRICS.TOTAL_STUDENTS': 'Total Students',
        'COMPREHENSIVE_DASHBOARDS.METRICS.ACTIVE_STUDENTS': 'Active Students',
        'COMPREHENSIVE_DASHBOARDS.METRICS.NEW_STUDENTS': 'New Students',
        'COMPREHENSIVE_DASHBOARDS.METRICS.SUSPENDED_STUDENTS': 'Suspended Students',
        'COMPREHENSIVE_DASHBOARDS.METRICS.TOTAL_DISBURSED': 'Total Disbursed (USD)',
        'COMPREHENSIVE_DASHBOARDS.METRICS.AVERAGE_DISBURSED': 'Average Disbursement (USD)',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.ENROLLMENT_TITLE': 'Enrollment Momentum',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.ENROLLMENT_DESCRIPTION': 'Delegation decision year trend for scholarship enrollment.',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.ENROLLMENT_LABEL': 'Students',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.FINANCIAL_TITLE': 'Financial Disbursement',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.FINANCIAL_DESCRIPTION': 'Annual totals for disbursed scholarship amounts.',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.FINANCIAL_LABEL': 'Amount (USD)',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.TOP_COUNTRIES_TITLE': 'Top Countries',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.TOP_COUNTRIES_DESCRIPTION': 'Leading study destinations by scholarship count.',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.TOP_COUNTRIES_LABEL': 'Students',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.TITLE': 'Key Insights',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.ACTIVE_RATIO': 'Active student ratio',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.NEW_RATIO': 'New student ratio',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.SUSPENDED_RATIO': 'Suspension ratio',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.AVG_DISBURSEMENT': 'Average disbursement',
        'COMPREHENSIVE_DASHBOARDS.ERRORS.LOAD': 'Failed to load dashboard overview data',

        // Student Add — residence & contact step
        'STUDENT_ADD.RESIDENCE_CONTACT_INFO': 'Residence & Contact',
        'STUDENT_ADD.RESIDENCE_CONTACT_HINT': 'Enter residence address and contact details'
      },
      ar: {
        // Common
        'COMMON.SEARCH': 'بحث...',
        'COMMON.EDIT': 'تعديل',
        'COMMON.DELETE': 'حذف',
        'COMMON.NO': 'لا',
        'COMMON.YES': 'نعم',
        'COMMON.CALENDAR': 'التقويم',
        'COMMON.PROFILE': 'الملف الشخصي',
        'COMMON.SETTINGS': 'الإعدادات',

        // Student Extension actions (fallback)
        'STUDENT_EXTENSION.ACTIONS.ADD': 'إضافة تمديد',
        'STUDENT_EXTENSION.ACTIONS.VIEW': 'عرض التمديد',
        'STUDENT_EXTENSION.ACTIONS.EDIT': 'تعديل التمديد',
        'STUDENT_EXTENSION.ACTIONS.DELETE': 'حذف التمديد',

        // Navigation
        'NAVIGATION.HOME': 'الرئيسية',
        'NAVIGATION.DASHBOARD': 'لوحة التحكم',
        'NAVIGATION.STUDENTS': 'الطلاب',
        'NAVIGATION.STUDENT_HISTORY': 'تاريخ الطلاب',
        'NAVIGATION.FINANCE': 'المالية',
        'NAVIGATION.FINANCE_HISTORY': 'تاريخ المالية',
        'NAVIGATION.UNIVERSITY_FEE': 'رسوم الجامعة',
        'NAVIGATION.SYSTEM_ADMINISTRATION': 'إدارة النظام',
        'NAVIGATION.LOOKUP_TABLES': 'تراميز النظام',
        'NAVIGATION.COUNTRY_MANAGEMENT': 'إدارة البلدان',
        'NAVIGATION.GOVERNMENT_MANAGEMENT': 'إدارة الحكومة',
        'NAVIGATION.MUNICIPALITY_MANAGEMENT': 'إدارة البلدية',
        'NAVIGATION.REPORTS': 'التقارير',
        'NAVIGATION.REPORTS_LIST': 'التقارير',
        'NAVIGATION.GRAPHS': 'الرسوم البيانية',
        'NAVIGATION.AUDIT_LOGS': 'سجلات التدقيق',

        // Students
        'STUDENTS.TITLE': 'الطلاب',
        'STUDENTS.NEW_STUDENT': 'طالب جديد',
        'STUDENTS.DELETE_SELECTED': 'حذف المحدد',
        'STUDENTS.ID': 'الرقم',
        'STUDENTS.NAME': 'الاسم',
        'STUDENTS.PASSPORT': 'جواز السفر',
        'STUDENTS.MAJOR': 'التخصص',
        'STUDENTS.DEGREE': 'الدرجة العلمية',
        'STUDENTS.STATUS': 'الحالة',
        'STUDENTS.ACTIONS': 'الإجراءات',
        'STUDENTS.ACTIVE': 'نشط',
        'STUDENTS.INACTIVE': 'غير نشط',
        'STUDENTS.CONFIRM_DELETE': 'هل أنت متأكد من حذف',
        'STUDENTS.CONFIRM_DELETE_SELECTED': 'هل أنت متأكد من حذف الطلاب المحددين؟',
        'STUDENTS.LOADING': 'جاري التحميل...',
        'STUDENTS.NO_STUDENTS': 'لم يتم العثور على طلاب',
        'STUDENTS.VIEW_DETAILS': 'عرض تفاصيل الطالب',

        // Student Add Form
        'STUDENT_ADD.TITLE': 'إضافة طالب جديد',
        'STUDENT_ADD.EDIT_TITLE': 'تعديل الطالب',
        'STUDENT_ADD.PERSONAL_INFO': 'المعلومات الشخصية',
        'STUDENT_ADD.STUDENT_PICTURE': 'صورة الطالب',
        'STUDENT_ADD.ACADEMIC_INFO': 'المعلومات الأكاديمية',
        'STUDENT_ADD.STUDY_DETAILS': 'تفاصيل الدراسة',
        'STUDENT_ADD.FINANCIAL_INFO': 'المعلومات المالية',
        'STUDENT_ADD.DOCUMENTS_STATUS': 'الوثائق والحالة',
        'STUDENT_ADD.UPLOAD_DOCUMENTS': 'رفع الوثائق',
        'STUDENT_ADD.STUDENT_NAME': 'اسم الطالب',
        'STUDENT_ADD.GENDER': 'الجنس',
        'STUDENT_ADD.SELECT_GENDER': 'اختر الجنس',
        'STUDENT_ADD.MALE': 'ذكر',
        'STUDENT_ADD.FEMALE': 'أنثى',
        'STUDENT_ADD.BIRTH_PLACE': 'مكان الميلاد',
        'STUDENT_ADD.PASSPORT_ISSUE_DATE': 'تاريخ إصدار جواز السفر',
        'STUDENT_ADD.STUDY_YEARS': 'سنوات الدراسة',
        'STUDENT_ADD.CURRENT_LEVEL': 'المستوى الحالي',
        'STUDENT_ADD.STUDY_STATUS': 'حالة الدراسة',
        'STUDENT_ADD.STUDY_TYPE': 'نوع الدراسة',
        'STUDENT_ADD.ACTIVE': 'نشط',
        'STUDENT_ADD.INACTIVE': 'غير نشط',
        'STUDENT_ADD.REQUIRED': 'مطلوب',
        'STUDENT_ADD.SUCCESS': 'تم إضافة الطالب بنجاح',
        'STUDENT_ADD.ERROR': 'فشل في إضافة الطالب',
        'STUDENT_ADD.DOCUMENT_PASSPORT': 'جواز السفر',
        'STUDENT_ADD.DOCUMENT_ACADEMIC_CERT': 'الشهادة الأكاديمية',
        'STUDENT_ADD.DOCUMENT_BIRTH_CERT': 'شهادة الميلاد',
        'STUDENT_ADD.DOCUMENT_VISA': 'التأشيرة',
        'STUDENT_ADD.DOCUMENT_OTHER': 'أخرى',

        // New lookup field labels
        'STUDENT_ADD.COUNTRY': 'البلد',
        'STUDENT_ADD.PASSPORT_ISSUE_COUNTRY': 'بلد إصدار جواز السفر',
        'STUDENT_ADD.STUDENT_DEGREE': 'درجة الطالب',
        'STUDENT_ADD.UNIVERSITY_TYPE': 'نوع الجامعة',

        // Select placeholders
        'STUDENT_ADD.SELECT_COUNTRY': 'اختر البلد',
        'STUDENT_ADD.SELECT_PASSPORT_ISSUE_COUNTRY': 'اختر بلد إصدار جواز السفر',
        'STUDENT_ADD.SELECT_STUDENT_DEGREE': 'اختر درجة الطالب',
        'STUDENT_ADD.SELECT_CURRENT_LEVEL': 'اختر المستوى الحالي',
        'STUDENT_ADD.SELECT_STUDY_STATUS': 'اختر حالة الدراسة',
        'STUDENT_ADD.SELECT_STUDY_TYPE': 'اختر نوع الدراسة',
        'STUDENT_ADD.SELECT_UNIVERSITY_TYPE': 'اختر نوع الجامعة',

        // Additional fields
        'STUDENT_ADD.STUDENT_COUNTRY': 'بلد الطالب',
        'STUDENT_ADD.STUDENT_MAJOR': 'تخصص الطالب',
        'STUDENT_ADD.AGENCY_TYPE': 'نوع الوكالة',
        'STUDENT_ADD.MINISTRY_TYPE': 'نوع الوزارة',
        'STUDENT_ADD.GOVERNMENT': 'المحافظة',
        'STUDENT_ADD.MUNICIPALITY': 'البلدية',
        'STUDENT_ADD.STUDY_LOCATION_INFO': 'معلومات موقع الدراسة',
        'STUDENT_ADD.STUDY_COUNTRY_IN': 'بلد الدراسة',
        'STUDENT_ADD.STUDY_GOV_IN': 'محافظة الدراسة',
        'STUDENT_ADD.STUDY_UNIVERSITY_IN': 'جامعة الدراسة',
        'STUDENT_ADD.STUDY_COLLEGE_IN': 'كلية الدراسة',
        'STUDENT_ADD.STUDY_EDUCATION_IN': 'القسم الأكاديمي',
        'STUDENT_ADD.STUDY_MAJOR_IN': 'البرنامج الدراسي في الجامعة',
        'STUDENT_ADD.DELEGATION_DECISION_YEAR': 'سنة قرار الوفد',
        'STUDENT_ADD.DELEGATE_DECISION_NO': 'رقم قرار المندوب',
        'STUDENT_ADD.FIRST_RECEIPT_OF_DUE': 'أول إيصال مستحق',
        'STUDENT_ADD.AMOUNT': 'المبلغ',
        'STUDENT_ADD.UNIVERSITY_FEE': 'رسوم الجامعة',
        'STUDENT_ADD.UNIVERSITY_FEE_STATUS': 'حالة رسوم الجامعة',
        'STUDENT_ADD.NOTE_1': 'ملاحظة 1',
        'STUDENT_ADD.NOTE_2': 'ملاحظة 2',
        'STUDENT_ADD.ADDED_QUARTER': 'الربع المضاف',
        'STUDENT_ADD.LAST_QUARTER': 'الربع الأخير',

        // Additional select placeholders
        'STUDENT_ADD.SELECT_BIRTH_PLACE': 'اختر مكان الميلاد',
        'STUDENT_ADD.SELECT_STUDENT_COUNTRY': 'اختر بلد الطالب',
        'STUDENT_ADD.SELECT_STUDENT_MAJOR': 'اختر تخصص الطالب',
        'STUDENT_ADD.SELECT_AGENCY_TYPE': 'اختر نوع الوكالة',
        'STUDENT_ADD.SELECT_MINISTRY_TYPE': 'اختر نوع الوزارة',
        'STUDENT_ADD.SELECT_GOVERNMENT': 'اختر المحافظة',
        'STUDENT_ADD.SELECT_MUNICIPALITY': 'اختر البلدية',
        'STUDENT_ADD.SELECT_STUDY_COUNTRY_IN': 'اختر بلد الدراسة',
        'STUDENT_ADD.SELECT_STUDY_GOV_IN': 'اختر محافظة الدراسة',
        'STUDENT_ADD.SELECT_STUDY_UNIVERSITY_IN': 'اختر جامعة الدراسة',
        'STUDENT_ADD.SELECT_STUDY_COLLEGE_IN': 'اختر كلية الدراسة',
        'STUDENT_ADD.SELECT_STUDY_EDUCATION_IN': 'اختر القسم الأكاديمي',
        'STUDENT_ADD.SELECT_STUDY_MAJOR_IN': 'اختر البرنامج الدراسي',
        'STUDENT_ADD.SELECT_YES_NO': 'اختر نعم/لا',
        'STUDENT_ADD.RESIDENCE_CONTACT_INFO': 'الإقامة والتواصل',
        'STUDENT_ADD.RESIDENCE_CONTACT_HINT': 'أدخل عنوان الإقامة وبيانات التواصل',

        // Line charts (fallback)
        'LINE_CHARTS.TITLE': 'مخططات الخطوط',
        'LINE_CHARTS.SUBTITLE': 'تتبّع اتجاهات التسجيل والمالية والتقدم الأكاديمي عبر الزمن',
        'LINE_CHARTS.SUMMARY.ENROLLMENT_TOTAL': 'إجمالي الطلاب',
        'LINE_CHARTS.SUMMARY.ENROLLMENT_SPAN': 'السنوات المغطاة',
        'LINE_CHARTS.SUMMARY.ENROLLMENT_GROWTH': 'معدل النمو',
        'LINE_CHARTS.SUMMARY.PAYMENT_TOTAL': 'إجمالي الصرف',
        'LINE_CHARTS.SUMMARY.PAYMENT_AVG': 'متوسط الصرف',
        'LINE_CHARTS.SUMMARY.PAYMENT_TRANSACTIONS': 'عدد الدفعات المسجلة',
        'LINE_CHARTS.SUMMARY.ACADEMIC_LEVEL': 'متوسط المستوى',
        'LINE_CHARTS.SUMMARY.ACADEMIC_ACTIVE': 'الطلاب النشطون',
        'LINE_CHARTS.SUMMARY.ACADEMIC_NEW': 'الطلاب الجدد',
        'LINE_CHARTS.SUMMARY.QUARTERLY_TOTAL': 'إجمالي الصرف الفصلي',
        'LINE_CHARTS.SUMMARY.QUARTERLY_AVG': 'متوسط الصرف',
        'LINE_CHARTS.SUMMARY.QUARTERLY_TRANSACTIONS': 'دفعات كل ربع',
        'LINE_CHARTS.SUMMARY.RETENTION_ACTIVE': 'الطلاب المحتفظ بهم',
        'LINE_CHARTS.SUMMARY.RETENTION_SUSPENDED': 'الموقوفون',
        'LINE_CHARTS.SUMMARY.RETENTION_WITHDRAWN': 'المنسحبون',
        'LINE_CHARTS.SUMMARY.RETENTION_OTHER': 'الطلاب المعرضون للخطر',
        'LINE_CHARTS.CHARTS.ENROLLMENT_TITLE': 'اتجاه نمو التسجيل',
        'LINE_CHARTS.CHARTS.ENROLLMENT_DESCRIPTION': 'نمو تسجيل الطلاب حسب سنة قرار الإيفاد مع عدد الطلاب النشطين والمتخرجين.',
        'LINE_CHARTS.CHARTS.PAYMENT_TITLE': 'اتجاه أنماط المدفوعات',
        'LINE_CHARTS.CHARTS.PAYMENT_DESCRIPTION': 'إجمالي ومتوسط مبالغ الصرف مع حجم الدفعات عبر السنوات المالية.',
        'LINE_CHARTS.CHARTS.ACADEMIC_TITLE': 'اتجاه التقدم الأكاديمي',
        'LINE_CHARTS.CHARTS.ACADEMIC_DESCRIPTION': 'متوسط المستوى الأكاديمي مع توزيع الطلاب النشطين والمتخرجين عبر الزمن.',
        'LINE_CHARTS.CHARTS.QUARTERLY_TITLE': 'اتجاه الصرف الفصلي',
        'LINE_CHARTS.CHARTS.QUARTERLY_DESCRIPTION': 'إجمالي ومتوسط مبالغ الصرف مع حجم الدفعات لكل ربع زمني.',
        'LINE_CHARTS.CHARTS.RETENTION_TITLE': 'اتجاه الاحتفاظ بالطلاب',
        'LINE_CHARTS.CHARTS.RETENTION_DESCRIPTION': 'مقارنة الطلاب النشطين بالموقوفين والمنسحبين عبر سنوات الإيفاد.',
        'LINE_CHARTS.DATASETS.ENROLLMENT.STUDENT_COUNT': 'عدد الطلاب',
        'LINE_CHARTS.DATASETS.ENROLLMENT.ACTIVE_COUNT': 'الطلاب النشطون',
        'LINE_CHARTS.DATASETS.ENROLLMENT.NEW_COUNT': 'الطلاب الجدد',
        'LINE_CHARTS.DATASETS.PAYMENT.TOTAL_AMOUNT': 'إجمالي الصرف',
        'LINE_CHARTS.DATASETS.PAYMENT.AVERAGE_AMOUNT': 'متوسط الصرف',
        'LINE_CHARTS.DATASETS.PAYMENT.PAYMENT_COUNT': 'حجم الدفعات',
        'LINE_CHARTS.DATASETS.ACADEMIC.AVERAGE_LEVEL': 'متوسط المستوى',
        'LINE_CHARTS.DATASETS.ACADEMIC.ACTIVE_COUNT': 'الطلاب النشطون',
        'LINE_CHARTS.DATASETS.ACADEMIC.NEW_COUNT': 'الطلاب الجدد',
        'LINE_CHARTS.DATASETS.QUARTERLY.TOTAL_AMOUNT': 'إجمالي الصرف',
        'LINE_CHARTS.DATASETS.QUARTERLY.AVERAGE_AMOUNT': 'متوسط الصرف',
        'LINE_CHARTS.DATASETS.QUARTERLY.PAYMENT_COUNT': 'عدد الدفعات',
        'LINE_CHARTS.DATASETS.RETENTION.ACTIVE_COUNT': 'الطلاب النشطون',
        'LINE_CHARTS.DATASETS.RETENTION.SUSPENDED_COUNT': 'الطلاب الموقوفون',
        'LINE_CHARTS.DATASETS.RETENTION.WITHDRAWN_COUNT': 'الطلاب المنسحبون',
        'LINE_CHARTS.ERRORS.ENROLLMENT': 'فشل في تحميل بيانات نمو التسجيل',
        'LINE_CHARTS.ERRORS.PAYMENT': 'فشل في تحميل بيانات أنماط المدفوعات',
        'LINE_CHARTS.ERRORS.ACADEMIC': 'فشل في تحميل بيانات التقدم الأكاديمي',
        'LINE_CHARTS.ERRORS.QUARTERLY': 'فشل في تحميل بيانات الصرف الفصلي',
        'LINE_CHARTS.ERRORS.RETENTION': 'فشل في تحميل بيانات الاحتفاظ بالطلاب',

        // Pie charts (fallback)
        'PIE_CHART_PAGE.TITLE': 'مخططات دائرية',
        'PIE_CHART_PAGE.SUBTITLE': 'عرض توزيع الطلاب حسب الفئات الرئيسية',
        'PIE_CHART_PAGE.SUMMARY.MAJOR_TITLE': 'حسب التخصص',
        'PIE_CHART_PAGE.SUMMARY.GENDER_TITLE': 'حسب الجنس',
        'PIE_CHART_PAGE.SUMMARY.UNIVERSITY_TITLE': 'حسب نوع الجامعة',
        'PIE_CHART_PAGE.SUMMARY.STATUS_TITLE': 'حسب حالة الدراسة',
        'PIE_CHART_PAGE.SUMMARY.TOP_CATEGORY': 'أبرز فئة',
        'PIE_CHART_PAGE.CHARTS.MAJOR_TITLE': 'الطلاب حسب التخصص',
        'PIE_CHART_PAGE.CHARTS.MAJOR_DESCRIPTION': 'توزيع الطلاب المسجلين عبر التخصصات الأكاديمية.',
        'PIE_CHART_PAGE.CHARTS.GENDER_TITLE': 'الطلاب حسب الجنس',
        'PIE_CHART_PAGE.CHARTS.GENDER_DESCRIPTION': 'توزيع الذكور والإناث وأي أجناس أخرى مسجلة.',
        'PIE_CHART_PAGE.CHARTS.UNIVERSITY_TITLE': 'الطلاب حسب نوع الجامعة',
        'PIE_CHART_PAGE.CHARTS.UNIVERSITY_DESCRIPTION': 'توزيع البعثات عبر الجامعات الحكومية والخاصة وأنواع أخرى.',
        'PIE_CHART_PAGE.CHARTS.STATUS_TITLE': 'الطلاب حسب حالة الدراسة',
        'PIE_CHART_PAGE.CHARTS.STATUS_DESCRIPTION': 'تفصيل الطلاب النشطين والمتخرجين والموقوفين والمنسحبين.',
        'PIE_CHART_PAGE.DATASETS.MAJOR': 'الطلاب',
        'PIE_CHART_PAGE.DATASETS.GENDER': 'الطلاب',
        'PIE_CHART_PAGE.DATASETS.UNIVERSITY': 'الطلاب',
        'PIE_CHART_PAGE.DATASETS.STATUS': 'الطلاب',
        'PIE_CHART_PAGE.ERRORS.MAJOR': 'فشل في تحميل بيانات توزيع التخصصات',
        'PIE_CHART_PAGE.ERRORS.GENDER': 'فشل في تحميل بيانات توزيع الجنس',
        'PIE_CHART_PAGE.ERRORS.UNIVERSITY': 'فشل في تحميل بيانات توزيع نوع الجامعة',
        'PIE_CHART_PAGE.ERRORS.STATUS': 'فشل في تحميل بيانات توزيع حالة الدراسة',

        // Hierarchical charts (fallback)
        'HIERARCHY_CHARTS.TITLE': 'الرسوم الهرمية',
        'HIERARCHY_CHARTS.SUBTITLE': 'استكشف توزيع الطلاب عبر الهياكل المتعددة المستويات',
        'HIERARCHY_CHARTS.SUMMARY.ORGANIZATION_TITLE': 'حسب الجهة والوزارة',
        'HIERARCHY_CHARTS.SUMMARY.LOCATION_TITLE': 'حسب الدولة والمحافظة',
        'HIERARCHY_CHARTS.SUMMARY.ACADEMIC_TITLE': 'حسب التخصص والحالة',
        'HIERARCHY_CHARTS.SUMMARY.TOP_SEGMENT': 'أبرز شريحة',
        'HIERARCHY_CHARTS.SECTIONS.ORGANIZATION_TITLE': 'الهيكل التنظيمي',
        'HIERARCHY_CHARTS.SECTIONS.ORGANIZATION_DESCRIPTION': 'تسلسل الجهات والوزارات المرتبط بأنواع الجامعات.',
        'HIERARCHY_CHARTS.SECTIONS.LOCATION_TITLE': 'التوزيع الجغرافي',
        'HIERARCHY_CHARTS.SECTIONS.LOCATION_DESCRIPTION': 'تفصيل الطلاب حسب الدول والمحافظات.',
        'HIERARCHY_CHARTS.SECTIONS.ACADEMIC_TITLE': 'التوزيع الأكاديمي',
        'HIERARCHY_CHARTS.SECTIONS.ACADEMIC_DESCRIPTION': 'توزيع التخصصات وحالة الدراسة والجنس للطلاب.',
        'HIERARCHY_CHARTS.ERRORS.ORGANIZATION': 'فشل في تحميل بيانات الهيكل التنظيمي',
        'HIERARCHY_CHARTS.ERRORS.LOCATION': 'فشل في تحميل بيانات التوزيع الجغرافي',
        'HIERARCHY_CHARTS.ERRORS.ACADEMIC': 'فشل في تحميل بيانات التوزيع الأكاديمي',

        // Geographic maps (fallback)
        'GEOGRAPHIC_MAPS.TITLE': 'الخرائط الجغرافية',
        'GEOGRAPHIC_MAPS.SUBTITLE': 'عرض توزيع الطلاب عبر الدول والمحافظات والبلديات',
        'GEOGRAPHIC_MAPS.SUMMARY.TOTAL_STUDENTS': 'إجمالي الطلاب',
        'GEOGRAPHIC_MAPS.SUMMARY.COUNTRY_COUNT': 'عدد الدول',
        'GEOGRAPHIC_MAPS.SUMMARY.TOP_COUNTRY': 'أبرز دولة',
        'GEOGRAPHIC_MAPS.SUMMARY.TOP_COUNTRY_STUDENTS': 'عدد الطلاب',
        'GEOGRAPHIC_MAPS.PANELS.COUNTRY_HEADER': 'الدول',
        'GEOGRAPHIC_MAPS.PANELS.GOVERNORATE_HEADER': 'المحافظات',
        'GEOGRAPHIC_MAPS.PANELS.MUNICIPALITY_HEADER': 'البلديات',
        'GEOGRAPHIC_MAPS.PANELS.SELECT_COUNTRY': 'اختر الدولة',
        'GEOGRAPHIC_MAPS.POPUP.STUDENTS': 'الطلاب',
        'GEOGRAPHIC_MAPS.ERRORS.LOAD': 'فشل في تحميل بيانات التوزيع الجغرافي',

        // Comprehensive dashboards (fallback)
        'COMPREHENSIVE_DASHBOARDS.TITLE': 'لوحات المعلومات الشاملة',
        'COMPREHENSIVE_DASHBOARDS.SUBTITLE': 'لوحات تفاعلية تجمع بين التحليل الطلابي والمالي',
        'COMPREHENSIVE_DASHBOARDS.METRICS.TOTAL_STUDENTS': 'إجمالي الطلاب',
        'COMPREHENSIVE_DASHBOARDS.METRICS.ACTIVE_STUDENTS': 'الطلاب النشطون',
        'COMPREHENSIVE_DASHBOARDS.METRICS.NEW_STUDENTS': 'الطلاب الجدد',
        'COMPREHENSIVE_DASHBOARDS.METRICS.SUSPENDED_STUDENTS': 'الطلاب الموقوفون',
        'COMPREHENSIVE_DASHBOARDS.METRICS.TOTAL_DISBURSED': 'إجمالي المصروف (دولار)',
        'COMPREHENSIVE_DASHBOARDS.METRICS.AVERAGE_DISBURSED': 'متوسط المصروف (دولار)',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.ENROLLMENT_TITLE': 'زخم التسجيل',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.ENROLLMENT_DESCRIPTION': 'اتجاه قرار الإيفاد السنوي لتسجيل المنح.',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.ENROLLMENT_LABEL': 'الطلاب',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.FINANCIAL_TITLE': 'الصرف المالي',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.FINANCIAL_DESCRIPTION': 'إجمالي المبالغ المصروفة سنوياً للمنح الدراسية.',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.FINANCIAL_LABEL': 'المبلغ (دولار)',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.TOP_COUNTRIES_TITLE': 'أهم الدول',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.TOP_COUNTRIES_DESCRIPTION': 'أبرز وجهات الدراسة حسب عدد المنح.',
        'COMPREHENSIVE_DASHBOARDS.CHARTS.TOP_COUNTRIES_LABEL': 'الطلاب',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.TITLE': 'أهم المؤشرات',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.ACTIVE_RATIO': 'نسبة الطلاب النشطين',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.NEW_RATIO': 'نسبة الطلاب الجدد',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.SUSPENDED_RATIO': 'نسبة الإيقاف',
        'COMPREHENSIVE_DASHBOARDS.INSIGHTS.AVG_DISBURSEMENT': 'متوسط الصرف',
        'COMPREHENSIVE_DASHBOARDS.ERRORS.LOAD': 'فشل في تحميل بيانات لوحة المعلومات'
      }
    };
    this.isInitialized = true;
  }

  private loadSavedLanguage(): void {
    try {
      const savedLanguage = localStorage.getItem('selectedLanguage') || 'en';
      this.setLanguage(savedLanguage);
    } catch (error) {
      this.setLanguage('en');
    }
  }

  getAvailableLanguages(): Language[] {
    return this.availableLanguages;
  }

  getCurrentLanguage(): string {
    return this.currentLanguageSubject.value;
  }

  /**
   * Handles a user-initiated language change, in place.
   *
   * This used to `window.location.reload()` — the whole app re-bootstrapped, every
   * unsaved form was lost, and a language switch on a slow line took seconds. The
   * three reasons it gave for reloading are now handled without one:
   *
   *  - NZ_I18N was resolved once by a factory at bootstrap. {@link setLanguage} now
   *    also calls NzI18nService.setLocale, which every ng-zorro widget observes.
   *  - PrimeNG's built-in strings are re-applied through setTranslation (D5), and
   *    direction is set on <body>; the 20+ components that subscribe to
   *    currentLanguage$ rebuild their options, columns and charts from it.
   *  - Data already fetched under the previous Accept-Language (lookup names, status
   *    names) is not re-fetched by this service. Components reload what they need on
   *    currentLanguage$; anything else refreshes on the next navigation.
   *
   * No consumer was found that cannot re-render, so no reload is kept.
   */
  switchLanguage(language: string): void {
    if (!this.availableLanguages.some(l => l.code === language)) {
      return;
    }
    if (this.getCurrentLanguage() === language) {
      return;
    }
    this.setLanguage(language);
  }

  setLanguage(language: string): void {
    const lang = this.availableLanguages.find(l => l.code === language);
    if (!lang) {
      return;
    }

    // Direction and lang first, so nothing that reacts to the emission below
    // reads the previous direction.
    this.updateDocumentDirection(lang.direction, language);

    // PrimeNG's own strings (datepicker, table empty-message, confirm Yes/No, file
    // upload) come from its config, not from our dictionaries, so they are switched here.
    this.primeng.setTranslation(primengTranslation(language));

    // ng-zorro reads its locale from NzI18nService, which is live; NZ_I18N alone is
    // read once at bootstrap.
    this.nzI18n.setLocale(language === 'ar' ? ar_EG : en_US);

    // ng-zorro modals are a special case. Cards, tables and inputs take their
    // direction from the CDK Directionality that the `[dir]` directive on the
    // layout wrapper provides, so they follow a switch on their own. A modal is
    // opened by NzModalService, which is a root service and asks the *root*
    // Directionality — read from document.body.dir once, at injection, before
    // any switch — unless the modal config says otherwise. This is the
    // "otherwise": every modal opened after this line, declared `<nz-modal>` or
    // `modal.confirm()`, gets the current direction.
    this.nzConfig.set('modal', { nzDirection: lang.direction });

    this.currentLanguageSubject.next(language);

    // Save to localStorage
    try {
      localStorage.setItem('selectedLanguage', language);
    } catch {
      // Storage can be unavailable (private mode, quota); the language still applies in memory.
    }
  }

  private updateDocumentDirection(direction: 'ltr' | 'rtl', language: string): void {
    try {
      const htmlElement = document.documentElement;
      // Do NOT set dir on <html> — that moves the browser's native scrollbar to the left in RTL.
      // Direction is handled by the `[dir]` directive on .layout-wrapper instead;
      // <body> carries the attribute for the `[dir="rtl"]` rules in styles.scss.
      htmlElement.setAttribute('lang', language);
      document.body.setAttribute('dir', direction);

      // Add/remove RTL class for CSS styling
      if (direction === 'rtl') {
        htmlElement.classList.add('rtl');
      } else {
        htmlElement.classList.remove('rtl');
      }
    } catch {
      // No document to mirror (server rendering, tests); nothing to do.
    }
  }

  translate(key: string): string {
    if (!this.isInitialized) {
      return key; // Return key if not initialized
    }

    const currentLang = this.getCurrentLanguage();

    if (!this.translations[currentLang]) {
      return key;
    }

    const translation = this.translations[currentLang][key];
    if (translation && translation !== key) {
      return translation;
    }
    // Fallback to English for missing keys so UI never shows raw key
    if (currentLang !== 'en' && this.translations['en'] && this.translations['en'][key]) {
      return this.translations['en'][key];
    }
    return key;
  }

  isRTL(): boolean {
    return this.getCurrentLanguage() === 'ar';
  }

  isReady(): boolean {
    return this.isInitialized;
  }


}
