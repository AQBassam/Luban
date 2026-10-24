// LubanTV.bassamailab.xyz: the copy buttons, and nothing else. No tracking, no network requests.
(function () {
  'use strict';
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
