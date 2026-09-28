/**
 * Guards the two CSS rules the proposed sidebar cannot work without, and the
 * gate that keeps them off the classic one.
 *
 * The collapse bug this exists for: `@children` collapses a section by animating
 * the submenu's height to 0 and nothing else. `_menu.scss` only ever put
 * `overflow: hidden` on `ul ul`, because classic's root sections never collapse —
 * so the proposed layout's root submenus had nothing clipping them and every
 * collapsed section painted its rows straight over the ones below it.
 *
 * These are computed-style assertions rather than component tests because the
 * behaviour lives entirely in the stylesheet; karma loads the real one.
 */
describe('proposed sidebar section styles', () => {

  let wrapper: HTMLElement;

  /** The DOM app.menu.component.html and app.menuitem.component.ts produce. */
  const build = (sections: boolean) => {
    wrapper = document.createElement('div');
    wrapper.className = 'layout-wrapper layout-static';
    wrapper.innerHTML = `
      <div class="layout-sidebar">
        <ul class="layout-menu${sections ? ' layout-menu--sections' : ''}">
          <li class="layout-root-menuitem">
            <div class="layout-menuitem-root-text">Students</div>
            <a><i class="pi pi-fw pi-id-card layout-menuitem-icon"></i>
               <span class="layout-menuitem-text">Students</span>
               <i class="pi pi-fw pi-angle-down layout-submenu-toggler"></i></a>
            <ul style="height: 0">
              <li><a><span class="layout-menuitem-text">All Students</span></a></li>
            </ul>
          </li>
        </ul>
      </div>`;
    document.body.appendChild(wrapper);
  };

  const rootSubmenu = () =>
    wrapper.querySelector('.layout-root-menuitem > ul') as HTMLElement;

  const rootAnchor = () =>
    wrapper.querySelector('.layout-root-menuitem > a') as HTMLElement;

  afterEach(() => wrapper?.remove());

  it('clips a collapsed section so its rows cannot paint over the menu', () => {
    build(true);
    expect(getComputedStyle(rootSubmenu()).overflow).toBe('hidden');
  });

  it('keeps a collapsed section at zero height', () => {
    build(true);
    // Belt and braces: clipping is only meaningful if the box really collapses.
    expect(rootSubmenu().getBoundingClientRect().height).toBe(0);
  });

  it('shows the section header, which classic hides', () => {
    build(true);
    // The proposed rule has to out-specify `:not(.layout-root-nav-link) > a
    // { display: none }`, not merely follow it in source order.
    expect(getComputedStyle(rootAnchor()).display).toBe('flex');
  });

  it('leaves the classic sidebar untouched by both rules', () => {
    build(false);
    // Without the gate class neither rule may apply: the root anchor stays
    // hidden and the submenu keeps the browser default.
    expect(getComputedStyle(rootAnchor()).display).toBe('none');
    expect(getComputedStyle(rootSubmenu()).overflow).toBe('visible');
  });
});
