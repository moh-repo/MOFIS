/**
 * Guards the gap between the sidebar and the main content.
 *
 * The sidebar width lives in Sass ($sidebarWidth), but the main container's
 * offset is set with `!important` from assets/styles/layout-ltr.css, which is
 * plain CSS loaded separately and cannot see Sass variables. It had 300px
 * hardcoded, so when $sidebarWidth became 270px the content kept being pushed
 * 300px out and a 30px dead strip opened between the two. Both now read
 * --layout-sidebar-width, and this fails if they drift apart again.
 */
describe('sidebar / main-content gap', () => {
  let wrapper: HTMLElement;
  let sidebar: HTMLElement;
  let container: HTMLElement;

  beforeEach(() => {
    wrapper = document.createElement('div');
    wrapper.className = 'layout-wrapper layout-static';
    wrapper.innerHTML =
      '<div class="layout-sidebar"></div><div class="layout-main-container"></div>';
    document.body.appendChild(wrapper);

    sidebar = wrapper.querySelector('.layout-sidebar') as HTMLElement;
    container = wrapper.querySelector('.layout-main-container') as HTMLElement;
  });

  afterEach(() => wrapper.remove());

  it('offsets the content by exactly the sidebar width', () => {
    const declared = getComputedStyle(document.documentElement)
      .getPropertyValue('--layout-sidebar-width').trim();
    expect(declared).withContext('--layout-sidebar-width should resolve').not.toBe('');

    // Media-query independent: the rendered sidebar and the declared width are
    // the same number, so the offset built from that variable cannot overshoot.
    expect(getComputedStyle(sidebar).width).toBe(declared);
  });

  it('leaves only the intended padding between sidebar and content', () => {
    // margin-left collapses to 0 below 992px, where the sidebar is off-canvas
    // and there is no gap to measure.
    if (!window.matchMedia('(min-width: 992px)').matches) {
      pending(`viewport is ${window.innerWidth}px; desktop layout not active`);
      return;
    }

    const gap = container.getBoundingClientRect().left
      + parseFloat(getComputedStyle(container).paddingLeft)
      - sidebar.getBoundingClientRect().right;

    // 3.5rem content padding less the sidebar's own 2rem left offset = 1.5rem.
    // At the 12.6px root size that is 18.9px; the stale 300px made it 48.9px.
    const rem = parseFloat(getComputedStyle(document.documentElement).fontSize);
    expect(gap).withContext(`gap was ${gap}px (${rem}px per rem)`)
      .toBeCloseTo(1.5 * rem, 0);
  });
});

/**
 * Guards the class that replaced `@if (hasVisibleMenuItems)` around the sidebar.
 *
 * The sidebar used to be removed from the DOM when the menu had nothing to show.
 * app-menu is what decides that, so removing it destroyed the component holding
 * the permission subscription — and when permissions landed a moment later, or a
 * second user signed in, nothing was left to rebuild the tree. The element now
 * stays mounted and this rule is the only thing hiding it, so the rule has to
 * exist.
 */
describe('empty sidebar', () => {
  let wrapper: HTMLElement;

  afterEach(() => wrapper?.remove());

  it('hides a sidebar with nothing in it', () => {
    wrapper = document.createElement('div');
    wrapper.className = 'layout-wrapper layout-static';
    wrapper.innerHTML = '<div class="layout-sidebar layout-sidebar-empty"></div>';
    document.body.appendChild(wrapper);

    const sidebar = wrapper.querySelector('.layout-sidebar') as HTMLElement;
    expect(getComputedStyle(sidebar).display).toBe('none');
  });

  it('leaves a sidebar that has items alone', () => {
    wrapper = document.createElement('div');
    wrapper.className = 'layout-wrapper layout-static';
    wrapper.innerHTML = '<div class="layout-sidebar"></div>';
    document.body.appendChild(wrapper);

    const sidebar = wrapper.querySelector('.layout-sidebar') as HTMLElement;
    expect(getComputedStyle(sidebar).display).not.toBe('none');
  });
});
