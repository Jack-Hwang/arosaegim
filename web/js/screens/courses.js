/* 코스 보기 (02문서 5절 2번) — 코스·장소 목록, 방문(스탬프) 표시.
 * 방문 여부는 GET /v1/families/me/timeline 의 place_id와 대조한다.
 * 아래에는 스탬프북 진행(예시)도 함께 보여 준다(02문서 6절).
 * 검증 전 장소 주소는 시드 플래그 문구 "(주소 검증 필요)"를 그대로 보이지 않게 하고
 * "주소는 확인 중이에요" 안내 한 줄로 바꿔 표시한다(09문서 8번, 2026-11-12). */
(function () {
  'use strict';
  Screens.courses = {
    sub: '코스',
    render: function (root) {
      var wrap = App.h('div', { class: 'page' });
      root.appendChild(wrap);
      wrap.appendChild(App.h('h1', { class: 'page-title', text: '코스 보기' }));
      var list = App.h('div', { class: 'loading', text: '코스를 불러오는 중...' });
      var bookBox = App.h('div');
      wrap.appendChild(list);
      wrap.appendChild(bookBox);
      load();

      /* 다시 시도도 같은 목록 컨테이너만 다시 채운다 — 페이지를 새로 붙이면 화면이 중복된다 */
      function load() {
        list.innerHTML = '';
        list.className = 'loading';
        list.textContent = '코스를 불러오는 중...';
        Promise.all([Api.get('/v1/courses'), Api.get('/v1/families/me/timeline')]).then(function (res) {
          var visited = {};
          (res[1] || []).forEach(function (t) {
            if (t && t.place_id) visited[t.place_id] = true;
          });
          list.innerHTML = '';
          list.className = '';
          var courses = res[0] || [];
          if (!courses.length) list.appendChild(App.h('p', { class: 'muted', text: '등록된 코스가 아직 없어요.' }));
          courses.forEach(function (c) { list.appendChild(courseCard(c, visited)); });
          loadStampBooks();
        }).catch(function (e) {
          list.innerHTML = '';
          list.className = '';
          list.appendChild(App.h('button', { class: 'btn outline', type: 'button', text: '다시 시도',
            onclick: load }));
          App.banner(e.message);
        });
      }

      function courseCard(c, visited) {
        var det = App.h('div', { class: 'course-places' });
        det.hidden = true;
        var mark = App.h('span', { class: 'toggle-mark', text: '열기' });
        var open = false;
        function toggle() {
          open = !open;
          det.hidden = !open;
          mark.textContent = open ? '닫기' : '열기';
          if (open && !det.hasChildNodes()) loadCoursePlaces(c.id, det, visited);
        }
        return App.h('div', { class: 'card' },
          App.h('button', { class: 'course-head', type: 'button', onclick: toggle },
            App.h('div', { class: 'grow' },
              App.h('h2', { text: c.title }),
              c.summary ? App.h('p', { class: 'muted small', text: c.summary }) : null),
            mark),
          det);
      }

      /* (주소 검증 필요)는 시드의 개발 플래그 문구라 사용자 화면에 그대로 내보내지 않는다(09문서 8번).
       * 주소 본문은 남기고, 검증 전 장소는 주소 줄 아래 사용자 말투 안내 한 줄로 바꿔 보여 준다.
       * 주소가 플래그 문구뿐이면 주소 줄은 비어 보이지 않게 안내만 나온다.
       * 서버·시드가 문구를 없애면 안내 없이 조용히 통과한다(가짜 동작 없음). */
      function addressNote(p) {
        var addr = (p.address || '').trim();
        var checking = addr.indexOf('(주소 검증 필요)') !== -1;
        if (checking) addr = addr.split('(주소 검증 필요)').join('').trim();
        return { addr: addr, checking: checking };
      }

      function loadCoursePlaces(id, det, visited) {
        det.appendChild(App.h('p', { class: 'loading', text: '장소를 불러오는 중...' }));
        Api.get('/v1/courses/' + encodeURIComponent(id)).then(function (d) {
          det.innerHTML = '';
          var places = (d && d.places) || [];
          if (!places.length) det.appendChild(App.h('p', { class: 'muted', text: '장소 정보가 없어요.' }));
          places.forEach(function (p, i) {
            var a = addressNote(p);
            det.appendChild(App.h('div', { class: 'place-row' },
              App.h('span', { class: 'seq', text: String(i + 1) }),
              App.h('div', { class: 'grow' },
                App.h('b', { text: p.name }),
                a.addr ? App.h('p', { class: 'muted small', text: a.addr }) : null,
                a.checking ? App.h('p', { class: 'muted small', text: '주소는 확인 중이에요' }) : null),
              visited[p.id]
                ? App.h('span', { class: 'stamp on', text: '스탬프 ✓' })
                : App.h('span', { class: 'stamp', text: '미방문' })));
          });
        }).catch(function (e) {
          det.innerHTML = '';
          det.appendChild(App.h('p', { class: 'muted', text: e.message }));
          App.banner(e.message);
        });
      }

      function loadStampBooks() {
        Api.get('/v1/families/me/stamp-books').then(function (books) {
          bookBox.innerHTML = '';
          (books || []).forEach(function (b) {
            var done = (b.stamped_count != null) ? b.stamped_count : b.stamped;
            var total = (b.total_count != null) ? b.total_count : b.total;
            var prog = (done != null && total) ? ' · ' + done + '/' + total + ' 완료' : '';
            bookBox.appendChild(App.h('div', { class: 'card slim' },
              App.h('b', { text: '스탬프북 · ' + (b.title || b.id || '') }),
              App.h('p', { class: 'muted small', text: (b.region ? b.region + ' · ' : '') + '완성 배지: ' + (b.badge || '-') + prog + ' (예시)' })));
          });
        }).catch(function (e) { App.banner(e.message); });
      }
    }
  };
})();
