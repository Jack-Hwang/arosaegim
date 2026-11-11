/* 가족 관리 — 구성원 · 계승 (02문서 5절 5번 · 3-1절 · 9절 · 10절)
 * 구성원 목록·추가(별칭만, 실명 금지), 미성년 보호자 동의(별도 확인 화면),
 * 이어받을 사람 지정(owner만 — 서버가 권한 검사), 관리 권한 이전(이중 확인 + 법적 상속 아님 문구).
 * T9-3 오늘의 자리 이름표: 구성원마다 seat_role(driver/navigator/explorer)을 골라 저장한다.
 *   저장은 PATCH /v1/families/me/members/{id} 우선, 없으면 POST .../{id}/seat-role 폴백(README 가정 12).
 *   차량 정차 화면이 이 값을 이름표로 쓴다(표기 이름은 시드 seat_roles).
 * B4 인원 비교 문장 접기 되돌리기: '그때와 지금' 카드의 인원 비교 문장을 접어 둔 기록을
 *   여기서 되돌린다(07문서 B4). GET /v1/families/me/settings로 조회하고
 *   PUT /v1/families/me/settings(전체 접기 스위치), DELETE /v1/families/me/folded-places/{place_id}(장소별 해제).
 *   서버가 두 필드를 주지 않으면(미대응) 이 UI를 아예 숨긴다(가짜 동작 금지).
 * 내려받기·코드 재발급·삭제·맛집 기록은 screens/data.js가 이 화면 아래에 붙는다. */
