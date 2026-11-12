const addForm = document.getElementById('add');
const tbody = document.getElementById('radars');
const statusEl = document.getElementById('status');
const loadEl = document.getElementById('load');

const rate = (r) => ((r.minPlots + r.maxPlots) / 2) / (r.rotationPeriodMs / 1000);

async function refresh() {
  try {
    const radars = await api.get('/api/v1/radars');
    tbody.replaceChildren(...(radars.length
      ? radars.map(renderRow)
      : [el('tr', {}, el('td', { colspan: '8', class: 'empty' }, 'Henüz radar yok.'))]));
    const running = radars.filter((r) => r.running);
    const total = running.reduce((sum, r) => sum + rate(r), 0);
    loadEl.textContent = radars.length
      ? `— ${running.length}/${radars.length} çalışıyor, toplam ≈ ${total.toFixed(1)} plot/sn`
      : '';
  } catch (e) {
    setStatus(statusEl, 'Radarlar alınamadı: ' + e.message, 'error');
  }
}

/** Butonu işlem süresince kilitler, hatayı durum satırına yazar. */
function action(button, run, okMessage, errPrefix) {
  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      await run();
      setStatus(statusEl, okMessage, 'ok');
      await refresh();
    } catch (e) {
      setStatus(statusEl, `${errPrefix}: ${e.message}`, 'error');
      button.disabled = false;
    }
  });
}

function renderRow(r) {
  const id = encodeURIComponent(r.radarId);
  const inputs = {
    rotationPeriodMs: el('input', { type: 'number', value: r.rotationPeriodMs, min: 100, max: 60000 }),
    minPlots: el('input', { type: 'number', value: r.minPlots, min: 0, max: 500 }),
    maxPlots: el('input', { type: 'number', value: r.maxPlots, min: 0, max: 500 }),
  };

  // Uygula: yalnızca satırdaki değerler değiştiyse etkin
  const apply = el('button', { type: 'button', class: 'secondary', disabled: '', title: 'Değiştirilen periyot / plot değerlerini radara gönderir' }, 'Uygula');
  const dirty = () => Object.entries(inputs).some(([k, input]) => Number(input.value) !== r[k]);
  Object.entries(inputs).forEach(([key, input]) => input.addEventListener('input', () => {
    input.classList.toggle('changed', Number(input.value) !== r[key]);
    apply.disabled = !dirty();
  }));
  action(apply, () => api.put('/api/v1/radars/' + id, {
    rotationPeriodMs: Number(inputs.rotationPeriodMs.value),
    minPlots: Number(inputs.minPlots.value),
    maxPlots: Number(inputs.maxPlots.value),
  }), `${r.radarId} ayarları güncellendi.`, `${r.radarId} güncellenemedi`);

  const toggle = el('button', { type: 'button', class: 'secondary' }, r.running ? 'Durdur' : 'Başlat');
  action(toggle, () => api.post(`/api/v1/radars/${id}/${r.running ? 'stop' : 'start'}`),
    `${r.radarId} ${r.running ? 'durduruldu' : 'başlatıldı'}.`,
    `${r.radarId} ${r.running ? 'durdurulamadı' : 'başlatılamadı'}`);

  // Sil: tarayıcı confirm() yerine iki adımlı onay (ilk tık onay ister, 3 sn içinde ikinci tık siler)
  const remove = el('button', { type: 'button', class: 'danger' }, 'Sil');
  let armed = null;
  remove.addEventListener('click', async () => {
    if (!armed) {
      remove.textContent = 'Emin misiniz?';
      armed = setTimeout(() => { armed = null; remove.textContent = 'Sil'; }, 3000);
      return;
    }
    clearTimeout(armed);
    armed = null;
    remove.disabled = true;
    try {
      await api.del('/api/v1/radars/' + id);
      setStatus(statusEl, `${r.radarId} silindi.`, 'ok');
      await refresh();
    } catch (e) {
      setStatus(statusEl, `${r.radarId} silinemedi: ${e.message}`, 'error');
      remove.disabled = false;
      remove.textContent = 'Sil';
    }
  });

  return el('tr', {},
    el('td', {}, el('strong', {}, r.radarId)),
    el('td', {}, r.radarType),
    el('td', {}, el('span', { class: 'state ' + (r.running ? 'on' : 'off') }, r.running ? 'Çalışıyor' : 'Durdu')),
    el('td', {}, inputs.rotationPeriodMs),
    el('td', {}, inputs.minPlots),
    el('td', {}, inputs.maxPlots),
    el('td', { class: 'mono' }, rate(r).toFixed(1)),
    el('td', {}, el('div', { class: 'actions' }, apply, toggle, remove)),
  );
}

addForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const d = new FormData(addForm);
  const body = {
    radarId: d.get('radarId').trim(),
    radarType: d.get('radarType').trim(),
    rotationPeriodMs: Number(d.get('rotationPeriodMs')),
    minPlots: Number(d.get('minPlots')),
    maxPlots: Number(d.get('maxPlots')),
  };
  try {
    await api.post('/api/v1/radars', body);
    setStatus(statusEl, `${body.radarId} eklendi ve başlatıldı.`, 'ok');
    addForm.elements.radarId.value = '';
    refresh();
  } catch (err) {
    setStatus(statusEl, 'Radar eklenemedi: ' + err.message, 'error');
  }
});

refresh();
