const form = document.getElementById('filter');
const list = document.getElementById('records');
const statusEl = document.getElementById('status');
const moreBtn = document.getElementById('more');

let anchor = null;   // { id, value } sonraki sayfa için
let loaded = 0;

function buildQuery() {
  const params = new URLSearchParams();
  const data = new FormData(form);
  for (const name of ['radarId', 'radarType', 'messageType', 'messageCode']) {
    String(data.get(name) || '')
      .split(',').map((v) => v.trim()).filter(Boolean)
      .forEach((v) => params.append(name, v));
  }
  if (data.get('direction')) params.append('direction', data.get('direction'));
  for (const name of ['actionTimeFrom', 'actionTimeTo']) {
    const v = data.get(name);
    if (v) params.append(name, new Date(v).toISOString());
  }
  params.append('size', data.get('size'));
  if (anchor) {
    params.append('anchorId', anchor.id);
    params.append('anchorValue', anchor.value);
  }
  return params;
}

async function load(reset) {
  if (reset) {
    anchor = null;
    loaded = 0;
    list.replaceChildren();
  }
  moreBtn.disabled = true;
  setStatus(statusEl, 'Yükleniyor…');
  try {
    const page = await api.get('/api/v1/radar-records?' + buildQuery());
    page.items.forEach((r) => list.append(renderRecord(r)));
    loaded += page.items.length;
    anchor = page.hasNext ? { id: page.nextAnchorId, value: page.nextAnchorValue } : null;
    moreBtn.hidden = !page.hasNext;
    setStatus(statusEl, loaded === 0 ? 'Kayıt bulunamadı.' : `${loaded} kayıt gösteriliyor${page.hasNext ? ' (devamı var)' : ''}.`);
  } catch (e) {
    setStatus(statusEl, 'Kayıtlar alınamadı: ' + e.message, 'error');
  } finally {
    moreBtn.disabled = false;
  }
}

function renderRecord(r) {
  const m = r.message;
  const panel = el('div', { class: 'record-panel', hidden: '' });
  const header = el('button', { class: 'record-header', type: 'button', 'aria-expanded': 'false' },
    el('span', { class: 'chev' }, '▶'),
    el('span', { class: 'mono' }, formatTime(r.actionTime)),
    el('span', { class: 'badge ' + m.direction }, m.direction),
    el('span', {}, el('strong', {}, m.radarId), ' · ', m.radarType),
    el('span', { class: 'hide-sm' }, m.messageName, ' (', m.messageType, ')'),
    el('span', { class: 'hide-sm mono' }, 'kod ', m.messageCode, m.subCode != null ? '/' + m.subCode : ''),
  );

  let fetched = false;
  header.addEventListener('click', async () => {
    const open = header.getAttribute('aria-expanded') === 'true';
    header.setAttribute('aria-expanded', String(!open));
    panel.hidden = open;
    if (open || fetched) return;
    fetched = true;
    panel.replaceChildren(el('div', { class: 'empty' }, 'Detay yükleniyor…'));
    try {
      panel.replaceChildren(renderDetail(await api.get('/api/v1/radar-records/' + r.id)));
    } catch (e) {
      fetched = false;
      panel.replaceChildren(el('div', { class: 'status error' }, 'Detay alınamadı: ' + e.message));
    }
  });

  return el('div', { class: 'record' }, header, panel);
}

function renderDetail(d) {
  const m = d.message;
  const meta = el('div', { class: 'meta' },
    el('div', {}, el('span', {}, 'ID: '), el('code', {}, d.id)),
    el('div', {}, el('span', {}, 'Son güncelleme: '), formatTime(d.lastUpdated)),
    el('div', {}, el('span', {}, 'Mesaj: '), `${m.messageName} / ${m.messageType}`),
    el('div', {}, el('span', {}, 'Kod: '), m.messageCode + (m.subCode != null ? ' / ' + m.subCode : '')),
  );
  const fields = d.fields.length === 0
    ? el('div', { class: 'empty' }, 'Çözülmüş alan yok.')
    : el('table', { class: 'fields' },
        el('thead', {}, el('tr', {}, el('th', {}, 'Alan'), el('th', {}, 'Değer'), el('th', {}, 'Birim'))),
        el('tbody', {}, d.fields.map((f) => el('tr', {}, el('td', {}, f.label), el('td', { class: 'mono' }, f.value), el('td', {}, f.unit || '')))),
      );
  return el('div', {}, meta, fields);
}

form.addEventListener('submit', (e) => { e.preventDefault(); load(true); });
document.getElementById('reset').addEventListener('click', () => { form.reset(); load(true); });
moreBtn.addEventListener('click', () => load(false));

load(true);
