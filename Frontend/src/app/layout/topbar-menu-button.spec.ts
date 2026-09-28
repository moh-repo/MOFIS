/**
 * Guards where the topbar's hamburger sits, in both directions.
 *
 * The bar used to be mirrored twice for RTL: `flex-direction: row-reverse` plus
 * an explicit `order` on every child, and — because the rules were spread over
 * _topbar.scss and assets/styles/layout-rtl.css — an `auto` margin on both the
 * logo and the topbar menu. Two auto margins split the free space between them,
 * so in Arabic the hamburger floated in the middle of the bar instead of sitting
 * beside the logo. It is now placed with logical properties only.
 */
describe('topbar menu button placement', () => {
  let wrapper: HTMLElement;

  const build = (dir: 'ltr' | 'rtl') => {
    wrapper = document.createElement('div');
    wrapper.className = dir === 'rtl' ? 'layout-wrapper layout-static rtl' : 'layout-wrapper layout-static';
    wrapper.setAttribute('dir', dir);
    wrapper.innerHTML = `
      <div class="layout-topbar${dir === 'rtl' ? ' rtl' : ''}">
        <a class="layout-topbar-logo"><span>System</span></a>
        <button class="p-link layout-menu-button layout-topbar-button"><i class="pi pi-bars"></i></button>
        <div class="layout-topbar-menu">
          <div class="topbar-actions">
            <button class="p-link layout-topbar-button"><i class="pi pi-user"></i></button>
          </div>
        </div>
      </div>`;
    document.body.appendChild(wrapper);
  };

  afterEach(() => wrapper?.remove());

  const rem = () => parseFloat(getComputedStyle(document.documentElement).fontSize);

  const desktopOnly = () => {
    // Below 992px the logo loses its fixed width and the parts are re-ordered
    // for the mobile bar, so there is no logo/hamburger gap to measure.
    if (!window.matchMedia('(min-width: 992px)').matches) {
      pending(`viewport is ${window.innerWidth}px; desktop layout not active`);
      return false;
    }
    return true;
  };

  it('keeps the hamburger beside the logo in LTR', () => {
    if (!desktopOnly()) { return; }
    build('ltr');

    const logo = wrapper.querySelector('.layout-topbar-logo') as HTMLElement;
    const button = wrapper.querySelector('.layout-menu-button') as HTMLElement;

    const gap = button.getBoundingClientRect().left - logo.getBoundingClientRect().right;
    expect(gap).withContext(`gap was ${gap}px`).toBeCloseTo(1.5 * rem(), 0);
  });

  it('keeps the hamburger beside the logo in RTL', () => {
    if (!desktopOnly()) { return; }
    build('rtl');

    const logo = wrapper.querySelector('.layout-topbar-logo') as HTMLElement;
    const button = wrapper.querySelector('.layout-menu-button') as HTMLElement;

    const gap = logo.getBoundingClientRect().left - button.getBoundingClientRect().right;
    expect(gap).withContext(`gap was ${gap}px`).toBeCloseTo(1.5 * rem(), 0);
  });

  it('pushes the icon group to the far edge in both directions', () => {
    if (!desktopOnly()) { return; }

    (['ltr', 'rtl'] as const).forEach(dir => {
      build(dir);
      const topbar = wrapper.querySelector('.layout-topbar') as HTMLElement;
      const menu = wrapper.querySelector('.layout-topbar-menu') as HTMLElement;

      const bar = topbar.getBoundingClientRect();
      const icons = menu.getBoundingClientRect();
      // 2rem is the bar's own horizontal padding.
      const inset = dir === 'ltr' ? bar.right - icons.right : icons.left - bar.left;

      expect(inset).withContext(`${dir}: inset was ${inset}px`).toBeCloseTo(2 * rem(), 0);
      wrapper.remove();
    });
  });
});
