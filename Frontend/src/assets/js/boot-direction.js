/*
 * Sets lang/dir on <html> before the first paint.
 *
 * Angular sets both when it bootstraps, but that is several hundred milliseconds
 * after the browser has already painted; without this the Arabic UI appears
 * left-to-right and jumps. Reading localStorage here is the only way to know the
 * language before the app exists.
 *
 * This lived inline in index.html until the Content-Security-Policy went in. An
 * inline script needs either `script-src 'unsafe-inline'`, which would undo most
 * of what the policy is for, or a sha256 hash in the policy, which silently stops
 * matching the moment anyone reformats the script — and the only symptom would be
 * the flash coming back. A file needs neither: `script-src 'self'` covers it.
 *
 * Keep it a classic (non-module, non-defer) script in <head> so it runs before
 * the body is parsed.
 */
(function () {
    try {
        var lang = localStorage.getItem('selectedLanguage') || 'en';
        var dir = lang === 'ar' ? 'rtl' : 'ltr';
        document.documentElement.setAttribute('lang', lang);
        document.documentElement.setAttribute('dir', dir);
        if (dir === 'rtl') document.documentElement.classList.add('rtl');
    } catch (e) {}
})();
