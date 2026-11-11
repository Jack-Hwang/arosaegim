/* 아로새김 가족 모바일 웹 — 공통 유틸 · 라우터 · 시작 흐름
 * 정적 HTML + 바닐라 JS(빌드 단계 없음 — 02문서 1절).
 * 해시 기반 단일 페이지 라우터: #/home #/consent #/courses #/record #/timeline #/family #/receipt */
(function () {
  'use strict';
  window.Screens = window.Screens || {};
  var LS_CONSENT = 'aro_privacy_consent';

  var App = window.App = {
    /* 개인정보 동의 상태: '' | 'granted' | 'denied' — 기기 로컬에만 저장 */
    consent: function () { return localStorage.getItem(LS_CONSENT) || ''; },
    setConsent: function (v) {
      if (v) localStorage.setItem(LS_CONSENT, v);
      else localStorage.removeItem(LS_CONSENT);
    },

    /* DOM 빌더: h('태그', {class, text, onclick, ...}, 자식...) */
    h: function (tag, attrs) {
      var el = document.createElement(tag);
      var k, v, i, c;
      for (k in (attrs || {})) {
        v = attrs[k];
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'text') el.textContent = v;
        else if (k.indexOf('on') === 0) el.addEventListener(k.slice(2), v);
        else el.setAttribute(k, v === true ? '' : v);
      }
      function add(node) {
        if (node == null || node === false) return;
        if (Array.isArray(node)) { node.forEach(add); return; }
        el.appendChild(typeof node === 'string' ? document.createTextNode(node) : node);
      }
      for (i = 2; i < arguments.length; i++) add(arguments[i]);
      return el;
    },

    /* 오류 배너: 상단 한 줄, 3초 (콘솔 로그는 api.js가 개인정보 없이 남긴다) */
    bannerTimer: null,
    banner: function (msg) {
      var b = document.getElementById('banner');
      b.textContent = msg || '요청을 처리하지 못했어요.';
      b.hidden = false;
      clearTimeout(App.bannerTimer);
      App.bannerTimer = setTimeout(function () { b.hidden = true; }, 3000);
    },

    fmtDT: function (iso) {
      if (!iso) return '';
      var d = new Date(iso);
      if (isNaN(d.getTime())) return String(iso);
      function p(n) { return (n < 10 ? '0' : '') + n; }
      return d.getFullYear() + '. ' + (d.getMonth() + 1) + '. ' + d.getDate() +
        '. ' + p(d.getHours()) + ':' + p(d.getMinutes());
    },
    fmtNum: function (n) { return Number(n || 0).toLocaleString('ko-KR'); },
    GEN_LABEL: { 1: '조부모대', 2: '부모대', 3: '자녀대', 4: '손주대' },
    /* 세대 표시: 서버는 문자 라벨(예: '부모대')을 돌려준다. 숫자(구버전)면 라벨로 바꾼다. */
    genLabel: function (g) {
      if (g == null || g === '') return '';
      if (typeof g === 'number' || /^\d+$/.test(String(g))) {
        return App.GEN_LABEL[Number(g)] || ('세대 ' + g);
      }
      return String(g);
    },
    trimNote: function (s, n) {
      s = String(s || '');
      return s.length > n ? s.slice(0, n) + '…' : s;
    },

    /* 사진 검증: JPEG/PNG, 5MB 이하 (02문서 3절 제한과 같은 기준) */
    checkPhoto: function (file) {
      if (!file) return '';
      if (file.type !== 'image/jpeg' && file.type !== 'image/png') return 'JPEG·PNG 사진만 올릴 수 있어요.';
      if (file.size > 5 * 1024 * 1024) return '사진이 너무 커요. 5MB 이하로 올려 주세요.';
      return '';
    },

    /* 모달: 바깥 클릭으로 닫히지 않는다(동의·위험 동작 보호 — 02문서 5절) */
    modal: function (title, bodyEl, opts) {
      opts = opts || {};
      var overlay = App.h('div', { class: 'modal-overlay' });
      var close = function () { overlay.remove(); };
      var head = App.h('div', { class: 'modal-head' }, App.h('h2', { text: title }));
      if (opts.closable !== false) {
        head.appendChild(App.h('button', { class: 'btn ghost small', type: 'button', text: '닫기', onclick: close }));
      }
      overlay.appendChild(App.h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true' }, head, bodyEl));
      document.body.appendChild(overlay);
      return { close: close, overlay: overlay };
    },

    /* 위험 동작 이중 확인: 1단 확인 → 2단 최종 확인 → 실행 */
    confirmDanger: function (title, lines, confirmLabel, onConfirm) {
      function closeAll() {
        var list = document.querySelectorAll('.modal-overlay');
        for (var i = 0; i < list.length; i++) list[i].remove();
      }
      var m;
      function step2() {
        closeAll();
        m = App.modal(title + ' · 최종 확인', App.h('div', null,
          App.h('p', { class: 'confirm-strong', text: '마지막 확인이에요.' }),
          App.h('p', { class: 'muted', text: '아래 버튼을 누르면 바로 진행돼요.' }),
          App.h('div', { class: 'btn-row' },
            App.h('button', { class: 'btn outline', type: 'button', text: '취소', onclick: function () { m.close(); } }),
            App.h('button', { class: 'btn danger', type: 'button', text: confirmLabel, onclick: function () { m.close(); onConfirm(); } }))),
          { closable: false });
      }
      m = App.modal(title, App.h('div', null,
        lines.map(function (t) { return App.h('p', { class: 'confirm-line', text: t }); }),
        App.h('div', { class: 'btn-row' },
          App.h('button', { class: 'btn outline', type: 'button', text: '취소', onclick: function () { m.close(); } }),
          App.h('button', { class: 'btn danger', type: 'button', text: '계속', onclick: step2 }))),
        { closable: false });
    },

    /* 화면 공용 캐시: 코스·장소 목록 (기록·영수증 화면에서 사용) */
    _placesPromise: null,
    loadPlaces: function () {
      if (App._placesPromise) return App._placesPromise;
      App._placesPromise = Api.get('/v1/courses').then(function (courses) {
        return Promise.all((courses || []).map(function (c) {
          return Api.get('/v1/courses/' + encodeURIComponent(c.id)).then(function (d) {
            return { id: c.id, title: c.title, places: (d && d.places) || [] };
          });
        }));
      });
      return App._placesPromise;
    },

    /* 제휴처 이름 캐시: 포인트 내역의 reason("receipt:{제휴처 id}")을 제휴처 이름으로
     * 바꿔 표시할 때 쓴다(서버 points 내역에는 partner_name이 없다 — README 가정 참조).
     * partners는 장소별로만 조회할 수 있어(GET /v1/places/{id}/partners)
     * 전체 장소를 훑어 모은다. 개별 장소 실패는 무시하고, 전체 실패 시 다음 호출에서 다시 읽는다. */
    _partnersPromise: null,
    loadPartnerNames: function () {
      if (App._partnersPromise) return App._partnersPromise;
      var map = {};
      App._partnersPromise = App.loadPlaces().then(function (groups) {
        var jobs = [];
        (groups || []).forEach(function (g) {
          (g.places || []).forEach(function (pl) {
            jobs.push(Api.get('/v1/places/' + encodeURIComponent(pl.id) + '/partners').then(function (ps) {
              (ps || []).forEach(function (p) {
                if (p && p.id) map[String(p.id)] = p.name || String(p.id);
              });
            }, function () { /* 이 장소의 제휴처를 못 읽어도 나머지로 진행한다 */ }));
          });
        });
        return Promise.all(jobs).then(function () { return map; });
      }, function (e) {
        App._partnersPromise = null; // 전체 실패 — 다음 호출에서 다시 시도한다
        throw e;
      });
      return App._partnersPromise;
    },
    invalidatePlaces: function () { App._placesPromise = null; App._partnersPromise = null; }
  };

  /* ── 라우터 ─────────────────────────────────────────────── */
  var ROUTES = {
    '#/home': 'home', '#/consent': 'consent', '#/courses': 'courses',
    '#/record': 'record', '#/timeline': 'timeline', '#/family': 'family',
    '#/receipt': 'receipt'
  };

  function render() {
    var hash = location.hash || '#/home';
    var name = ROUTES[hash] || 'home';
    var c = App.consent();

    // 개인정보 동의 전에는 서비스 화면에 들어갈 수 없다. 거부 시 홈으로(02문서 5절).
    if (name !== 'consent' && name !== 'home' && c !== 'granted') {
      location.hash = '#/consent';
      return;
    }
    // 가족 코드가 없으면 홈(가족 코드 화면)부터.
    if (!Api.hasAuth() && name !== 'home' && name !== 'consent') {
      location.hash = '#/home';
      return;
    }
    var screen = Screens[name];
    if (!screen) { location.hash = '#/home'; return; }

    var root = document.getElementById('app');
    root.innerHTML = '';
    window.scrollTo(0, 0);

    var nav = document.getElementById('nav');
    nav.hidden = !(Api.hasAuth() && c === 'granted');
    var btns = nav.querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      btns[i].classList.toggle('active', btns[i].getAttribute('data-go') === hash);
    }
    document.getElementById('homeBtn').disabled = (name === 'home' || !Api.hasAuth());
    document.getElementById('topSub').textContent = screen.sub || '';

    try {
      screen.render(root);
    } catch (err) {
      console.log('[aro] render-error ' + name);
      App.banner('화면을 표시하지 못했어요. 새로고침해 주세요.');
    }
  }

  function boot() {
    document.getElementById('homeBtn').addEventListener('click', function () {
      location.hash = '#/home';
    });
    var btns = document.querySelectorAll('#nav button');
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener('click', function () {
        location.hash = this.getAttribute('data-go');
      });
    }
    window.addEventListener('hashchange', render);
    if (!location.hash) {
      // 첫 방문(동의 전)이면 개인정보 안내가 맨 처음 온다. 거부 시 홈으로(02문서 5절).
      location.hash = App.consent() === '' ? '#/consent'
        : (Api.hasAuth() ? '#/courses' : '#/home');
    }
    render();
  }
  App.rerender = render;
  document.addEventListener('DOMContentLoaded', boot);
})();
