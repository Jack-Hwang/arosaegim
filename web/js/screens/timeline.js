/* 우리 가족 타임라인 (02문서 5절 4번)
 * 방문·기록·맛집을 시간순(최신이 위)으로 보여 주고,
 * 항목을 누르면 '그때와 지금' 상세(GET /v1/places/{id}/then-now)를 연다. */
(function () {
  'use strict';
  var KIND_LABEL = { visit: '방문', memory: '기록', food: '맛집', food_note: '맛집' };

  Screens.timeline = {
    sub: '타임라인',
    render: function (root) {
      var wrap = App.h('div', { class: 'page' });
      root.appendChild(wrap);
      wrap.appendChild(App.h('div', { class: 'page-head' },
        App.h('h1', { class: 'page-title', text: '우리 가족 타임라인' }),
        App.h('button', { class: 'btn ghost small', type: 'button', text: '새로고침',
          onclick: function () { Screens.timeline.render(root); } })));
      var list = App.h('div', { class: 'loading', text: '불러오는 중...' });
      wrap.appendChild(list);

      Api.get('/v1/families/me/timeline').then(function (items) {
        list.innerHTML = '';
        var arr = (items || []).slice().sort(function (a, b) {
          return String(b.visited_at || '').localeCompare(String(a.visited_at || ''));
        });
        if (!arr.length) {
          list.appendChild(App.h('p', { class: 'muted', text: '아직 기록이 없어요. 장소를 방문하거나 그때 기록을 남겨 보세요.' }));
        }
        arr.forEach(function (t) { list.appendChild(row(t)); });
      }).catch(function (e) {
        list.innerHTML = '';
        list.appendChild(App.h('button', { class: 'btn outline', type: 'button', text: '다시 시도',
          onclick: function () { Screens.timeline.render(root); } }));
        App.banner(e.message);
      });

      function row(t) {
        var kind = KIND_LABEL[t.kind] || t.kind || '기록';
        var title = t.place_name || t.name || t.title || t.place_id || '(항목)';
        var sub = App.fmtDT(t.visited_at);
        if (t.vehicle_label) sub += ' · ' + t.vehicle_label;
        if (t.note) sub += ' · ' + App.trimNote(t.note, 40);
        return App.h('button', { class: 'card slim row-btn', type: 'button', onclick: function () { openThenNow(t); } },
          App.h('span', { class: 'badge kind', text: kind }),
          App.h('div', { class: 'grow' },
            App.h('b', { text: title }),
            App.h('p', { class: 'muted small', text: sub })));
      }

      function openThenNow(t) {
        if (!t.place_id) {
          App.banner('이 항목에는 장소 정보가 없어요.');
          return;
        }
        var body = App.h('div', { class: 'loading', text: '불러오는 중...' });
        App.modal('그때와 지금', body);
        Api.get('/v1/places/' + encodeURIComponent(t.place_id) + '/then-now').then(function (d) {
          body.innerHTML = '';
          fill(body, d || {});
        }).catch(function (e) {
          body.innerHTML = '';
          body.appendChild(App.h('p', { class: 'muted', text: e.message }));
          App.banner(e.message);
        });
      }

      function fill(body, d) {
        var past = d.past;
        var now = d.now || {};
        if (!past) {
          body.appendChild(App.h('div', { class: 'card slim' },
            App.h('b', { text: '첫 방문이에요' }),
            App.h('p', { class: 'muted small', text: '이 기록이 다음 방문의 “그때”가 돼요.' })));
        } else {
          var head = '그때' + (past.year ? ' · ' + past.year + '년' : '') +
            (past.years_ago != null ? ' (' + past.years_ago + '년 전)' : '');
          var src = past.photo_url || '';
          body.appendChild(App.h('div', { class: 'card slim' },
            App.h('b', { text: head }),
            src ? App.h('img', { class: 'photo', alt: '그때 사진',
              src: (src.indexOf('http') === 0 ? '' : Api.base()) + src }) : null,
            past.note ? App.h('p', { text: past.note }) : null,
            App.h('p', { class: 'muted small', text: pastLine(past) })));
        }
        body.appendChild(App.h('div', { class: 'card slim' },
          App.h('b', { text: '지금' }),
          App.h('p', { class: 'muted small', text: App.fmtDT(now.visited_at) || '최근 방문 기록이 없어요.' }),
          now.vehicle_label ? App.h('p', { class: 'muted small', text: '차: ' + now.vehicle_label }) : null,
          now.party_size != null ? App.h('p', { class: 'muted small', text: '함께 ' + now.party_size + '명' }) : null));
      }

      function pastLine(p) {
        var parts = [];
        if (p.vehicle_label) parts.push('차: ' + p.vehicle_label);
        if (p.party_size != null) parts.push('함께 ' + p.party_size + '명');
        return parts.join(' · ');
      }
    }
  };
})();
