/* 그때 기록하기 (02문서 5절 3번 · 10절)
 * 장소·연도·메모(200자)·차량 라벨(선택)·party_size(그때 함께 간 인원, 선택)·사진(선택)
 * → POST /v1/memories (multipart).
 * 업로드 전 안내 문구 + 동의 체크박스 필수.
 * 보호자 동의 전인 미성년 구성원이 있으면 안내를 표시한다(02문서 9절).
 * B5 사진 속 사람 이름 붙이기(07문서 B5): 사진을 고르면 미리보기가 뜨고,
 *   사진 위를 눌러 점을 찍어 사람마다 이름(별칭)을 적는다. 점 좌표는 사진 크기 대비 비율(0~1).
 *   저장은 기존 multipart에 'markers' 필드(JSON 문자열: [{x,y,name}])를 하나만 더한다 —
 *   기존 요청 형식은 그대로, 서버 미반영이면 필드는 무시된다.
 *   얼굴 자동 인식·외부 라이브러리는 일절 없다. 이름은 가족이 직접 적는다(실명 금지).
 *   표시(차량 화면)는 서버·앱 담당 — 웹은 저장만 한다.
 * 음성 편지 업로드는 이번 범위에 없다(로드맵 — 02문서 10절). */
(function () {
  'use strict';
  var MARKER_MAX = 10; // 사진 한 장에 붙일 수 있는 사람 표시 상한(화면 보호용)

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

      /* B5 사진 속 사람 표시 상태 — { x, y, name }, x·y는 사진 크기 대비 0~1 비율 */
      var markers = [];
      var markArea = App.h('div');      // 미리보기 + 안내 + 칩이 붙는 자리
      var markBox = null;               // 사진과 점을 담는 position:relative 컨테이너
      var markImg = null;               // 미리보기 <img>
      var chipBox = App.h('div', { class: 'marker-chips' });
      var markerModal = null;           // 열려 있는 이름 입력 모달(사진이 바뀌면 닫는다)
      photoIn.addEventListener('change', onPhotoChange);

      var formCard = App.h('div', { class: 'card' },
        App.h('label', { class: 'field' }, App.h('span', { text: '장소' }), placeSel),
        App.h('label', { class: 'field' }, App.h('span', { text: '그때 연도' }), yearIn),
        App.h('label', { class: 'field' }, App.h('span', { text: '메모 (200자)' }), noteIn, counter),
        App.h('label', { class: 'field' }, App.h('span', { text: '차량 라벨 (선택)' }), carIn),
        App.h('label', { class: 'field' }, App.h('span', { text: '그때 함께 간 인원 (선택)' }), partySel),
        App.h('label', { class: 'field' }, App.h('span', { text: '사진 (선택 · JPEG/PNG · 5MB 이하)' }), photoIn),
        markArea);
      wrap.appendChild(formCard);

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

      /* ── B5: 사진 속 사람 이름 붙이기 ─────────────────────────────
       * 사진을 고르면 미리보기를 보여 주고, 사진 위를 누른 자리에 점을 찍어 이름을 붙인다.
       * 점 좌표는 img 크기에 대한 비율(0~1)로만 계산·저장한다(픽셀 좌표는 남기지 않는다).
       * 점을 다시 누르면 이름을 고치거나 점을 지울 수 있다. */
      function clamp01(v) {
        v = Number(v);
        if (isNaN(v)) return 0;
        return Math.min(1, Math.max(0, v));
      }

      function onPhotoChange() {
        if (markerModal) { markerModal.close(); markerModal = null; }
        markers = [];
        markBox = null;
        markImg = null;
        markArea.innerHTML = '';
        chipBox.innerHTML = '';
        var file = photoIn.files && photoIn.files[0];
        if (!file) return;
        var perr = App.checkPhoto(file);
        if (perr) { App.banner(perr); return; } // 저장 버튼에서도 한 번 더 검사한다

        markImg = App.h('img', { class: 'photo-mark-img', alt: '올릴 사진 미리보기' });
        var url = URL.createObjectURL(file);
        markImg.onload = function () { URL.revokeObjectURL(url); };
        markImg.onerror = function () { URL.revokeObjectURL(url); };
        markImg.addEventListener('click', function (ev) {
          var r = markImg.getBoundingClientRect();
          if (!r.width || !r.height) return;
          addMarker(clamp01((ev.clientX - r.left) / r.width), clamp01((ev.clientY - r.top) / r.height));
        });
        markBox = App.h('div', { class: 'photo-mark' }, markImg);
        markArea.appendChild(App.h('p', { class: 'muted small', text: '사진 위를 눌러 사람이 있는 자리에 점을 찍고 이름을 붙여 보세요 (선택). 얼굴을 자동으로 찾지 않고, 가족이 직접 적은 이름만 저장해요.' }));
        markArea.appendChild(markBox);
        markArea.appendChild(chipBox);
        markImg.src = url;
        renderMarkers();
      }

      function addMarker(x, y) {
        if (markers.length >= MARKER_MAX) return App.banner('사람 표시는 ' + MARKER_MAX + '개까지예요.');
        markerModal = openMarkerForm('사람 이름 붙이기', '', function (name) {
          markers.push({ x: x, y: y, name: name });
          renderMarkers();
        }, null);
      }

      function editMarker(idx) {
        var mk = markers[idx];
        if (!mk) return;
        markerModal = openMarkerForm('이름 고치기', mk.name, function (name) {
          mk.name = name;
          renderMarkers();
        }, function () {
          markers.splice(idx, 1);
          renderMarkers();
        });
      }

      /* 이름 입력 모달: 새 점(제거 없음)과 고치기(제거 버튼 있음)를 한 형태로 쓴다 */
      function openMarkerForm(title, initial, onSave, onRemove) {
        var nameIn = App.h('input', { class: 'input', type: 'text', maxlength: '20',
          placeholder: '예: 할아버지, 큰아버지 (실명은 쓰지 않아요)' });
        if (initial) nameIn.setAttribute('value', initial);
        var m;
        var body = App.h('div', null,
          App.h('p', { class: 'muted small', text: '이름은 가족이 직접 적어요. 사진 속 사람을 자동으로 찾지 않습니다.' }),
          App.h('label', { class: 'field' }, App.h('span', { text: '이름 (별칭 · 실명 금지)' }), nameIn),
          App.h('div', { class: 'btn-row' },
            onRemove ? App.h('button', { class: 'btn outline', type: 'button', text: '점 지우기', onclick: function () {
              m.close(); onRemove();
            } }) : null,
            App.h('button', { class: 'btn outline', type: 'button', text: '취소', onclick: function () { m.close(); } }),
            App.h('button', { class: 'btn primary', type: 'button', text: '저장', onclick: function () {
              var n = nameIn.value.trim();
              if (!n) return App.banner('이름을 적어 주세요.');
              m.close(); onSave(n);
            } })));
        m = App.modal(title, body);
        setTimeout(function () { try { nameIn.focus(); } catch (e) { /* 포커스 실패는 무시 */ } }, 50);
        return m;
      }

      function renderMarkers() {
        if (!markBox) return;
        var dots = markBox.querySelectorAll('.photo-marker');
        for (var i = 0; i < dots.length; i++) dots[i].remove();
        chipBox.innerHTML = '';
        markers.forEach(function (mk, idx) {
          var dot = App.h('button', {
            class: 'photo-marker', type: 'button',
            'aria-label': (idx + 1) + '번 사람: ' + mk.name,
            style: 'left:' + (mk.x * 100).toFixed(2) + '%;top:' + (mk.y * 100).toFixed(2) + '%;',
            text: String(idx + 1)
          });
          dot.addEventListener('click', function (ev) {
            ev.stopPropagation(); // 사진의 "점 찍기"가 같이 실행되지 않게 한다
            editMarker(idx);
          });
          markBox.appendChild(dot);
          chipBox.appendChild(App.h('button', { class: 'marker-chip', type: 'button',
            'aria-label': (idx + 1) + '번 사람 이름 고치기',
            onclick: function () { editMarker(idx); } },
            App.h('b', { text: String(idx + 1) }), mk.name));
        });
        if (!markers.length) {
          chipBox.appendChild(App.h('span', { class: 'muted small', text: '아직 표시한 사람이 없어요.' }));
        }
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
        /* B5: 사진 속 사람 이름 — 사진이 있고 점을 찍었을 때만 보낸다.
         * multipart 필드 하나(JSON 문자열)라 기존 요청 형식은 그대로다. 좌표는 0~1 비율(소수 셋째 자리). */
        if (file && markers.length) {
          fd.append('markers', JSON.stringify(markers.map(function (mk) {
            return {
              x: Math.round(clamp01(mk.x) * 1000) / 1000,
              y: Math.round(clamp01(mk.y) * 1000) / 1000,
              name: mk.name
            };
          })));
        }

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
