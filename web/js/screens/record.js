/* 그때 기록하기 (02문서 5절 3번 · 10절)
 * 장소·연도·메모(200자)·차량 라벨(선택)·party_size(그때 함께 간 인원, 선택)·사진(선택)
 * → POST /v1/memories (multipart).
 * 업로드 전 안내 문구 + 동의 체크박스 필수.
 * 보호자 동의 전인 미성년 구성원이 있으면 안내를 표시한다(02문서 9절).
 * 음성 편지 업로드는 이번 범위에 없다(로드맵 — 02문서 10절). */
(function () {
  'use strict';
  Screens.record = {
    sub: '기록',
    render: function (root) {
      var wrap = App.h('div', { class: 'page' });
      root.appendChild(wrap);
      wrap.appendChild(App.h('h1', { class: 'page-title', text: '그때 기록하기' }));

      var placeSel = App.h('select', { class: 'input' }, App.h('option', { value: '', text: '장소를 고르세요' }));
      var yearIn = App.h('input', { class: 'input', type: 'number', inputmode: 'numeric',
        min: '1900', max: String(new Date().getFullYear()), placeholder: '예: 1995' });
      var noteIn = App.h('textarea', { class: 'input', maxlength: '200', rows: '4',
        placeholder: '그때의 한 장면을 적어 보세요 (최대 200자)' });
      var counter = App.h('p', { class: 'muted small counter', text: '0/200' });
      noteIn.addEventListener('input', function () { counter.textContent = noteIn.value.length + '/200'; });
      var carIn = App.h('input', { class: 'input', type: 'text', maxlength: '30',
        placeholder: '예: 아버지의 소나타 (선택)' });
      /* party_size: 그때 함께 간 인원(02문서 10절) — 숫자 선택 입력, 없으면 보내지 않는다 */
      var partySel = App.h('select', { class: 'input' });
      partySel.appendChild(App.h('option', { value: '', text: '선택 안 함' }));
      [1, 2, 3, 4, 5, 6, 7, 8].forEach(function (n) {
        partySel.appendChild(App.h('option', { value: String(n), text: n + '명' }));
      });
      var photoIn = App.h('input', { class: 'input', type: 'file', accept: 'image/jpeg,image/png' });
      var agreeChk = App.h('input', { type: 'checkbox' });

      wrap.appendChild(App.h('div', { class: 'card' },
        App.h('label', { class: 'field' }, App.h('span', { text: '장소' }), placeSel),
        App.h('label', { class: 'field' }, App.h('span', { text: '그때 연도' }), yearIn),
        App.h('label', { class: 'field' }, App.h('span', { text: '메모 (200자)' }), noteIn, counter),
        App.h('label', { class: 'field' }, App.h('span', { text: '차량 라벨 (선택)' }), carIn),
        App.h('label', { class: 'field' }, App.h('span', { text: '그때 함께 간 인원 (선택)' }), partySel),
        App.h('label', { class: 'field' }, App.h('span', { text: '사진 (선택 · JPEG/PNG · 5MB 이하)' }), photoIn)));

      var minorSlot = App.h('div');
      wrap.appendChild(minorSlot);

      wrap.appendChild(App.h('div', { class: 'card notice-card' },
        App.h('p', null, App.h('b', { text: '가족 얼굴·이름이 보이는 사진은 가족만 볼 수 있어요.' })),
        App.h('label', { class: 'check-row' }, agreeChk, App.h('span', { text: '위 안내에 동의합니다 (필수)' }))));

      var submit = App.h('button', { class: 'btn primary big', type: 'button', text: '기록 저장하기', onclick: save });
      wrap.appendChild(submit);

      loadPlaceOptions();
      checkMinors();

      function loadPlaceOptions() {
        App.loadPlaces().then(function (groups) {
          (groups || []).forEach(function (g) {
            (g.places || []).forEach(function (p) {
              placeSel.appendChild(App.h('option', { value: p.id, text: g.title + ' · ' + p.name }));
            });
          });
        }).catch(function (e) { App.banner(e.message); });
      }

      function checkMinors() {
        Api.get('/v1/families/me/members').then(function (ms) {
          var need = (ms || []).filter(function (m) { return m && m.is_minor && !m.guardian_consent; });
          if (!need.length) return;
          var names = need.map(function (m) { return m.nickname || '(별칭 없음)'; }).join(', ');
          minorSlot.appendChild(App.h('div', { class: 'card slim warn-card' },
            App.h('b', { text: '보호자 동의가 필요해요' }),
            App.h('p', { class: 'muted small', text: '미성년 구성원(' + names + ')이 찍은 사진은 보호자 동의 후 올릴 수 있어요. 가족 관리 화면에서 보호자 동의를 해 주세요.' })));
        }).catch(function (e) { App.banner(e.message); });
      }

      function save() {
        var placeId = placeSel.value;
        var year = Number(yearIn.value);
        var note = noteIn.value.trim();
        var maxY = new Date().getFullYear();
        if (!placeId) return App.banner('장소를 고르세요.');
        if (!year || year < 1900 || year > maxY) return App.banner('연도를 1900 ~ ' + maxY + ' 사이로 입력해 주세요.');
        if (!note) return App.banner('메모를 적어 주세요.');
        if (!agreeChk.checked) return App.banner('사진 안내에 동의해 주세요.');
        var file = photoIn.files && photoIn.files[0];
        var perr = App.checkPhoto(file);
        if (perr) return App.banner(perr);

        var fd = new FormData();
        fd.append('place_id', placeId);
        fd.append('year', String(year));
        fd.append('note', note);
        if (carIn.value.trim()) fd.append('vehicle_label', carIn.value.trim());
        if (partySel.value) fd.append('party_size', partySel.value); // 02문서 10절 (서버 미반영이면 무시됨)
        if (file) fd.append('photo', file);

        submit.disabled = true;
        submit.textContent = '저장 중...';
        Api.postForm('/v1/memories', fd).then(function () {
          App.banner('기록을 저장했어요.');
          location.hash = '#/timeline';
        }).catch(function (e) {
          submit.disabled = false;
          submit.textContent = '기록 저장하기';
          App.banner(e.message);
        });
      }
    }
  };
})();
