/* Offline, synthetic UI demonstration. No backend calls, capture, or automatic clipboard reads. */
'use strict';
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const themeNames = { aurora: 'Aurora', daylight: 'Daylight', ocean: 'Ocean', rose: 'Rose' };
const samples = {
  friendly: ["Yes! Really looking forward to catching up at 4.", "Absolutely! Coffee and a catch-up sounds perfect.", "Wouldn't miss it. See you at 4!"],
  casual: ["Yep, 4 works! See you there.", "For sure. I'll meet you there at 4.", "We're on! Can't wait to try that place."],
  professional: ["Yes, 4:00 PM still works well for me. See you soon.", "Confirmed for 4:00 PM. Looking forward to catching up.", "That time works for me. I'll see you at the coffee shop."],
  funny: ["Absolutely. My coffee budget has approved this meeting.", "It's a date with caffeine. See you at 4!", "Yep! I'll bring the stories, they can bring the coffee."],
  flirty: ["Definitely. The coffee is just a bonus.", "Wouldn't miss a chance to see you. 4 it is!", "Yes! I've been looking forward to it."],
  short: ["Yep, see you at 4!", "We're on!", "4 works. See you!"],
  detailed: ["Yes, we're still on for 4! I'll meet you at that little coffee spot. Looking forward to catching up.", "Absolutely! 4 works perfectly for me. I'll head over a little early and try to find us a table.", "We're all set for 4. I've been wanting to try that place, so this is the perfect excuse. See you soon!"]
};
let theme = 'aurora', tone = 'all', mode = 'ready', screen = 'replies', keys = false;
let toastTimer, generationTimer;
const completedSteps = new Set();
function notify(message) {
  clearTimeout(toastTimer); $('#toast').textContent = message; $('#toast').classList.add('visible');
  toastTimer = setTimeout(() => $('#toast').classList.remove('visible'), 3200);
}
function setTheme(value, persist = true) {
  theme = Object.hasOwn(themeNames, value) ? value : 'aurora';
  $$('.phone').forEach(phone => phone.dataset.theme = theme);
  $$('[data-theme-choice]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.themeChoice === theme)));
  $('#theme-caption').textContent = themeNames[theme].toUpperCase();
  if (persist) { try { localStorage.setItem('wingai-preview-theme', theme); } catch (_) { /* Private browsing still works. */ } }
}
function setScreen(value) {
  screen = value === 'setup' ? 'setup' : 'replies';
  $('#conversation-screen').hidden = screen !== 'replies'; $('#setup-screen').hidden = screen !== 'setup';
  $$('[data-screen]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.screen === screen)));
  $('#main-caption').textContent = screen === 'setup' ? '01 — A WARMER WELCOME' : '01 — IN THE CONVERSATION';
  $('#demo-state').disabled = screen !== 'replies';
}
function setKeys(value) {
  keys = value; $('#reply-panel').hidden = keys; $('#keyboard-panel').hidden = !keys;
  $('#toggle-keys').textContent = keys ? 'AI replies' : 'Keyboard';
  $('#toggle-keys').setAttribute('aria-pressed', String(keys));
}
function actionButton(text, handler, className = '') {
  const button = document.createElement('button'); button.textContent = text; button.className = className;
  button.addEventListener('click', handler); return button;
}
function setMode(value) {
  clearTimeout(generationTimer); mode = value; $('#demo-state').value = mode; renderReplies();
}
function demoGenerate() {
  setKeys(false); setMode('loading');
  generationTimer = setTimeout(() => { setMode('ready'); notify('Sample replies loaded. No AI request was made.'); }, 900);
}
function renderReplies() {
  const list = $('#reply-list'); list.replaceChildren(); list.scrollTop = 0;
  if (mode === 'loading') {
    const label = document.createElement('span'); label.className = 'sr-only'; label.textContent = 'Finding the words. Demo loading state.'; list.append(label);
    for (let i = 0; i < 3; i++) {
      const card = document.createElement('div'); card.className = 'reply-card'; card.setAttribute('aria-hidden', 'true');
      card.innerHTML = '<div class="loading-bar short"></div><div class="loading-bar"></div><div class="loading-bar short"></div>';
      list.append(card);
    }
    list.prepend(actionButton('Cancel', () => setMode('empty'))); return;
  }
  if (mode === 'empty' || mode === 'error') {
    const card = document.createElement('div'); card.className = 'reply-empty';
    const title = document.createElement('h3'); title.textContent = mode === 'error' ? "Let's try that again." : 'Find your next words.';
    const text = document.createElement('p'); text.textContent = mode === 'error' ? "Couldn't connect. Your draft is still here. This is a simulated error." : 'Choose a chat area or paste a conversation. Get a few ways to say it, then make one yours.';
    card.append(title, text, actionButton(mode === 'error' ? 'Try again' : 'Load sample chat', demoGenerate)); list.append(card); return;
  }
  const replies = tone === 'all' ? ['friendly', 'casual', 'professional'].map(t => ({ tone: t, text: samples[t][0] })) : samples[tone].map(text => ({ tone, text }));
  replies.forEach(reply => {
    const card = document.createElement('article'); card.className = 'reply-card';
    const head = document.createElement('div'); head.className = 'reply-card-head';
    const badge = document.createElement('span'); badge.className = 'tone-label'; badge.textContent = reply.tone;
    const actions = document.createElement('div'); actions.className = 'reply-actions';
    const text = document.createElement('p'); text.textContent = reply.text;
    actions.append(actionButton('Shorten', () => { text.textContent = samples.short[0]; notify('Sample shortened version — no AI request.'); }), actionButton('Expand', () => { text.textContent = samples.detailed[0]; notify('Sample expanded version — no AI request.'); }), actionButton('Insert', () => { $('#draft').value = text.textContent; notify('Added to the demo draft. Nothing was sent.'); }, 'insert'));
    head.append(badge, actions); card.append(head, text); list.append(card);
  });
}
function buildKeyboard() {
  const host = $('#key-rows');
  ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'].forEach(letters => {
    const row = document.createElement('div'); row.className = 'key-row';
    [...letters].forEach(key => row.append(actionButton(key, () => { $('#draft').value += key; })));
    host.append(row);
  });
  const row = document.createElement('div'); row.className = 'key-row';
  row.append(actionButton('123', () => notify('Symbols are part of the real HeliBoard keyboard, not this demo.'), 'wide-key'), actionButton('space', () => { $('#draft').value += ' '; }, 'space-key'), actionButton('↵', () => notify('Return key preview. No message is sent.'), 'wide-key action-key'), actionButton('⌫', () => { $('#draft').value = [...$('#draft').value].slice(0, -1).join(''); }, 'wide-key'));
  host.append(row);
}
$$('[data-theme-choice]').forEach(button => button.addEventListener('click', () => setTheme(button.dataset.themeChoice)));
$$('[data-screen]').forEach(button => button.addEventListener('click', () => setScreen(button.dataset.screen)));
$$('[data-tone]').forEach(button => button.addEventListener('click', () => {
  tone = button.dataset.tone;
  $$('[data-tone]').forEach(item => item.setAttribute('aria-pressed', String(item.dataset.tone === tone)));
  setMode('ready');
}));
$('#demo-state').addEventListener('change', event => { setKeys(false); setMode(event.target.value); });
$('#toggle-keys').addEventListener('click', () => setKeys(!keys));
$('#analyze-demo').addEventListener('click', demoGenerate);
$('#send-demo').addEventListener('click', () => notify('Preview only. Messages are never sent from this page.'));
$('#copy-id').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText('wing_demo_preview'); notify('Demo ID copied. This is not a real account.'); }
  catch (_) { notify('Demo ID: wing_demo_preview. Clipboard access is unavailable here.'); }
});
$$('[data-setup]').forEach(button => button.addEventListener('click', () => {
  const step = Number(button.dataset.setup); completedSteps.add(step);
  button.querySelector('.step-check').textContent = '✓';
  $$('.setup-progress i').forEach((item, index) => item.classList.toggle('done', completedSteps.has(index)));
  $('#setup-progress-label').textContent = `${completedSteps.size} of 3 demo steps explored`;
  notify('In Android this opens system settings. No device permission was changed.');
}));
$('#try-replies').addEventListener('click', () => setScreen('replies'));
$('#reset-demo').addEventListener('click', () => {
  setTheme('aurora'); setScreen('replies'); setKeys(false); tone = 'all';
  $$('[data-tone]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.tone === tone)));
  $('#draft').value = ''; completedSteps.clear();
  $$('.setup-progress i').forEach(item => item.classList.remove('done'));
  $$('.step-check').forEach(item => item.textContent = '↗');
  $('#setup-progress-label').textContent = '0 of 3 demo steps explored';
  setMode('ready'); notify('Preview reset.');
});
try { setTheme(localStorage.getItem('wingai-preview-theme'), false); } catch (_) { setTheme('aurora', false); }
buildKeyboard(); renderReplies();