(function () {
  'use strict';
  /* 시드 seat_roles 표기 이름과 02문서 10절 저장 값의 짝 */
  var SEAT_ROLES = [
    { v: 'driver', label: '운전석' },
    { v: 'navigator', label: '조수석 길잡이' },
    { v: 'explorer', label: '뒷좌석 탐험가' }
  ];
  function seatLabel(v) {
    for (var i = 0; i < SEAT_ROLES.length; i++) {
      if (SEAT_ROLES[i].v === v) return SEAT_ROLES[i].label;
    }
    return '';
  }

  Screens.family = {
    sub: '가족',
    render: function (root) {
      var wrap = App.h('div', { class: 'page' });
      root.appendChild(wrap);
      wrap.appendChild(App.h('h1', { class: 'page-title', text: '가족 관리' }));

      var membersBox = App.h('div');
      wrap.appendChild(membersBox);
      loadMembers();

      /* B4: 인원 비교 문장 접기 설정(전체 스위치 + 장소별 되돌리기). 서버 미대응이면 숨긴다. */
      var foldBox = App.h('div');
      wrap.appendChild(foldBox);
      loadFoldSettings();

      var dataBox = App.h('div');
      wrap.appendChild(dataBox);
      if (Screens.familyData) Screens.familyData.render(dataBox);

      function loadMembers() {
        membersBox.innerHTML = '';
        membersBox.appendChild(App.h('p', { class: 'loading', text: '구성원을 불러오는 중...' }));
        Api.get('/v1/families/me/members').then(function (ms) {
          membersBox.innerHTML = '';
          build(membersBox, ms || []);
        }).catch(function (e) {
          membersBox.innerHTML = '';
          membersBox.appendChild(App.h('button', { class: 'btn outline', type: 'button', text: '다시 시도', onclick: loadMembers }));
          App.banner(e.message);
        });
      }

      function build(box, members) {
        box.appendChild(App.h('h2', { class: 'sec-title', text: '구성원 (별칭만 사용해요)' }));
        box.appendChild(App.h('p', { class: 'muted small', text: '구성원마다 오늘의 자리를 고르면 차량 정차 화면에 이름표로 보여요(예시).' }));
        if (!members.length) {
          box.appendChild(App.h('p', { class: 'muted', text: '구성원이 아직 없어요. 아래에서 추가해 보세요.' }));
        }
        members.forEach(function (m) { box.appendChild(memberCard(m)); });
        box.appendChild(addCard());
        box.appendChild(heirCard(members));
        box.appendChild(handoverCard(members));
      }

      function memberCard(m) {
        var badges = App.h('div', { class: 'badge-row' },
          App.h('span', { class: 'badge', text: App.genLabel(m.generation) }),
          m.role === 'owner' ? App.h('span', { class: 'badge owner', text: '관리자' }) : null,
          m.role === 'heir' ? App.h('span', { class: 'badge heir', text: '이어받을 사람' }) : null);
        if (m.seat_role) {
          badges.appendChild(App.h('span', { class: 'badge sim', text: '자리: ' + (seatLabel(m.seat_role) || m.seat_role) }));
        }
        if (m.is_minor) {
          badges.appendChild(App.h('span', { class: 'badge', text: '미성년' }));
          badges.appendChild(App.h('span', { class: 'badge ' + (m.guardian_consent ? 'ok' : 'warn'),
            text: m.guardian_consent ? '보호자 동의됨' : '보호자 동의 필요' }));
        }

        /* T9-3 오늘의 자리 이름표: 저장 값은 driver/navigator/explorer, 화면 표기는 시드 이름 */
        var seatSel = App.h('select', { class: 'input seat-select' });
        seatSel.appendChild(App.h('option', { value: '', text: '자리 선택 안 함' }));
        SEAT_ROLES.forEach(function (s) {
          seatSel.appendChild(App.h('option', { value: s.v, text: s.label }));
        });
        seatSel.value = m.seat_role || '';

        return App.h('div', { class: 'card slim member-block' },
          App.h('div', { class: 'member' },
            App.h('div', { class: 'grow' },
              App.h('b', { text: m.nickname || '(별칭 없음)' }),
              badges),
            (m.is_minor && !m.guardian_consent)
              ? App.h('button', { class: 'btn small', type: 'button', text: '보호자 동의', onclick: function () { askConsent(m); } })
              : null),
          App.h('div', { class: 'seat-row' },
            App.h('span', { class: 'muted small seat-label', text: '오늘의 자리' }),
            seatSel,
            App.h('button', { class: 'btn small outline', type: 'button', text: '자리 저장',
              onclick: function () { saveSeat(m, seatSel.value); } })));
      }

      /* seat_role 저장: PATCH가 없으면(404/405/501) seat-role 전용 POST로 폴백.
       * 서버가 아직 미반영이어도 오류 배너 한 줄로 흐름이 깨지지 않는다(README 가정 12). */
      function saveSeat(m, role) {
        var base = '/v1/families/me/members/' + encodeURIComponent(m.id);
        var body = { seat_role: role ? role : null }; // 빈 선택 = 해제(null)
        Api.patch(base, body).catch(function (e) {
          if (e.status === 404 || e.status === 405 || e.status === 501) {
            return Api.post(base + '/seat-role', body);
          }
          throw e;
        }).then(function () {
          App.banner(role ? '자리 역할을 저장했어요.' : '자리 역할을 지웠어요.');
          loadMembers();
        }).catch(function (e) {
          App.banner(e.message);
        });
      }

      /* 보호자 동의는 별도 확인 화면(닫힘 버튼 없는 모달)에서만 진행 — 02문서 9절 */
      function askConsent(m) {
        var m2;
        m2 = App.modal('보호자 동의', App.h('div', null,
          App.h('p', { text: '“' + (m.nickname || '이 구성원') + '”과 관련된 사진·음성 업로드를 허용할까요?' }),
          App.h('p', { class: 'muted small', text: '보호자만 동의해 주세요. 동의한 시각이 기록돼요.' }),
          App.h('div', { class: 'btn-row' },
            App.h('button', { class: 'btn outline', type: 'button', text: '취소', onclick: function () { m2.close(); } }),
            App.h('button', { class: 'btn primary', type: 'button', text: '보호자로서 동의', onclick: function () {
              m2.close();
              Api.post('/v1/families/me/members/' + encodeURIComponent(m.id) + '/consent').then(function () {
                App.banner('보호자 동의를 저장했어요.');
                loadMembers();
              }).catch(function (e) { App.banner(e.message); });
            } }))), { closable: false });
      }

      function addCard() {
        var nick = App.h('input', { class: 'input', type: 'text', maxlength: '20', placeholder: '예: 외할아버지, 막내 (실명 금지)' });
        /* 세대는 서버가 문자 라벨로 받는다(MemberIn.generation: str) — 라벨을 그대로 보낸다 */
        var gen = App.h('select', { class: 'input' });
        ['조부모대', '부모대', '자녀대', '손주대'].forEach(function (label) {
          gen.appendChild(App.h('option', { value: label, text: label }));
        });
        gen.value = '부모대';
        /* T9-3: 추가할 때부터 자리를 고를 수 있다(서버가 구성원 생성에 seat_role을 받는다).
         * 이후 변경은 PATCH /seat-role 폴백 경로(saveSeat)를 쓴다. */
        var seatSel = App.h('select', { class: 'input' });
        seatSel.appendChild(App.h('option', { value: '', text: '자리 선택 안 함' }));
        SEAT_ROLES.forEach(function (s) {
          seatSel.appendChild(App.h('option', { value: s.v, text: s.label }));
        });
        var minor = App.h('input', { type: 'checkbox' });
        return App.h('div', { class: 'card' },
          App.h('h2', { text: '구성원 추가' }),
          App.h('label', { class: 'field' }, App.h('span', { text: '별칭 (실명·연락처 금지)' }), nick),
          App.h('label', { class: 'field' }, App.h('span', { text: '세대' }), gen),
          App.h('label', { class: 'field' }, App.h('span', { text: '오늘의 자리 (차량 화면 이름표, 선택)' }), seatSel),
          App.h('label', { class: 'check-row' }, minor, App.h('span', { text: '미성년이에요' })),
          App.h('button', { class: 'btn primary', type: 'button', text: '추가하기', onclick: function () {
            var n = nick.value.trim();
            if (!n) return App.banner('별칭을 입력해 주세요.');
            var base = { nickname: n, generation: gen.value, is_minor: minor.checked };
            /* seat_role은 새 필드다 — 서버가 아직 받지 않으면(422 invalid_request) 뺴고 다시 시도해
             * 구성원 추가 자체는 막히지 않게 한다(README 가정 12). */
            function add(body, seat) {
              Api.post('/v1/families/me/members', body).then(function () {
                App.banner('구성원을 추가했어요.');
                loadMembers();
              }).catch(function (e) {
                if (seat && (e.status === 400 || e.status === 422)) {
                  App.banner('자리는 저장하지 못했어요. 구성원만 추가해요.');
                  add(base, false);
                  return;
                }
                App.banner(e.message);
              });
            }
            if (seatSel.value) {
              var withSeat = { nickname: n, generation: gen.value, is_minor: minor.checked, seat_role: seatSel.value };
              add(withSeat, true);
            } else {
              add(base, false);
            }
          } }));
      }

      /* 선택 목록: 관리자(owner, 화면을 쓰는 본인)는 후보에서 뺀다.
       * 이어받을 사람 지정·관리 권한 이전 모두 서버가 owner 대상을 400 already_owner로 거부하므로
       * 처음부터 목록에 보이지 않게 한다(README 가정 참조). */
      function pickMember(members) {
        var sel = App.h('select', { class: 'input' });
        var any = false;
        (members || []).forEach(function (m) {
          if (m && m.role === 'owner') return; // 본인(관리자) 제외
          any = true;
          sel.appendChild(App.h('option', { value: String(m.id), text: (m.nickname || '구성원') + ' (' + App.genLabel(m.generation) + ')' }));
        });
        if (!any) sel.appendChild(App.h('option', { value: '', text: '관리자를 제외한 구성원이 없어요' }));
        sel.disabled = !any;
        return sel;
      }

      function heirCard(members) {
        var heir = null;
        members.forEach(function (m) { if (m.role === 'heir') heir = m; });
        var sel = pickMember(members);
        return App.h('div', { class: 'card' },
          App.h('h2', { text: '이어받을 사람 지정' }),
          App.h('p', { class: 'muted small', text: '지금 이어받을 사람: ' + (heir ? heir.nickname : '아직 미지정') }),
          App.h('p', { class: 'muted small', text: '관리자(나)는 후보에서 제외돼요.' }),
          App.h('label', { class: 'field' }, App.h('span', { text: '구성원 선택' }), sel),
          App.h('button', { class: 'btn primary', type: 'button', text: '이어받을 사람으로 지정', onclick: function () {
            if (!sel.value) return App.banner('구성원을 선택해 주세요.');
            var target = null;
            members.forEach(function (m) { if (String(m.id) === sel.value) target = m; });
            var m1;
            m1 = App.modal('이어받을 사람 지정', App.h('div', null,
              App.h('p', { text: '“' + (target ? target.nickname : '') + '”을(를) 이어받을 사람으로 지정할까요?' }),
              App.h('p', { class: 'muted small', text: '관리자(owner)만 지정할 수 있어요. 지정만으로 권한이 넘어가지는 않아요.' }),
              App.h('div', { class: 'btn-row' },
                App.h('button', { class: 'btn outline', type: 'button', text: '취소', onclick: function () { m1.close(); } }),
                App.h('button', { class: 'btn primary', type: 'button', text: '지정', onclick: function () {
                  m1.close();
                  Api.post('/v1/families/me/heir', { member_id: Number(sel.value) }).then(function () {
                    App.banner('이어받을 사람을 지정했어요.');
                    loadMembers();
                  }).catch(function (e) { App.banner(e.message); });
                } }))), { closable: false });
          } }));
      }

      function handoverCard(members) {
        var sel = pickMember(members);
        return App.h('div', { class: 'card' },
          App.h('h2', { text: '관리 권한 이전' }),
          App.h('p', { class: 'muted small', text: '관리 권한을 다른 구성원에게 넘겨요. 기록은 하나도 지워지지 않아요. 관리자(나)는 후보에서 제외돼요.' }),
          App.h('label', { class: 'field' }, App.h('span', { text: '넘길 구성원' }), sel),
          App.h('button', { class: 'btn danger', type: 'button', text: '관리 권한 넘기기', onclick: function () {
            if (!sel.value) return App.banner('구성원을 선택해 주세요.');
            var target = null;
            members.forEach(function (m) { if (String(m.id) === sel.value) target = m; });
            App.confirmDanger('관리 권한 이전', [
              '관리 권한을 “' + (target ? target.nickname : '') + '”에게 넘겨요.',
              '이전 후에는 그 사람이 관리자가 되고, 나는 일반 구성원이 돼요.',
              '이 기능은 관리 권한을 넘기는 것일 뿐 법적 상속이 아닙니다.'
            ], '넘기기', function () {
              Api.post('/v1/families/me/handover', { to_member_id: Number(sel.value) }).then(function () {
                App.banner('관리 권한을 넘겼어요.');
                loadMembers();
              }).catch(function (e) { App.banner(e.message); });
            });
          } }));
      }

      /* ── B4: 인원 비교 문장 접어 두기(되돌리기) ─────────────────────
       * 차량 정차 화면의 '그때와 지금' 카드에서 장소 단위로 접어 둔 기록과
       * '모든 장소에서 접어 두기' 설정을 여기서 되돌린다(07문서 B4).
       * 계약: GET  /v1/families/me/settings → { fold_person_comparison_all: bool, folded_place_ids: [...] }
       *       PUT  /v1/families/me/settings { fold_person_comparison_all }
       *       DELETE /v1/families/me/folded-places/{place_id}
       * 서버가 GET에서 두 필드를 주지 않으면(미대응) 이 UI를 통째로 숨긴다(가짜 동작 금지).
       * 초기 조회 실패는 배너 없이 조용히 숨겨 기존 화면 흐름을 해치지 않는다. */
      function loadFoldSettings() {
        Api.get('/v1/families/me/settings').then(function (s) {
          foldBox.innerHTML = '';
          if (!s || typeof s.fold_person_comparison_all !== 'boolean' ||
              !Array.isArray(s.folded_place_ids)) return; // 미대응 → 숨김
          buildFoldCard(s);
        }).catch(function () {
          foldBox.innerHTML = ''; // 미대응·오류 모두 조용히 숨김
        });
      }

      function buildFoldCard(s) {
        var allChk = App.h('input', { type: 'checkbox' });
        allChk.checked = s.fold_person_comparison_all;
        allChk.addEventListener('change', function () {
          var on = allChk.checked;
          Api.put('/v1/families/me/settings', { fold_person_comparison_all: on }).then(function () {
            App.banner(on ? '모든 장소에서 인원 비교 문장을 접어 둬요.' : '장소마다 접어 둔 것만 적용해요.');
            loadFoldSettings();
          }).catch(function (e) {
            allChk.checked = !on; // 실패하면 화면 값을 원래대로 되돌린다
            App.banner(e.message);
          });
        });

        var listWrap = App.h('div');
        renderFoldList(listWrap, s.folded_place_ids);

        foldBox.appendChild(App.h('div', { class: 'card' },
          App.h('h2', { text: '인원 비교 문장 접어 두기' }),
          App.h('p', { class: 'muted small', text: '“그때와 지금” 카드의 인원 비교 문장(그때는 N명, 오늘은 N명)을 접어 둘 수 있어요. 차량 화면에도 적용돼요.' }),
          App.h('label', { class: 'check-row' }, allChk, App.h('span', { text: '인원 비교 문장을 모든 장소에서 접어 두기' })),
          App.h('p', { class: 'muted small fold-list-title' }, App.h('b', { text: '접어 둔 장소 되돌리기' })),
          listWrap));
      }

      function renderFoldList(listWrap, ids) {
        listWrap.innerHTML = '';
        if (!ids.length) {
          listWrap.appendChild(App.h('p', { class: 'muted small', text: '접어 둔 장소가 없어요. 차량 화면의 카드에서 장소를 접어 둘 수 있어요.' }));
          return;
        }
        App.loadPlaces().then(function (groups) {
          var nameMap = {};
          (groups || []).forEach(function (g) {
            (g.places || []).forEach(function (p) { nameMap[String(p.id)] = g.title + ' · ' + p.name; });
          });
          listWrap.innerHTML = '';
          ids.forEach(function (id) { listWrap.appendChild(foldRow(id, nameMap[String(id)])); });
        }).catch(function () {
          // 장소 이름을 못 가져오면 번호로라도 보여 준다(되돌리기는 항상 가능해야 한다)
          listWrap.innerHTML = '';
          ids.forEach(function (id) { listWrap.appendChild(foldRow(id, null)); });
        });
      }

      function foldRow(id, label) {
        return App.h('div', { class: 'fold-row' },
          App.h('div', { class: 'grow' },
            App.h('b', { text: label || ('장소 ' + id) }),
            label ? null : App.h('span', { class: 'muted small', text: ' (이름을 못 가져왔어요)' })),
          App.h('button', { class: 'btn small outline', type: 'button', text: '되돌리기', onclick: function () {
            Api.del('/v1/families/me/folded-places/' + encodeURIComponent(String(id))).then(function () {
              App.banner('이 장소에서 인원 비교 문장을 다시 보여 줘요.');
              loadFoldSettings();
            }).catch(function (e) { App.banner(e.message); });
          } }));
      }
    }
  };
})();
