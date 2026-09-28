/**
 * The emblem shown when the system logo cannot be loaded.
 *
 * <p>There is one, and this is it. Until the August 2026 review there were two
 * file names — `logo-dark.svg` and `logo-white.svg` — which were byte-identical
 * copies of the same auto-traced emblem, and that trace began with an opaque
 * black rectangle covering its whole canvas. Every surface that fell back to it
 * showed the emblem inside a black box: the login page, the top bar and the
 * footer. The reviewer asked for the background to be taken away.
 *
 * <p>`MOHE_Logo.png` is the emblem the ministry uploaded through Settings —
 * the same bytes the dev database holds — on a transparent background. The
 * report masthead has always fallen back to it; now everything does, so there
 * is a single answer to "which file is the logo".
 *
 * <p>It is one file for both colour schemes because the emblem is full colour:
 * the light/dark pair of names promised a distinction the artwork never had.
 */
export const SYSTEM_LOGO_FALLBACK = 'assets/MOHE_Logo.png';
