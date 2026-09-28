/**
 * Visual smoke check for the layout after the PrimeNG 17 -> 21 upgrade.
 *
 * The upgrade removed the prebuilt theme.css that defined --surface-card,
 * --text-color and the rest of the palette the layout is built on. Nothing in
 * the compiler or the unit tests could see that the chrome had lost its colours,
 * so this asserts against a real browser and leaves a screenshot behind.
 */
describe('layout smoke', () => {
  const THEME_VARS = [
    '--surface-card',
    '--surface-ground',
    '--surface-border',
    '--text-color',
    '--text-color-secondary',
    '--primary-color',
    '--border-radius'
  ];

  it('applies the theme to the login screen', () => {
    cy.viewport(1600, 900);
    cy.visit('/');

    // Wait for bootstrap and for PrimeNG to inject its generated theme.
    cy.get('body', { timeout: 30000 }).should('be.visible');
    cy.get('.p-component, input, button', { timeout: 30000 }).should('exist');

    cy.document().then((doc) => {
      const cs = getComputedStyle(doc.documentElement);

      for (const name of THEME_VARS) {
        const value = cs.getPropertyValue(name).trim();
        cy.log(`${name} = ${value || '(EMPTY)'}`);
        expect(value, `${name} should resolve`).to.not.equal('');
      }

      // PrimeNG's own generated tokens, pointed at indigo by the preset in
      // app.module.ts rather than Aura's default emerald.
      const primary = cs.getPropertyValue('--p-primary-color').trim();
      cy.log(`--p-primary-color = ${primary || '(EMPTY)'}`);
      expect(primary, '--p-primary-color should resolve').to.not.equal('');
    });

    cy.screenshot('login-screen', { capture: 'viewport' });
  });
});
