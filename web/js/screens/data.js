/* 가족 데이터 · 맛집 기록 (02문서 3-1 · 6절 · 9절)
 * 가족 관리 화면 아래에 붙는 카드들:
 * 기록 전체 내려받기(ZIP) · 가족 코드 재발급 · 맛집 기록 추가 · 가족 데이터 삭제(이중 확인). */
(function () {
  'use strict';
  Screens.familyData = {
    render: function (root) {
      root.appendChild(exportCard());
      root.appendChild(rotateCard());
      root.appendChild(foodCard());
      root.appendChild(deleteCard());
    }
  };

  function exportCard() {
    var btn = App.h('button', { class: 'btn primary', type: 'button', text: '기록 전체 내려받기 (ZIP)', onclick: run });
    return App.h('div', { class: 'card' },
      App.h('h2', { text: '기록 전체 내려받기' }),
      App.h('p', { class: 'muted small', text: '방문·기록·사진·맛집을 ZIP 한 파일로 내려받아요. 서버가 사라져도 열어 볼 수 있어요.' }),
      btn);

    function run() {
      btn.disabled = true;
      btn.textContent = '준비 중...';
      Api.raw('/v1/families/me/export').then(function (res) {
        if (!res.ok) throw { status: res.status, message: '내려받기를 준비하지 못했어요. (' + res.status + ')' };
        return res.blob();
      }).then(function (blob) {
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'family-export.zip';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
        App.banner('내려받기를 시작했어요.');
      }).catch(function (e) {
        App.banner(e && e.message ? e.message : '내려받기에 실패했어요.');
      }).then(function () {
        btn.disabled = false;
        btn.textContent = '기록 전체 내려받기 (ZIP)';
      });
    }
  }

  function rotateCard() {
    return App.h('div', { class: 'card' },
      App.h('h2', { text: '가족 코드 재발급' }),
      App.h('p', { class: 'muted small', text: '새 코드와 PIN을 발급해요. 기존 코드로는 더 이상 들어올 수 없어요.' }),
      App.h('button', { class: 'btn outline', type: 'button', text: '재발급하기', onclick: run }));

    function run() {
      App.confirmDanger('가족 코드 재발급', [
        '새 가족 코드와 PIN을 발급해요.',
        '기존 코드로는 더 이상 접근할 수 없어요. 가족에게 새 코드를 알려 주세요.'
      ], '재발급', function () {
        Api.post('/v1/families/me/rotate-code').then(function (d) {
          d = d || {};
          Api.saveAuth(d.family_code || Api.code(), d.pin || Api.pin());
          App.modal('새 가족 코드', App.h('div', null,
            d.family_code ? App.h('p', { class: 'code-line big' }, '새 코드 ', App.h('b', { text: d.family_code })) : null,
            d.pin ? App.h('p', { class: 'code-line big' }, '새 PIN ', App.h('b', { text: d.pin })) : null,
            App.h('p', { class: 'muted small', text: '안전한 곳에 적어 두세요. 이 기기에도 저장했어요.' })));
          App.banner('가족 코드를 재발급했어요.');
        }).catch(function (e) { App.banner(e.message); });
      });
    }
  }

  function foodCard() {
    var visitSel = App.h('select', { class: 'input' }, App.h('option', { value: '', text: '방문을 불러오는 중...' }));
    var nameIn = App.h('input', { class: 'input', type: 'text', maxlength: '30', placeholder: '예: 대릉원 앞 쌈밥집' });
    var noteIn = App.h('input', { class: 'input', type: 'text', maxlength: '100', placeholder: '한 줄 메모 (선택)' });
    var photoIn = App.h('input', { class: 'input', type: 'file', accept: 'image/jpeg,image/png' });
    loadVisits();
    return App.h('div', { class: 'card' },
      App.h('h2', { text: '맛집 기록 추가' }),
      App.h('p', { class: 'muted small', text: '방문했던 장소 근처에서 먹은 맛집을 기록해요 (예시).' }),
      App.h('label', { class: 'field' }, App.h('span', { text: '어떤 방문과 함께할까요' }), visitSel),
      App.h('label', { class: 'field' }, App.h('span', { text: '가게 이름' }), nameIn),
      App.h('label', { class: 'field' }, App.h('span', { text: '메모 (선택)' }), noteIn),
      App.h('label', { class: 'field' }, App.h('span', { text: '사진 (선택 · JPEG/PNG · 5MB 이하)' }), photoIn),
      App.h('button', { class: 'btn primary', type: 'button', text: '맛집 기록 저장', onclick: save }));

    function loadVisits() {
      Api.get('/v1/families/me/timeline').then(function (items) {
        visitSel.innerHTML = '';
        var any = false;
        (items || []).forEach(function (t) {
          if (t.kind !== 'visit' && t.kind !== 'memory') return;
          var vid = (t.visit_id != null) ? t.visit_id : t.id; // visit_id 포함 가정 — README 참조
          if (vid == null) return;
          any = true;
          visitSel.appendChild(App.h('option', { value: String(vid),
            text: (t.place_name || t.place_id || '') + ' · ' + App.fmtDT(t.visited_at) }));
        });
        if (!any) {
          visitSel.appendChild(App.h('option', { value: '', text: '방문 기록이 필요해요. 먼저 장소를 방문해 주세요.' }));
        }
      }).catch(function (e) {
        visitSel.innerHTML = '';
        visitSel.appendChild(App.h('option', { value: '', text: '방문 목록을 불러오지 못했어요' }));
        App.banner(e.message);
      });
    }

    function save() {
      if (!visitSel.value) return App.banner('방문 기록이 필요해요. 먼저 장소를 방문해 주세요.');
      var name = nameIn.value.trim();
      if (!name) return App.banner('가게 이름을 입력해 주세요.');
      var file = photoIn.files && photoIn.files[0];
      var perr = App.checkPhoto(file);
      if (perr) return App.banner(perr);
      var fd = new FormData();
      fd.append('visit_id', visitSel.value);
      fd.append('name', name);
      if (noteIn.value.trim()) fd.append('note', noteIn.value.trim());
      if (file) fd.append('photo', file);
      Api.postForm('/v1/families/me/food-notes', fd).then(function () {
        App.banner('맛집 기록을 저장했어요.');
        nameIn.value = '';
        noteIn.value = '';
        photoIn.value = '';
      }).catch(function (e) { App.banner(e.message); });
    }
  }

  function deleteCard() {
    return App.h('div', { class: 'card danger-card' },
      App.h('h2', { text: '가족 데이터 삭제' }),
      App.h('p', { class: 'muted small', text: '이 가족의 방문·기록·사진·맛집·포인트 내역을 모두 삭제해요. 개인정보 안내 동의도 초기화돼요.' }),
      App.h('button', { class: 'btn danger', type: 'button', text: '가족 데이터 전체 삭제', onclick: run }));

    function run() {
      App.confirmDanger('가족 데이터 삭제', [
        '이 가족의 모든 기록(방문·사진·메모·맛집·포인트)을 삭제해요.',
        '되돌릴 수 없습니다.'
      ], '삭제', function () {
        Api.del('/v1/families/me').then(function () {
          Api.clearAuth();
          App.setConsent('');
          App.invalidatePlaces();
          App.banner('가족 데이터를 삭제했어요.');
          location.hash = '#/home';
        }).catch(function (e) { App.banner(e.message); });
      });
    }
  }
})();
