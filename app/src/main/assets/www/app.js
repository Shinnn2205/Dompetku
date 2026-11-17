(() => {
  'use strict';

  const STORAGE_KEY = 'dompetku_state_v1';

  const CATEGORIES = {
    expense: [
      { id: 'Makan & Minum', icon: '🍽️' },
      { id: 'Jajan & Minuman', icon: '🧋' },
      { id: 'Transportasi', icon: '🛵' },
      { id: 'Belanja Online', icon: '🛒' },
      { id: 'Tagihan & Pulsa', icon: '⚡' },
      { id: 'Hiburan & Game', icon: '🎮' },
      { id: 'Kesehatan', icon: '💊' },
      { id: 'Lainnya', icon: '📦' }
    ],
    income: [
      { id: 'Gaji / Uang Saku', icon: '💵' },
      { id: 'Bonus / Hadiah', icon: '🎁' },
      { id: 'Usaha / Freelance', icon: '💼' },
      { id: 'Lainnya', icon: '🪙' }
    ]
  };

  const DEFAULT_STATE = () => ({
    theme: 'light',
    dailyLimit: 0,
    monthlyLimit: 0,
    transactions: [],
    goals: [],
    bans: [
      {
        id: 'ban_default_1',
        name: 'Kurangi Jajan Minuman Kekinian',
        keywords: ['boba', 'kopi', 'es', 'jajan', 'snack', 'cimol', 'seblak'],
        category: 'Jajan & Minuman',
        mode: 'strict',
        dailyMax: 0,
        active: true,
        createdAt: todayISO(),
        lastBrokenDate: null
      }
    ]
  });

  let state = loadState();
  let currentFilter = 'all';
  let pendingTx = null;
  let pendingConfirmAction = null;

  // --- Utilities ---
  function todayISO() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  function currentMonthPrefix() {
    return todayISO().slice(0, 7);
  }

  function formatRupiah(num) {
    const n = Number(num) || 0;
    const sign = n < 0 ? '-' : '';
    return `${sign}Rp ${Math.abs(n).toLocaleString('id-ID')}`;
  }

  function formatDateID(iso) {
    if (!iso) return '';
    const [y, m, d] = iso.split('-').map(Number);
    const date = new Date(y, m - 1, d);
    return date.toLocaleDateString('id-ID', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    });
  }

  function daysBetween(startISO, endISO) {
    const a = new Date(startISO + 'T00:00:00');
    const b = new Date(endISO + 'T00:00:00');
    return Math.max(0, Math.floor((b - a) / 86400000));
  }

  function uid(prefix = 'id') {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  }

  function escapeHTML(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function getCategoryIcon(type, catName) {
    if (type === 'saving') return '🎯';
    const list = CATEGORIES[type] || [];
    const found = list.find((c) => c.id === catName);
    return found ? found.icon : '💸';
  }

  function showToast(msg) {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => el.classList.remove('show'), 2600);
  }

  // --- Persistence ---
  function loadState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return DEFAULT_STATE();
      const parsed = JSON.parse(raw);
      return {
        ...DEFAULT_STATE(),
        ...parsed,
        transactions: Array.isArray(parsed.transactions) ? parsed.transactions : [],
        goals: Array.isArray(parsed.goals) ? parsed.goals : [],
        bans: Array.isArray(parsed.bans) ? parsed.bans : []
      };
    } catch {
      return DEFAULT_STATE();
    }
  }

  function saveState() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {
      showToast('Gagal menyimpan: memori penuh!');
    }
  }

  async function requestPersistentStorage() {
    if (navigator.storage && navigator.storage.persist) {
      try {
        await navigator.storage.persist();
      } catch {
        // Ignore if unsupported
      }
    }
  }

  // --- Theme ---
  function applyTheme() {
    const isDark = state.theme === 'dark';
    document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
    document.getElementById('btn-theme').textContent = isDark ? '☀️' : '🌙';
  }

  // --- Navigation ---
  function switchTab(tabName) {
    document.querySelectorAll('.tab-view').forEach((el) => {
      el.classList.toggle('active', el.id === `tab-${tabName}`);
    });
    document.querySelectorAll('.nav-item').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // --- Modals ---
  function openModal(id) {
    const el = document.getElementById(id);
    if (el) {
      el.classList.add('open');
      el.setAttribute('aria-hidden', 'false');
    }
  }

  function closeModal(id) {
    const el = document.getElementById(id);
    if (el) {
      el.classList.remove('open');
      el.setAttribute('aria-hidden', 'true');
    }
  }

  function openConfirm(title, message, onConfirm) {
    document.getElementById('confirm-title').textContent = title;
    document.getElementById('confirm-message').textContent = message;
    pendingConfirmAction = onConfirm;
    openModal('modal-confirm');
  }

  // --- Calculations ---
  function computeBalance() {
    return state.transactions.reduce((acc, tx) => {
      if (tx.type === 'income') return acc + tx.amount;
      return acc - tx.amount;
    }, 0);
  }

  function computeMonthTotals() {
    const prefix = currentMonthPrefix();
    let income = 0;
    let expense = 0;
    for (const tx of state.transactions) {
      if (!tx.date.startsWith(prefix)) continue;
      if (tx.type === 'income') income += tx.amount;
      else if (tx.type === 'expense') expense += tx.amount;
    }
    return { income, expense };
  }

  function computeTodayExpense() {
    const today = todayISO();
    return state.transactions
      .filter((tx) => tx.type === 'expense' && tx.date === today)
      .reduce((sum, tx) => sum + tx.amount, 0);
  }

  function getBanStreak(ban) {
    const ref = ban.lastBrokenDate || ban.createdAt || todayISO();
    return daysBetween(ref, todayISO());
  }

  function doesTxMatchBan(tx, ban) {
    if (!ban.active || tx.type !== 'expense') return false;
    if (ban.category && tx.category === ban.category) return true;
    const text = `${tx.note || ''} ${tx.category || ''}`.toLowerCase();
    return (ban.keywords || []).some((kw) => kw && text.includes(kw.toLowerCase()));
  }

  function checkTxWarnings(tx) {
    if (tx.type !== 'expense') return null;

    const violatedBans = [];
    for (const ban of state.bans) {
      if (!doesTxMatchBan(tx, ban)) continue;
      if (ban.mode === 'strict') {
        violatedBans.push({ ban, reason: 'Aturan blokir total' });
      } else if (ban.mode === 'limit') {
        const spentForBanToday = state.transactions
          .filter((t) => t.date === tx.date && doesTxMatchBan(t, ban))
          .reduce((s, t) => s + t.amount, 0);
        if (spentForBanToday + tx.amount > (ban.dailyMax || 0)) {
          violatedBans.push({
            ban,
            reason: `Melebihi batas harian larangan (${formatRupiah(ban.dailyMax)})`
          });
        }
      }
    }

    const todaySpent = state.transactions
      .filter((t) => t.type === 'expense' && t.date === tx.date)
      .reduce((s, t) => s + t.amount, 0);
    const overDailyLimit =
      state.dailyLimit > 0 && tx.date === todayISO() && todaySpent + tx.amount > state.dailyLimit;

    if (violatedBans.length === 0 && !overDailyLimit) return null;
    return { violatedBans, overDailyLimit, todaySpent };
  }

  // --- Render Functions ---
  function renderHeader() {
    document.getElementById('header-date').textContent = new Date().toLocaleDateString('id-ID', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }

  function renderHome() {
    const balance = computeBalance();
    const { income, expense } = computeMonthTotals();
    document.getElementById('home-balance').textContent = formatRupiah(balance);
    document.getElementById('home-income').textContent = formatRupiah(income);
    document.getElementById('home-expense').textContent = formatRupiah(expense);

    // Daily Guard
    const todaySpent = computeTodayExpense();
    const limit = state.dailyLimit || 0;
    document.getElementById('daily-spent').textContent = formatRupiah(todaySpent);
    document.getElementById('daily-limit-text').textContent = limit > 0 ? formatRupiah(limit) : 'Belum diatur';

    const badge = document.getElementById('daily-guard-badge');
    const bar = document.getElementById('daily-progress');
    const hint = document.getElementById('daily-guard-hint');

    bar.className = 'progress-fill';
    badge.className = 'badge';

    if (limit > 0) {
      const pct = Math.min(100, Math.round((todaySpent / limit) * 100));
      bar.style.width = `${pct}%`;
      const rem = limit - todaySpent;
      if (rem < 0) {
        bar.classList.add('over');
        badge.classList.add('badge-danger');
        badge.textContent = 'Lewat Batas!';
        hint.textContent = `Pengeluaran hari ini sudah lewat ${formatRupiah(Math.abs(rem))}. Stop jajan dulu ya!`;
      } else if (pct >= 80) {
        bar.classList.add('warn');
        badge.classList.add('badge-warning');
        badge.textContent = 'Hampir Habis';
        hint.textContent = `Sisa jatah hari ini tinggal ${formatRupiah(rem)}.`;
      } else {
        badge.classList.add('badge-success');
        badge.textContent = 'Aman';
        hint.textContent = `Masih ada sisa ${formatRupiah(rem)} untuk hari ini.`;
      }
    } else {
      bar.style.width = '0%';
      badge.textContent = 'Nonaktif';
      hint.textContent = 'Atur batas harian di menu Larangan agar pengeluaran lebih terkontrol.';
    }

    // Active Bans Preview
    const bansPreview = document.getElementById('home-bans-preview');
    const activeBans = state.bans.filter((b) => b.active);
    if (activeBans.length === 0) {
      bansPreview.innerHTML = `<div class="empty-state">Belum ada larangan jajan aktif.</div>`;
    } else {
      bansPreview.innerHTML = activeBans
        .slice(0, 3)
        .map((b) => {
          const streak = getBanStreak(b);
          return `
            <div class="tx-item">
              <div class="tx-left">
                <div class="tx-icon">🚫</div>
                <div class="tx-info">
                  <div class="tx-title">${escapeHTML(b.name)}</div>
                  <div class="tx-meta">${b.mode === 'strict' ? 'Blokir Total' : `Maks ${formatRupiah(b.dailyMax)}/hari`}</div>
                </div>
              </div>
              <span class="badge badge-success">🔥 ${streak} hari</span>
            </div>
          `;
        })
        .join('');
    }

    // Category Breakdown (Month)
    const breakdownEl = document.getElementById('home-breakdown');
    const prefix = currentMonthPrefix();
    const catMap = {};
    let totalMonthExp = 0;
    for (const tx of state.transactions) {
      if (tx.type === 'expense' && tx.date.startsWith(prefix)) {
        catMap[tx.category] = (catMap[tx.category] || 0) + tx.amount;
        totalMonthExp += tx.amount;
      }
    }
    const entries = Object.entries(catMap).sort((a, b) => b[1] - a[1]);
    if (entries.length === 0) {
      breakdownEl.innerHTML = `<div class="empty-state">Belum ada pengeluaran bulan ini.</div>`;
    } else {
      breakdownEl.innerHTML = entries
        .map(([cat, amt]) => {
          const pct = Math.round((amt / totalMonthExp) * 100);
          return `
            <div class="breakdown-row">
              <div class="breakdown-head">
                <span>${getCategoryIcon('expense', cat)} ${escapeHTML(cat)}</span>
                <strong>${formatRupiah(amt)} (${pct}%)</strong>
              </div>
              <div class="progress-track" style="height:7px;margin:2px 0;">
                <div class="progress-fill" style="width:${pct}%"></div>
              </div>
            </div>
          `;
        })
        .join('');
    }

    // Recent Transactions
    const recentEl = document.getElementById('home-recent');
    const recent = [...state.transactions]
      .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt - a.createdAt)
      .slice(0, 5);
    if (recent.length === 0) {
      recentEl.innerHTML = `<div class="empty-state">Belum ada transaksi. Yuk mulai catat keuanganmu!</div>`;
    } else {
      recentEl.innerHTML = recent.map((tx) => renderTxItemHTML(tx, false)).join('');
    }
  }

  function renderTxItemHTML(tx, showDelete = true) {
    const sign = tx.type === 'income' ? '+' : '-';
    const icon = getCategoryIcon(tx.type, tx.category);
    const title = tx.note ? escapeHTML(tx.note) : escapeHTML(tx.category);
    const sub = `${escapeHTML(tx.category)} • ${formatDateID(tx.date)}`;
    return `
      <div class="tx-item">
        <div class="tx-left">
          <div class="tx-icon">${icon}</div>
          <div class="tx-info">
            <div class="tx-title">${title}</div>
            <div class="tx-meta">${sub}</div>
          </div>
        </div>
        <div class="tx-right">
          <span class="tx-amount ${tx.type}">${sign}${formatRupiah(tx.amount)}</span>
          ${showDelete ? `<button class="del-mini-btn" data-del-tx="${tx.id}" title="Hapus">🗑️</button>` : ''}
        </div>
      </div>
    `;
  }

  function renderHistory() {
    const query = (document.getElementById('tx-search').value || '').trim().toLowerCase();
    const listEl = document.getElementById('history-list');

    const filtered = state.transactions
      .filter((tx) => {
        if (currentFilter !== 'all' && tx.type !== currentFilter) return false;
        if (!query) return true;
        return (
          (tx.note || '').toLowerCase().includes(query) ||
          (tx.category || '').toLowerCase().includes(query)
        );
      })
      .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt - a.createdAt);

    if (filtered.length === 0) {
      listEl.innerHTML = `<div class="card empty-state">Tidak ada transaksi yang cocok.</div>`;
      return;
    }

    const grouped = {};
    for (const tx of filtered) {
      if (!grouped[tx.date]) grouped[tx.date] = [];
      grouped[tx.date].push(tx);
    }

    listEl.innerHTML = Object.entries(grouped)
      .map(([date, items]) => {
        return `
          <div>
            <div class="date-group-label">${formatDateID(date)}</div>
            <div class="tx-list">
              ${items.map((tx) => renderTxItemHTML(tx, true)).join('')}
            </div>
          </div>
        `;
      })
      .join('');
  }

  function renderSavings() {
    const totalSaved = state.goals.reduce((s, g) => s + (g.saved || 0), 0);
    document.getElementById('savings-total').textContent = formatRupiah(totalSaved);

    const listEl = document.getElementById('goals-list');
    if (state.goals.length === 0) {
      listEl.innerHTML = `
        <div class="card empty-state">
          Belum ada target tabungan. Tekan <strong>+ Target Baru</strong> untuk mulai menabung!
        </div>
      `;
      return;
    }

    listEl.innerHTML = state.goals
      .map((g) => {
        const pct = Math.min(100, Math.round(((g.saved || 0) / g.target) * 100));
        const done = (g.saved || 0) >= g.target;
        const deadlineText = g.deadline ? `Target: ${formatDateID(g.deadline)}` : 'Tanpa tenggat waktu';
        return `
          <div class="goal-card">
            <div class="goal-top">
              <div class="goal-title-wrap">
                <span class="goal-emoji">${g.emoji || '🎯'}</span>
                <div>
                  <div class="goal-name">${escapeHTML(g.name)}</div>
                  <div class="goal-sub">${deadlineText}</div>
                </div>
              </div>
              <span class="badge ${done ? 'badge-success' : ''}">${pct}%</span>
            </div>
            <div class="progress-track">
              <div class="progress-fill" style="width:${pct}%"></div>
            </div>
            <div class="goal-amounts">
              <span>Terkumpul: <strong>${formatRupiah(g.saved || 0)}</strong></span>
              <span>Target: <strong>${formatRupiah(g.target)}</strong></span>
            </div>
            <div class="goal-actions">
              <button class="btn-primary btn-sm" style="flex:1" data-deposit-goal="${g.id}">💰 Nabung</button>
              <button class="btn-outline btn-sm" data-del-goal="${g.id}">Hapus</button>
            </div>
          </div>
        `;
      })
      .join('');
  }

  function renderBans() {
    document.getElementById('input-daily-limit').value = state.dailyLimit || '';
    document.getElementById('input-monthly-limit').value = state.monthlyLimit || '';

    const listEl = document.getElementById('bans-list');
    if (state.bans.length === 0) {
      listEl.innerHTML = `
        <div class="card empty-state">
          Belum ada larangan jajan. Tambahkan agar kamu terhindar dari jajan impulsif!
        </div>
      `;
      return;
    }

    listEl.innerHTML = state.bans
      .map((b) => {
        const streak = getBanStreak(b);
        const modeText = b.mode === 'strict' ? 'Blokir Total' : `Maks ${formatRupiah(b.dailyMax)} / hari`;
        const kwText = (b.keywords || []).length ? `Kata kunci: ${b.keywords.join(', ')}` : 'Tanpa kata kunci';
        const catText = b.category ? `Kategori: ${b.category}` : '';
        return `
          <div class="ban-card">
            <div class="ban-header">
              <div class="ban-title">${escapeHTML(b.name)}</div>
              <span class="badge ${b.active ? 'badge-success' : 'badge-warning'}">${b.active ? 'Aktif' : 'Dijeda'}</span>
            </div>
            <div class="ban-meta">${modeText}${catText ? ` • ${escapeHTML(catText)}` : ''}</div>
            <div class="ban-meta">${escapeHTML(kwText)}</div>
            <div class="streak-pill">🔥 Streak Bebas Jajan: ${streak} hari</div>
            <div class="ban-controls">
              <button class="btn-secondary btn-sm" data-toggle-ban="${b.id}">
                ${b.active ? '⏸️ Jeda' : '▶️ Aktifkan'}
              </button>
              <button class="btn-outline btn-sm" data-del-ban="${b.id}" style="color:var(--danger)">Hapus</button>
            </div>
          </div>
        `;
      })
      .join('');
  }

  function renderSettings() {
    document.getElementById('stat-tx-count').textContent = state.transactions.length;
    document.getElementById('stat-goal-count').textContent = state.goals.length;
    document.getElementById('stat-ban-count').textContent = state.bans.length;
  }

  function renderAll() {
    applyTheme();
    renderHeader();
    renderHome();
    renderHistory();
    renderSavings();
    renderBans();
    renderSettings();
  }

  // --- Transaction Form Helpers ---
  function populateTxCategories(type) {
    const select = document.getElementById('tx-category');
    const list = CATEGORIES[type] || [];
    select.innerHTML = list.map((c) => `<option value="${c.id}">${c.icon} ${c.id}</option>`).join('');
  }

  function openTxModal(type = 'expense') {
    document.getElementById('form-tx').reset();
    document.getElementById('tx-type').value = type;
    document.getElementById('tx-date').value = todayISO();
    document.querySelectorAll('.seg-btn').forEach((b) => {
      b.classList.toggle('active', b.dataset.txType === type);
    });
    populateTxCategories(type);
    openModal('modal-tx');
  }

  function commitTransaction(tx, violatedBans = []) {
    state.transactions.push(tx);
    for (const v of violatedBans) {
      const targetBan = state.bans.find((b) => b.id === v.ban.id);
      if (targetBan) targetBan.lastBrokenDate = tx.date;
    }
    saveState();
    renderAll();
    closeModal('modal-tx');
    closeModal('modal-ban-warning');
    showToast(tx.type === 'income' ? 'Pemasukan dicatat!' : 'Pengeluaran dicatat!');
  }

  // --- Event Listeners ---
  function bindEvents() {
    // Bottom nav & quick links
    document.querySelectorAll('.nav-item').forEach((btn) => {
      btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });
    document.querySelectorAll('[data-goto]').forEach((btn) => {
      btn.addEventListener('click', () => switchTab(btn.dataset.goto));
    });

    // Theme toggle
    document.getElementById('btn-theme').addEventListener('click', () => {
      state.theme = state.theme === 'dark' ? 'light' : 'dark';
      saveState();
      applyTheme();
    });

    // Modal close buttons & backdrop click
    document.querySelectorAll('[data-close]').forEach((btn) => {
      btn.addEventListener('click', () => closeModal(btn.dataset.close));
    });
    document.querySelectorAll('.modal-backdrop').forEach((backdrop) => {
      backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) closeModal(backdrop.id);
      });
    });

    // Quick add transaction buttons
    document.getElementById('btn-quick-income').addEventListener('click', () => openTxModal('income'));
    document.getElementById('btn-quick-expense').addEventListener('click', () => openTxModal('expense'));
    document.getElementById('btn-add-tx-history').addEventListener('click', () => openTxModal('expense'));

    // Segmented control in tx modal
    document.querySelectorAll('.seg-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        const type = btn.dataset.txType;
        document.getElementById('tx-type').value = type;
        document.querySelectorAll('.seg-btn').forEach((b) => b.classList.toggle('active', b === btn));
        populateTxCategories(type);
      });
    });

    // Quick amount chips
    document.querySelectorAll('#form-tx .amt-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        const input = document.getElementById('tx-amount');
        input.value = (Number(input.value) || 0) + Number(chip.dataset.amt);
      });
    });
    document.querySelectorAll('.dep-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        const input = document.getElementById('deposit-amount');
        input.value = (Number(input.value) || 0) + Number(chip.dataset.amt);
      });
    });

    // Submit Transaction
    document.getElementById('form-tx').addEventListener('submit', (e) => {
      e.preventDefault();
      const amount = Number(document.getElementById('tx-amount').value);
      if (!amount || amount <= 0) return;

      const tx = {
        id: uid('tx'),
        type: document.getElementById('tx-type').value,
        amount,
        category: document.getElementById('tx-category').value,
        note: document.getElementById('tx-note').value.trim(),
        date: document.getElementById('tx-date').value || todayISO(),
        createdAt: Date.now()
      };

      const warnings = checkTxWarnings(tx);
      if (warnings) {
        pendingTx = { tx, warnings };
        const parts = [];
        if (warnings.violatedBans.length > 0) {
          const names = warnings.violatedBans.map((v) => `"${v.ban.name}"`).join(', ');
          parts.push(`Pengeluaran ini melanggar larangan jajan: ${names}.`);
        }
        if (warnings.overDailyLimit) {
          parts.push(`Total belanja hari ini akan melebihi batas harian (${formatRupiah(state.dailyLimit)}).`);
        }
        parts.push('Yakin mau lanjut? Streak hematmu bisa tereset lho!');
        document.getElementById('ban-warn-message').textContent = parts.join(' ');
        openModal('modal-ban-warning');
        return;
      }

      commitTransaction(tx);
    });

    // Ban warning actions
    document.getElementById('btn-ban-cancel').addEventListener('click', () => {
      pendingTx = null;
      closeModal('modal-ban-warning');
      closeModal('modal-tx');
      showToast('Keren! Kamu berhasil menahan diri 💪');
    });

    document.getElementById('btn-ban-proceed').addEventListener('click', () => {
      if (!pendingTx) return;
      const { tx, warnings } = pendingTx;
      pendingTx = null;
      commitTransaction(tx, warnings.violatedBans);
    });

    // History search & filters
    document.getElementById('tx-search').addEventListener('input', renderHistory);
    document.querySelectorAll('#tx-filters .chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        currentFilter = chip.dataset.filter;
        document.querySelectorAll('#tx-filters .chip').forEach((c) => c.classList.toggle('active', c === chip));
        renderHistory();
      });
    });

    // Delete single transaction (delegated)
    document.getElementById('history-list').addEventListener('click', (e) => {
      const btn = e.target.closest('[data-del-tx]');
      if (!btn) return;
      const id = btn.dataset.delTx;
      openConfirm('Hapus Transaksi?', 'Transaksi ini akan dihapus permanen dari riwayat.', () => {
        state.transactions = state.transactions.filter((t) => t.id !== id);
        saveState();
        renderAll();
        showToast('Transaksi dihapus.');
      });
    });

    // Savings Goals
    document.getElementById('btn-add-goal').addEventListener('click', () => {
      document.getElementById('form-goal').reset();
      openModal('modal-goal');
    });

    document.getElementById('form-goal').addEventListener('submit', (e) => {
      e.preventDefault();
      const name = document.getElementById('goal-name').value.trim();
      const target = Number(document.getElementById('goal-target').value);
      if (!name || !target) return;

      state.goals.push({
        id: uid('goal'),
        emoji: document.getElementById('goal-emoji').value,
        name,
        target,
        saved: 0,
        deadline: document.getElementById('goal-deadline').value || null
      });
      saveState();
      renderAll();
      closeModal('modal-goal');
      showToast('Target tabungan dibuat!');
    });

    document.getElementById('goals-list').addEventListener('click', (e) => {
      const depBtn = e.target.closest('[data-deposit-goal]');
      if (depBtn) {
        const goal = state.goals.find((g) => g.id === depBtn.dataset.depositGoal);
        if (!goal) return;
        document.getElementById('form-deposit').reset();
        document.getElementById('deposit-goal-id').value = goal.id;
        document.getElementById('deposit-title').textContent = `Nabung: ${goal.name}`;
        document.getElementById('deposit-deduct-balance').checked = true;
        openModal('modal-deposit');
        return;
      }

      const delBtn = e.target.closest('[data-del-goal]');
      if (delBtn) {
        const id = delBtn.dataset.delGoal;
        openConfirm('Hapus Target Tabungan?', 'Target ini akan dihapus dari daftar.', () => {
          state.goals = state.goals.filter((g) => g.id !== id);
          saveState();
          renderAll();
          showToast('Target tabungan dihapus.');
        });
      }
    });

    document.getElementById('form-deposit').addEventListener('submit', (e) => {
      e.preventDefault();
      const id = document.getElementById('deposit-goal-id').value;
      const amount = Number(document.getElementById('deposit-amount').value);
      const deduct = document.getElementById('deposit-deduct-balance').checked;
      const goal = state.goals.find((g) => g.id === id);
      if (!goal || !amount || amount <= 0) return;

      goal.saved = (goal.saved || 0) + amount;
      if (deduct) {
        state.transactions.push({
          id: uid('tx'),
          type: 'saving',
          amount,
          category: 'Tabungan',
          note: `Setor ke ${goal.name}`,
          date: todayISO(),
          createdAt: Date.now()
        });
      }
      saveState();
      renderAll();
      closeModal('modal-deposit');
      showToast(`Berhasil menabung ${formatRupiah(amount)}!`);
    });

    // Limits & Bans
    document.getElementById('btn-save-limits').addEventListener('click', () => {
      state.dailyLimit = Math.max(0, Number(document.getElementById('input-daily-limit').value) || 0);
      state.monthlyLimit = Math.max(0, Number(document.getElementById('input-monthly-limit').value) || 0);
      saveState();
      renderAll();
      showToast('Batas pengeluaran disimpan!');
    });

    document.getElementById('btn-add-ban').addEventListener('click', () => {
      document.getElementById('form-ban').reset();
      document.getElementById('ban-limit-group').style.display = 'none';
      openModal('modal-ban');
    });

    document.getElementById('ban-mode').addEventListener('change', (e) => {
      document.getElementById('ban-limit-group').style.display =
        e.target.value === 'limit' ? 'flex' : 'none';
    });

    document.getElementById('form-ban').addEventListener('submit', (e) => {
      e.preventDefault();
      const name = document.getElementById('ban-name').value.trim();
      const rawKw = document.getElementById('ban-keywords').value;
      const keywords = rawKw
        .split(',')
        .map((k) => k.trim().toLowerCase())
        .filter(Boolean);
      const category = document.getElementById('ban-category').value;
      const mode = document.getElementById('ban-mode').value;
      const dailyMax = mode === 'limit' ? Number(document.getElementById('ban-daily-max').value) || 0 : 0;

      if (!name) return;
      state.bans.push({
        id: uid('ban'),
        name,
        keywords,
        category,
        mode,
        dailyMax,
        active: true,
        createdAt: todayISO(),
        lastBrokenDate: null
      });
      saveState();
      renderAll();
      closeModal('modal-ban');
      showToast('Larangan jajan diaktifkan!');
    });

    document.getElementById('bans-list').addEventListener('click', (e) => {
      const toggleBtn = e.target.closest('[data-toggle-ban]');
      if (toggleBtn) {
        const ban = state.bans.find((b) => b.id === toggleBtn.dataset.toggleBan);
        if (ban) {
          ban.active = !ban.active;
          saveState();
          renderAll();
          showToast(ban.active ? 'Larangan diaktifkan.' : 'Larangan dijeda.');
        }
        return;
      }

      const delBtn = e.target.closest('[data-del-ban]');
      if (delBtn) {
        const id = delBtn.dataset.delBan;
        openConfirm('Hapus Larangan Jajan?', 'Aturan larangan ini akan dihapus.', () => {
          state.bans = state.bans.filter((b) => b.id !== id);
          saveState();
          renderAll();
          showToast('Larangan dihapus.');
        });
      }
    });

    // Settings: Backup / Restore / Clear Data
    document.getElementById('btn-export-data').addEventListener('click', () => {
      const jsonStr = JSON.stringify(state, null, 2);
      const filename = `dompetku-backup-${todayISO()}.json`;
      if (window.AndroidBridge && typeof window.AndroidBridge.exportBackup === 'function') {
        window.AndroidBridge.exportBackup(jsonStr, filename);
        showToast('Membuka menu simpan/bagikan...');
        return;
      }
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
      showToast('File cadangan diunduh!');
    });

    document.getElementById('input-import-data').addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const parsed = JSON.parse(reader.result);
          if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.transactions)) {
            throw new Error('Format tidak valid');
          }
          state = { ...DEFAULT_STATE(), ...parsed };
          saveState();
          renderAll();
          showToast('Data berhasil dipulihkan!');
        } catch {
          showToast('File cadangan tidak valid!');
        }
      };
      reader.readAsText(file);
      e.target.value = '';
    });

    document.getElementById('btn-clear-tx').addEventListener('click', () => {
      openConfirm(
        'Hapus Semua Riwayat Transaksi?',
        'Semua catatan pemasukan & pengeluaran akan dihapus, sedangkan target tabungan dan larangan jajan tetap ada.',
        () => {
          state.transactions = [];
          saveState();
          renderAll();
          showToast('Riwayat transaksi berhasil dihapus.');
        }
      );
    });

    document.getElementById('btn-clear-all').addEventListener('click', () => {
      openConfirm(
        'Reset Total Semua Data?',
        'Seluruh transaksi, target tabungan, batas harian, dan larangan jajan akan dihapus permanen dari HP ini.',
        () => {
          localStorage.removeItem(STORAGE_KEY);
          state = {
            theme: state.theme,
            dailyLimit: 0,
            monthlyLimit: 0,
            transactions: [],
            goals: [],
            bans: []
          };
          saveState();
          renderAll();
          showToast('Semua data telah dihapus bersih.');
        }
      );
    });

    document.getElementById('btn-confirm-yes').addEventListener('click', () => {
      const fn = pendingConfirmAction;
      pendingConfirmAction = null;
      closeModal('modal-confirm');
      if (typeof fn === 'function') fn();
    });
  }

  // --- Init ---
  function init() {
    requestPersistentStorage();
    bindEvents();
    renderAll();

    if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
      navigator.serviceWorker.register('./sw.js').catch(() => {});
    }
  }

  init();
})();
