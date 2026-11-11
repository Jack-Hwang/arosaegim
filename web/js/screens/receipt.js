/* 영수증 인증 (02문서 6절 · 11절 — 시뮬레이션 한정)
 * 장소 선택 → 주변 제휴처(GET /v1/places/{id}/partners) → 금액·결제 시각·사진(필수)
 *   · 제휴처 kind가 lodging(숙소)이면 체크인·체크아웃(YYYY-MM-DD, 체크아웃>체크인, 최대 3박)을 추가로 받는다.
 *   · 식당·가게는 결제 일시만. museum_shop은 화면에서 '가게'로 표기한다(00문서 13절).
 * → POST /v1/receipts (multipart) → 결과: accepted / pending(확인 대기) / rejected(사유별 한국어 한 줄).
 * 문구는 "포인트 캐시백(예시)"만 쓴다. 환급·입금·원 단위 지급처럼 읽히는 문구 금지(00문서 13절).
 * 확률·추첨 문구도 쓰지 않는다. */
(function () {
  'use strict';
  /* 제휴처 kind → 화면 표기. museum_shop은 '가게'로 묶는다(00문서 13절) */
  var KIND_LABEL = {
    restaurant: '맛집',
    shop: '가게',
    museum_shop: '가게',
    lodging: '숙소'
  };
  var PENDING_LINE = '같은 제휴처·같은 날의 두 번째 영수증은 확인 후 적립돼요.';
  /* rejected 사유 → 한국어 한 줄. (400 입력 오류는 api.js 배너 계층이 처리한다) */
  var REJECT_MSG = {
    no_visit: '스탬프를 찍은 장소 주변 제휴처만 인정돼요. 방문 기록을 먼저 만들어 주세요.',
    duplicate: '이미 등록된 영수증이에요.',
    duplicate_photo: '같은 사진은 다시 쓸 수 없어요. 영수증 사진을 새로 찍어 주세요.',
    daily_limit: '오늘 캐시백 한도를 채웠어요. 내일 다시 시도해 주세요.',
    invalid_amount: '금액을 확인해 주세요. (1원 ~ 100만 원)',
    missing_photo: '영수증 사진이 필요해요. 사진을 선택해 주세요.'
  };

  Screens.receipt = {
    sub: '영수증',
    render: function (root) {
      var wrap = App.h('div', { class: 'page' });
      root.appendChild(wrap);
      wrap.appendChild(App.h('div', { class: 'page-head' },
        App.h('h1', { class: 'page-title', text: '영수증 인증' }),
        App.h('span', { class: 'badge sim', text: '예시(시뮬레이션)' })));
      wrap.appendChild(App.h('p', { class: 'muted small', text: '포인트 캐시백(예시) — 현금이 아니에요. 실제 결제·포인트와 연결되지 않아요.' }));

      /* 제휴처 id → kind 매핑(숙소 판정용) */
      var partnerKind = {};
      var placeSel = App.h('select', { class: 'input' }, App.h('option', { value: '', text: '장소를 고르세요' }));
      var partnerSel = App.h('select', { class: 'input', disabled: true }, App.h('option', { value: '', text: '먼저 장소를 고르세요' }));
      var amountIn = App.h('input', { class: 'input', type: 'number', inputmode: 'numeric', min: '1', max: '1000000', placeholder: '예: 25000' });
      var paidLabel = App.h('span', { text: '결제 일시' });
      var paidIn = App.h('input', { class: 'input', type: 'datetime-local' });
      try { paidIn.value = localNow(); } catch (e) { /* 구형 브라우저는 직접 입력 */ }
      /* 숙소(lodging)만 보이는 숙박 기간 입력(02문서 11절) */
      var checkinIn = App.h('input', { class: 'input', type: 'date' });
      var checkoutIn = App.h('input', { class: 'input', type: 'date' });
      var stayBox = App.h('div', { hidden: true },
        App.h('label', { class: 'field' }, App.h('span', { text: '체크인 날짜 (YYYY-MM-DD)' }), checkinIn),
        App.h('label', { class: 'field' }, App.h('span', { text: '체크아웃 날짜 (체크인보다 뒤, 최대 3박)' }), checkoutIn));
      var paidBox = App.h('label', { class: 'field' }, paidLabel, paidIn);
      /* 사진은 필수(00문서 13절 — 부정 사용 방지) */
      var photoIn = App.h('input', { class: 'input', type: 'file', accept: 'image/jpeg,image/png' });
      var resultBox = App.h('div');
      var pointsBox = App.h('div', { class: 'loading', text: '포인트 내역을 불러오는 중...' });
      var submitBtn = App.h('button', { class: 'btn primary big', type: 'button', text: '인증하기', onclick: submitR });

      wrap.appendChild(App.h('div', { class: 'card' },
        App.h('label', { class: 'field' }, App.h('span', { text: '방문한 장소' }), placeSel),
        App.h('label', { class: 'field' }, App.h('span', { text: '제휴처 (예시)' }), partnerSel),
        App.h('label', { class: 'field' }, App.h('span', { text: '결제 금액 (원)' }), amountIn),
        paidBox,
        stayBox,
        App.h('label', { class: 'field' }, App.h('span', { text: '영수증 사진 (필수 · JPEG/PNG · 5MB 이하)' }), photoIn),
        App.h('p', { class: 'muted small', text: '같은 제휴처·같은 날 두 번째 영수증은 확인 후 적립돼요.' }),
        submitBtn));
      wrap.appendChild(resultBox);
      wrap.appendChild(App.h('div', { class: 'card' },
        App.h('h2', { text: '포인트 캐시백 내역 (예시)' }),
        App.h('p', { class: 'muted small', text: '포인트는 예시이며 현금 가치나 사용처를 약속하지 않아요.' }),
        pointsBox));

      loadPlaces();
      loadPoints();
      placeSel.addEventListener('change', loadPartners);
      partnerSel.addEventListener('change', toggleStay);

      function localNow() {
        var d = new Date();
        function p(n) { return (n < 10 ? '0' : '') + n; }
        return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
          'T' + p(d.getHours()) + ':' + p(d.getMinutes());
      }

      function loadPlaces() {
        App.loadPlaces().then(function (groups) {
          (groups || []).forEach(function (g) {
            (g.places || []).forEach(function (pl) {
              placeSel.appendChild(App.h('option', { value: pl.id, text: pl.name }));
            });
          });
        }).catch(function (e) { App.banner(e.message); });
      }

      function loadPartners() {
        partnerSel.innerHTML = '';
        partnerSel.disabled = true;
        partnerKind = {};
        if (!placeSel.value) {
          partnerSel.appendChild(App.h('option', { value: '', text: '먼저 장소를 고르세요' }));
          toggleStay();
          return;
        }
        partnerSel.appendChild(App.h('option', { value: '', text: '제휴처를 불러오는 중...' }));
        Api.get('/v1/places/' + encodeURIComponent(placeSel.value) + '/partners').then(function (ps) {
          partnerSel.innerHTML = '';
          var arr = ps || [];
          if (!arr.length) {
            partnerSel.appendChild(App.h('option', { value: '', text: '이 장소 주변 제휴처가 없어요 (예시)' }));
            return;
          }
          partnerSel.disabled = false;
          partnerSel.appendChild(App.h('option', { value: '', text: '제휴처를 고르세요' }));
          arr.forEach(function (p) {
            partnerKind[p.id] = p.kind || '';
            var rate = (p.point_rate != null) ? ' · 적립률 ' + Math.round(p.point_rate * 100) + '%' : '';
            var kindLabel = KIND_LABEL[p.kind] || '가게';
            partnerSel.appendChild(App.h('option', { value: p.id, text: (p.name || p.id) + ' (' + kindLabel + ')' + rate }));
          });
        }).catch(function (e) {
          partnerSel.innerHTML = '';
          partnerSel.appendChild(App.h('option', { value: '', text: '제휴처를 불러오지 못했어요' }));
          App.banner(e.message);
        });
      }

      /* 숙소는 결제 일시 대신 숙박 기간(체크인~체크아웃)을 받는다(02문서 11절) */
      function toggleStay() {
        var isLodging = partnerKind[partnerSel.value] === 'lodging';
        stayBox.hidden = !isLodging;
        paidBox.hidden = isLodging;
      }

      /* YYYY-MM-DD 두 날짜의 일수 차(박 수) */
      function nights(a, b) {
        var d1 = new Date(a + 'T00:00:00');
        var d2 = new Date(b + 'T00:00:00');
        if (isNaN(d1.getTime()) || isNaN(d2.getTime())) return NaN;
        return Math.round((d2 - d1) / 86400000);
      }

      function validateStay() {
        var ci = checkinIn.value;
        var co = checkoutIn.value;
        if (!/^\d{4}-\d{2}-\d{2}$/.test(ci) || !/^\d{4}-\d{2}-\d{2}$/.test(co)) {
          return '체크인·체크아웃 날짜를 모두 입력해 주세요. (YYYY-MM-DD)';
        }
        var n = nights(ci, co);
        if (!(n >= 1)) return '체크아웃은 체크인보다 뒤 날짜여야 해요.';
        if (n > 3) return '숙박은 최대 3박까지만 인정돼요.';
        return '';
      }

      function submitR() {
        var partnerId = partnerSel.value;
        var amount = Number(amountIn.value);
        var isLodging = partnerKind[partnerId] === 'lodging';
        if (!partnerId) return App.banner('제휴처를 고르세요.');
        if (!amount || amount < 1 || amount > 1000000 || Math.floor(amount) !== amount) {
          return App.banner('금액은 1원 ~ 100만 원(정수)으로 입력해 주세요.');
        }
        var paidAt;
        if (isLodging) {
          /* 숙소: 숙박 기간 검증. 현재 서버가 paid_at을 필수로 받아 체크인 날짜로 임시 전송한다(README 가정 14) */
          var serr = validateStay();
          if (serr) return App.banner(serr);
          paidAt = checkinIn.value + 'T12:00:00+09:00';
        } else {
          var paid = paidIn.value;
          if (!paid) return App.banner('결제 일시를 확인해 주세요.');
          /* 데모 전제: 한국 시간대. "2026-11-14T10:20" → "...:00+09:00" (README 가정 8 참조) */
          paidAt = paid.length === 16 ? paid + ':00+09:00' : paid;
        }
        /* 사진 필수(00문서 11절): 선택 → 필수로 바뀌었다. 크기·형식 검증은 기존 유지 */
        var file = photoIn.files && photoIn.files[0];
        if (!file) return App.banner('영수증 사진은 필수예요. 사진을 선택해 주세요.');
        var perr = App.checkPhoto(file);
        if (perr) return App.banner(perr);

        var fd = new FormData();
        fd.append('partner_id', partnerId);
        fd.append('amount_krw', String(amount));
        fd.append('paid_at', paidAt);
        if (isLodging) {
          fd.append('stay_checkin', checkinIn.value);
          fd.append('stay_checkout', checkoutIn.value);
        }
        fd.append('photo', file);

        submitBtn.disabled = true;
        submitBtn.textContent = '확인 중...';
        Api.postForm('/v1/receipts', fd).then(function (d) {
          submitBtn.disabled = false;
          submitBtn.textContent = '인증하기';
          showResult(d || {});
          loadPoints();
        }).catch(function (e) {
          submitBtn.disabled = false;
          submitBtn.textContent = '인증하기';
          App.banner(e.message);
        });
      }

      /* 결과 카드: accepted(적립) / pending(확인 대기) / rejected(사유별 한 줄) */
      function showResult(d) {
        resultBox.innerHTML = '';
        var status = d.status;
        var head, line, cls;
        if (status === 'accepted') {
          head = '포인트 캐시백 결과 (예시)';
          line = '적립 완료! ' + App.fmtNum(d.points) + '점이 캐시백됐어요.';
          cls = 'ok';
        } else if (status === 'pending') {
          head = '확인 대기';
          line = PENDING_LINE;
          cls = 'wait';
        } else {
          head = '인증 결과';
          line = REJECT_MSG[d.reason] || ('영수증을 받아들이지 못했어요.' + (d.reason ? ' (사유: ' + d.reason + ')' : ''));
          cls = 'fail';
        }
        resultBox.appendChild(App.h('div', { class: 'card slim result-card ' + cls },
          App.h('b', { text: head }),
          App.h('p', { class: 'result-line', text: line }),
          App.h('p', { class: 'muted small', text: '예시(시뮬레이션) — 실제 결제·포인트와 연결되지 않아요.' })));
      }

      function loadPoints() {
        Api.get('/v1/families/me/points').then(function (d) {
          pointsBox.innerHTML = '';
          var ledger, total;
          if (Array.isArray(d)) {
            ledger = d;
          } else {
            /* 실서버는 { total, entries } — 과거 키(ledger/history/items)도 방어적으로 읽는다 */
            ledger = (d && (d.entries || d.ledger || d.history || d.items)) || [];
            total = d ? (d.total != null ? d.total : d.points_total) : null;
          }
          if (total == null) {
            total = 0;
            ledger.forEach(function (l) { total += Number(l.points || 0); });
          }
          pointsBox.appendChild(App.h('p', { class: 'points-total' },
            '합계 ', App.h('b', { text: App.fmtNum(total) + '점' }), ' (예시)'));
          if (!ledger.length) {
            pointsBox.appendChild(App.h('p', { class: 'muted small', text: '아직 적립 내역이 없어요.' }));
          }
          ledger.forEach(function (l) {
            pointsBox.appendChild(App.h('div', { class: 'ledger-row' },
              App.h('b', { text: '+' + App.fmtNum(l.points) + '점' }),
              App.h('span', { class: 'muted small grow', text: (l.reason || '') + ' · ' + App.fmtDT(l.created_at) })));
          });
        }).catch(function (e) {
          pointsBox.innerHTML = '';
          pointsBox.appendChild(App.h('p', { class: 'muted small', text: '포인트 내역을 불러오지 못했어요.' }));
          App.banner(e.message);
        });
      }
    }
  };
})();
