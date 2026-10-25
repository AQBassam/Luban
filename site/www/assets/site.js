// LubanTV.bassamailab.xyz: the phone menu and the copy buttons, and nothing else. No tracking, no network requests.
(function () {
  'use strict';
  // the phone menu: the button opens and closes the links; a link, Escape or a tap outside closes them.
  // Without this script the button stays hidden (CSS shows it only under .js) and the page works as before.
  document.documentElement.classList.add('js');
  const nav = document.querySelector('nav'), menu = nav && nav.querySelector('button.menu');
  if (menu) {
    const set = open => { nav.classList.toggle('open', open); menu.setAttribute('aria-expanded', String(open)); };
    menu.addEventListener('click', () => set(!nav.classList.contains('open')));
    nav.querySelector('.links').addEventListener('click', ev => { if (ev.target.closest('a')) set(false); });
    document.addEventListener('keydown', ev => { if (ev.key === 'Escape' && nav.classList.contains('open')) { set(false); menu.focus(); } });
    document.addEventListener('click', ev => { if (!nav.contains(ev.target)) set(false); });
  }

  function fallback(text) {
    const t = document.createElement('textarea');
    t.value = text; t.setAttribute('readonly', '');
    t.style.position = 'fixed'; t.style.opacity = '0';   // CSSOM, not an inline style attribute: allowed by the CSP
    document.body.appendChild(t); t.select();
    let ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    t.remove();
    return ok;
  }
  async function copy(text) {
    try { await navigator.clipboard.writeText(text); return true; } catch (e) { return fallback(text); }
  }
  document.addEventListener('click', async (ev) => {
    const b = ev.target.closest('button.copy[data-copy]');
    if (!b) return;
    const label = b.querySelector('span'), was = label.textContent;
    if (!(await copy(b.dataset.copy))) return;
    b.classList.add('done'); label.textContent = b.dataset.done || '✓';
    clearTimeout(b._t);
    b._t = setTimeout(() => { b.classList.remove('done'); label.textContent = was; }, 1800);
  });
})();
