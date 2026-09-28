import { Injectable } from '@angular/core';
import { UmsPermissionService } from './ums-permission.service';

/**
 * A card on the reports landing page or a hub, as the catalogue
 * (`UMS_Reports`) describes it: both names, both descriptions, the route and
 * what the reader must hold to see it.
 */
export interface ReportHubCard {
  nameAr?: string | null;
  nameEn?: string | null;
  descAr?: string | null;
  descEn?: string | null;
  icon: string;
  color?: string;
  route: string;
  queryParams?: Record<string, string> | null;
  pageCode?: string;
  /** Any-of: one held code shows the card. */
  permissionCodes?: string[];
}

/** The card's name in a language; the other language, then nothing, when missing. */
export function hubCardName(card: ReportHubCard, lang: string): string {
  const primary = lang === 'ar' ? card.nameAr : card.nameEn;
  const other = lang === 'ar' ? card.nameEn : card.nameAr;
  return primary || other || '';
}

/** The card's description in a language, with the same fallback. */
export function hubCardDescription(card: ReportHubCard, lang: string): string {
  const primary = lang === 'ar' ? card.descAr : card.descEn;
  const other = lang === 'ar' ? card.descEn : card.descAr;
  return primary || other || '';
}

/**
 * Returns true when the user may see a hub card (any listed permission grants access).
 */
@Injectable({ providedIn: 'root' })
export class ReportHubPermissionService {

  constructor(private umsPermission: UmsPermissionService) {}

  canShowCard(card: ReportHubCard): boolean {
    if (card.permissionCodes?.length) {
      return card.permissionCodes.some(code => this.umsPermission.canAccessPage(code));
    }
    if (card.pageCode) {
      return this.umsPermission.canAccessPage(card.pageCode);
    }
    return true;
  }

  filterCards<T extends ReportHubCard>(cards: T[]): T[] {
    return cards.filter(c => this.canShowCard(c));
  }
}
