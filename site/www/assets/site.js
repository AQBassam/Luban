// LubanTV.bassamailab.xyz: the phone menu and the copy buttons, and nothing else. No tracking, no network requests.
(function () {
  'use strict';
  // the phone menu: the button opens and closes the links; a link, Escape or a tap outside closes them.
  // Without this script the button stays hidden (CSS shows it only under .js) and the page works as before.
  document.documentElement.classList.add('js');
  const nav = document.querySelector('nav'), menu = nav && nav.querySelector('button.menu');
  if (menu) {
    const set = open => { nav.classList.toggle('open', open); menu.setAttribute('aria-expanded', String(open)); };
    // opening moves focus to the first link: the links come before the button, so Tab alone would skip them
    menu.addEventListener('click', () => { const open = !nav.classList.contains('open'); set(open); if (open) nav.querySelector('.links a').focus(); });
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
  // one polite live region tells screen readers whether a copy worked
  const say = (() => {
    const live = document.createElement('span');
    live.className = 'sr'; live.setAttribute('role', 'status'); live.setAttribute('aria-live', 'polite');
    document.body.appendChild(live);
    return msg => { live.textContent = ''; setTimeout(() => { live.textContent = msg; }, 50); };
  })();
  const FAIL = document.documentElement.lang === 'ar' ? 'انسخه يدويًا' : 'Copy it by hand';
  // when the browser refuses, select the visible text so the reader can copy it themselves
  function selectText(b) {
    const box = b.closest('.cmd, .prompt'), r = document.createRange();
    const code = box && box.querySelector('code');
    if (code) r.selectNodeContents(code);
    else if (box) { const q = box.querySelectorAll('.q'); if (q.length < 2) return; r.setStartAfter(q[0]); r.setEndBefore(q[1]); }   // the request, without its quote marks
    else return;
    const s = getSelection(); s.removeAllRanges(); s.addRange(r);
  }
  document.addEventListener('click', async (ev) => {
    const b = ev.target.closest('button.copy[data-copy]');
    if (!b) return;
    const label = b.querySelector('span');
    if (!b._was) b._was = label.textContent;
    const ok = await copy(b.dataset.copy);
    b.classList.remove('done', 'fail');
    b.classList.add(ok ? 'done' : 'fail');
    label.textContent = ok ? (b.dataset.done || '✓') : FAIL;
    say(label.textContent);
    if (!ok) selectText(b);
    clearTimeout(b._t);
    b._t = setTimeout(() => { b.classList.remove('done', 'fail'); label.textContent = b._was; }, ok ? 1800 : 5000);
  });
})();
