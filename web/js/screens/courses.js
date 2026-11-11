/* 코스 보기 (02문서 5절 2번) — 코스·장소 목록, 방문(스탬프) 표시.
 * 방문 여부는 GET /v1/families/me/timeline 의 place_id와 대조한다.
 * 아래에는 스탬프북 진행(예시)도 함께 보여 준다(02문서 6절). */
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

      function loadCoursePlaces(id, det, visited) {
        det.appendChild(App.h('p', { class: 'loading', text: '장소를 불러오는 중...' }));
        Api.get('/v1/courses/' + encodeURIComponent(id)).then(function (d) {
          det.innerHTML = '';
          var places = (d && d.places) || [];
          if (!places.length) det.appendChild(App.h('p', { class: 'muted', text: '장소 정보가 없어요.' }));
          places.forEach(function (p, i) {
            det.appendChild(App.h('div', { class: 'place-row' },
              App.h('span', { class: 'seq', text: String(i + 1) }),
              App.h('div', { class: 'grow' },
                App.h('b', { text: p.name }),
                p.address ? App.h('p', { class: 'muted small', text: p.address }) : null),
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
