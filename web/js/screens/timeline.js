/* 우리 가족 타임라인 (02문서 5절 4번)
 * 방문·기록·맛집을 시간순(최신이 위)으로 보여 주고,
 * 항목을 누르면 '그때와 지금' 상세(GET /v1/places/{id}/then-now)를 연다.
 * 기획서 v10 B1·B4·B5 필드: 방문 항목의 visit_ordinal(몇 번째 방문)·trip_km(함께 달린 거리),
 * 기록 항목의 year(그때 연도), 가족 누적 거리는 GET /v1/families/me/metrics의
 * family_total_trip_km(없거나 실패하면 조용히 숨긴다 — 가짜 동작 금지).
 * 새로고침은 목록 컨테이너만 비우고 다시 읽는다(페이지를 다시 붙이지 않는다 — 중복 렌더 방지). */
(function () {
  'use strict';
  var KIND_LABEL = { visit: '방문', memory: '기록', food: '맛집', food_note: '맛집' };

  /* B5: 사진 속 사람 이름 — 서버가 markers 배열을 줄 때만 이름을 뽑는다(없으면 빈 목록 → 표시 안 함).
   * 좌표(x·y)는 차량·앱의 표시 몫이라 웹은 이름만 글로 보여 준다. */
  function markerNames(list) {
    var names = [];
    if (Array.isArray(list)) {
      list.forEach(function (mk) {
        if (mk && mk.name) names.push(String(mk.name));
      });
    }
    return names;
  }

  /* trip_km 표시용: 3.2 → "3.2 km", 12 → "12 km". 숫자가 아니면 빈 문자(숨김). */
  function kmLabel(v) {
    var n = Number(v);
    if (v == null || !isFinite(n)) return '';
    return (n % 1 === 0 ? String(n) : String(Math.round(n * 100) / 100)) + ' km';
  }

  Screens.timeline = {
    sub: '타임라인',
    render: function (root) {
      var wrap = App.h('div', { class: 'page' });
      root.appendChild(wrap);
      wrap.appendChild(App.h('div', { class: 'page-head' },
        App.h('h1', { class: 'page-title', text: '우리 가족 타임라인' }),
        App.h('button', { class: 'btn ghost small', type: 'button', text: '새로고침',
          onclick: load })));
      /* 가족 누적 거리(예시): 서버 metrics의 family_total_trip_km. 실패·미제공이면 줄 자체가 없다. */
      var tripLine = App.h('p', { class: 'muted small', hidden: true });
      wrap.appendChild(tripLine);
      loadTripTotal();
      var list = App.h('div', { class: 'loading', text: '불러오는 중...' });
      wrap.appendChild(list);
      load();

      /* 새로고침·다시 시도는 이 함수만 부른다 — 페이지를 새로 붙이지 않고 같은 목록을 채운다 */
      function load() {
        list.innerHTML = '';
        list.className = 'loading';
        list.textContent = '불러오는 중...';
        Api.get('/v1/families/me/timeline').then(function (items) {
          list.innerHTML = '';
          list.className = '';
          var arr = (items || []).slice().sort(function (a, b) {
            return String(b.visited_at || '').localeCompare(String(a.visited_at || ''));
          });
          if (!arr.length) {
            list.appendChild(App.h('p', { class: 'muted', text: '아직 기록이 없어요. 장소를 방문하거나 그때 기록을 남겨 보세요.' }));
          }
          arr.forEach(function (t) { list.appendChild(row(t)); });
        }).catch(function (e) {
          list.innerHTML = '';
          list.className = '';
          list.appendChild(App.h('button', { class: 'btn outline', type: 'button', text: '다시 시도',
            onclick: load }));
          App.banner(e.message);
        });
      }

      /* 가족 누적 주행거리(기획서 v10 B4): metrics가 숫자로 줄 때만 위에 한 줄 표시 */
      function loadTripTotal() {
        Api.get('/v1/families/me/metrics').then(function (m) {
          var km = m ? kmLabel(m.family_total_trip_km) : '';
          if (!km) { tripLine.hidden = true; return; }
          tripLine.textContent = '우리 가족이 함께 달린 거리: ' + km + ' (코스 주행 기준, 예시)';
          tripLine.hidden = false;
        }).catch(function () {
          tripLine.hidden = true; // 지표 조회 실패는 조용히 숨긴다(타임라인 자체는 계속 보임)
        });
      }

      function row(t) {
        var kind = KIND_LABEL[t.kind] || t.kind || '기록';
        var title = t.place_name || t.name || t.title || t.place_id || '(항목)';
        var sub = App.fmtDT(t.visited_at);
        /* 그때 기록의 연도: 서버가 memory 항목에 year를 준다(등록일과 '그때'는 다르다) */
        if (t.year != null && t.year !== '') sub = '그때 ' + t.year + '년 · ' + sub;
        /* 방문 항목: 가족·장소 기준 순번과 이번 코스 주행거리(서버가 줄 때만) */
        if (t.visit_ordinal != null) sub = t.visit_ordinal + '번째 방문 · ' + sub;
        var km = kmLabel(t.trip_km);
        if (km) sub += ' · 함께 달린 거리 ' + km;
        if (t.vehicle_label) sub += ' · ' + t.vehicle_label;
        if (t.note) sub += ' · ' + App.trimNote(t.note, 40);
        var rowWho = markerNames(t.markers); // 기록 항목에 이름표가 있을 때만
        if (rowWho.length) sub += ' · 사진 속: ' + App.trimNote(rowWho.join(', '), 30);
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
          /* B5: 사진 속 사람 이름 — 서버가 past.markers를 줄 때만 글로 나열한다(없으면 숨김) */
          var who = markerNames(past.markers);
          body.appendChild(App.h('div', { class: 'card slim' },
            App.h('b', { text: head }),
            src ? App.h('img', { class: 'photo', alt: '그때 사진',
              src: (src.indexOf('http') === 0 ? '' : Api.base()) + src }) : null,
            who.length ? App.h('p', { class: 'muted small', text: '사진 속 사람: ' + who.join(', ') }) : null,
            past.note ? App.h('p', { text: past.note }) : null,
            App.h('p', { class: 'muted small', text: pastLine(past) })));
        }
        /* 이번 방문: 순번(then-now 응답의 visit_ordinal)·주행거리(now.trip_km)는 서버가 줄 때만 */
        body.appendChild(App.h('div', { class: 'card slim' },
          App.h('b', { text: '지금' + (d.visit_ordinal != null ? ' · ' + d.visit_ordinal + '번째 방문' : '') }),
          App.h('p', { class: 'muted small', text: App.fmtDT(now.visited_at) || '최근 방문 기록이 없어요.' }),
          now.vehicle_label ? App.h('p', { class: 'muted small', text: '차: ' + now.vehicle_label }) : null,
          now.party_size != null ? App.h('p', { class: 'muted small', text: '함께 ' + now.party_size + '명' }) : null,
          kmLabel(now.trip_km) ? App.h('p', { class: 'muted small', text: '함께 달린 거리 ' + kmLabel(now.trip_km) }) : null));
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
