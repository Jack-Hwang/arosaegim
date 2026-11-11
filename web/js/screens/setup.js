/* 홈 — 가족 코드 화면 (02문서 5절 1번)
 * 새로 만들기(발급 + PIN 표시·저장 안내) / 코드 + PIN 입력.
 * 실명·연락처 입력란은 절대 두지 않는다.
 * 서버 주소 설정: 기본 같은 오리진(상대 경로 fetch),
 * 안드로이드 에뮬레이터 웹뷰에서는 http://10.0.2.2:8000 으로 전환(02문서 1절). */
(function () {
  'use strict';
  Screens.home = {
    sub: '가족 코드',
    render: function (root) {
      var wrap = App.h('div', { class: 'page' });
      root.appendChild(wrap);
      wrap.appendChild(App.h('h1', { class: 'page-title', text: '우리 가족 시작하기' }));

      if (App.consent() === 'denied') {
        wrap.appendChild(App.h('div', { class: 'card notice-card' },
          App.h('p', { text: '개인정보 안내에 동의하지 않으면 가족 기록을 사용할 수 없어요.' }),
          App.h('button', { class: 'btn primary', type: 'button', text: '개인정보 안내 다시 보기',
            onclick: function () { location.hash = '#/consent'; } })));
      }

      if (Api.hasAuth()) {
        wrap.appendChild(App.h('div', { class: 'card' },
          App.h('h2', { text: '우리 가족' }),
          App.h('p', { class: 'code-line' }, '이 기기의 가족 코드: ', App.h('b', { text: Api.code() })),
          App.h('p', { class: 'muted small', text: '코드와 PIN은 이 기기에만 저장돼요.' }),
          App.h('div', { class: 'btn-col' },
            App.h('button', { class: 'btn primary big', type: 'button', text: '이 가족으로 계속하기',
              onclick: function () { location.hash = '#/courses'; } }),
            App.h('button', { class: 'btn ghost', type: 'button', text: '이 기기에서 코드·PIN 지우기',
              onclick: forgetLocal }))));
      }

      var createOut = App.h('div');
      wrap.appendChild(App.h('div', { class: 'card' },
        App.h('h2', { text: '새로 만들기' }),
        App.h('p', { class: 'muted small', text: '새 가족 코드와 PIN을 발급해요. 하루 한 번만 만들 수 있어요. 이름·전화번호는 받지 않아요.' }),
        createOut,
        App.h('button', { id: 'createBtn', class: 'btn primary', type: 'button', text: '가족 코드 발급받기',
          onclick: createFamily })));

      var codeIn = App.h('input', { class: 'input code-input', type: 'text', maxlength: '8',
        placeholder: '예: K7M2QX4B', autocomplete: 'off', autocapitalize: 'characters' });
      codeIn.addEventListener('input', function () {
        codeIn.value = codeIn.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
      });
      var pinIn = App.h('input', { class: 'input', type: 'password', inputmode: 'numeric', maxlength: '12',
        placeholder: 'PIN (발급 때 받은 숫자)', autocomplete: 'off' });
      wrap.appendChild(App.h('div', { class: 'card' },
        App.h('h2', { text: '코드로 들어가기' }),
        App.h('p', { class: 'muted small', text: '다른 기기에서 만든 가족 코드로 들어와요.' }),
        App.h('label', { class: 'field' }, App.h('span', { text: '가족 코드 (6~8자리)' }), codeIn),
        App.h('label', { class: 'field' }, App.h('span', { text: 'PIN' }), pinIn),
        App.h('button', { class: 'btn primary', type: 'button', text: '들어가기', onclick: enterFamily })));

      wrap.appendChild(serverCard());

      /* 동의 전에는 가족 데이터를 만들 수 없다 */
      function gate() {
        if (App.consent() !== 'granted') {
          App.banner('먼저 개인정보 안내에 동의해 주세요.');
          location.hash = '#/consent';
          return true;
        }
        return false;
      }

      function createFamily() {
        if (gate()) return;
        var btn = wrap.querySelector('#createBtn');
        btn.disabled = true;
        Api.post('/v1/families').then(function (d) {
          btn.disabled = false;
          if (!d || !d.family_code) {
            App.banner('발급에 실패했어요. 잠시 후 다시 시도해 주세요.');
            return;
          }
          Api.saveAuth(d.family_code, d.pin || '');
          showCreateResult(d);
        }).catch(function (e) {
          btn.disabled = false;
          /* 기기당 생성 빈도 제한(00문서 13절): 429·device_limit은 전용 안내 카드로 남긴다 */
          if (e.status === 429 || e.code === 'device_limit') {
            createOut.innerHTML = '';
            createOut.appendChild(App.h('div', { class: 'card slim warn-card' },
              App.h('b', { text: '오늘은 새 코드를 만들 수 없어요' }),
              App.h('p', { class: 'muted small', text: '하루 한 번만 만들 수 있어요. 기존 코드로 접속해 주세요.' })));
          }
          App.banner(e.message);
        });
      }

      function showCreateResult(d) {
        createOut.innerHTML = '';
        createOut.appendChild(App.h('div', { class: 'card slim result' },
          App.h('p', { class: 'code-line big' }, '가족 코드 ', App.h('b', { text: d.family_code })),
          d.pin ? App.h('p', { class: 'code-line big' }, 'PIN ', App.h('b', { text: d.pin })) : null,
          App.h('p', { class: 'muted small', text: '이 코드와 PIN을 종이 등 안전한 곳에 적어 두세요. 잊어버리면 가족 기록을 다시 열어 볼 수 없어요. 이 기기에도 저장했어요.' }),
          App.h('button', { class: 'btn primary big', type: 'button', text: '저장했어요, 시작하기',
            onclick: function () { location.hash = '#/courses'; } })));
        App.banner('가족 코드를 발급했어요.');
      }

      function enterFamily() {
        var c = codeIn.value.trim();
        var p = pinIn.value.trim();
        /* 실서버가 8자리 코드를 발급한다(FAMILY_CODE_LENGTH=8) — 6~8자리 영숫자로 검증(README 가정 17 참조) */
        if (!/^[A-Z0-9]{6,8}$/.test(c)) return App.banner('가족 코드는 6~8자리 영숫자예요.');
        if (!p) return App.banner('PIN을 입력해 주세요.');
        if (gate()) return;
        Api.saveAuth(c, p);
        /* 구성원 조회로 코드+PIN이 맞는지 확인 */
        Api.get('/v1/families/me/members').then(function () {
          App.banner('가족에 연결했어요.');
          location.hash = '#/courses';
        }).catch(function (e) {
          if (e.status === 401 || e.status === 403) Api.clearAuth();
          App.banner(e.message);
        });
      }

      function forgetLocal() {
        var m;
        m = App.modal('이 기기에서 지우기', App.h('div', null,
          App.h('p', { text: '이 기기에 저장된 가족 코드·PIN만 지워요. 서버의 가족 기록은 지워지지 않아요.' }),
          App.h('div', { class: 'btn-row' },
            App.h('button', { class: 'btn outline', type: 'button', text: '취소', onclick: function () { m.close(); } }),
            App.h('button', { class: 'btn danger', type: 'button', text: '지우기', onclick: function () {
              m.close();
              Api.clearAuth();
              App.setConsent('');
              App.invalidatePlaces();
              App.banner('이 기기의 코드를 지웠어요.');
              if (location.hash === '#/home') App.rerender();
              else location.hash = '#/home';
            } }))));
      }

      function serverCard() {
        var cur = Api.base() || '같은 오리진(이 웹을 서비스하는 서버)';
        var urlIn = App.h('input', { class: 'input', type: 'url', placeholder: '예: http://10.0.2.2:8000', value: Api.base() });
        var status = App.h('p', { class: 'muted small', text: '현재: ' + cur });
        function apply() {
          Api.setBase(urlIn.value.trim());
          App.invalidatePlaces();
          status.textContent = '현재: ' + (Api.base() || '같은 오리진(이 웹을 서비스하는 서버)');
          App.banner('서버 주소를 저장했어요.');
        }
        function resetBase() {
          Api.setBase('');
          urlIn.value = '';
          App.invalidatePlaces();
          status.textContent = '현재: 같은 오리진(이 웹을 서비스하는 서버)';
        }
        function check() {
          status.textContent = '확인 중...';
          Api.health().then(function () {
            status.textContent = '연결 좋아요: ' + (Api.base() || '같은 오리진');
            App.banner('서버에 연결돼요.');
          }).catch(function (e) {
            status.textContent = '연결 실패';
            App.banner(e.message);
          });
        }
        return App.h('div', { class: 'card' },
          App.h('h2', { text: '서버 주소' }),
          App.h('p', { class: 'muted small', text: '기본은 이 웹을 서비스하는 서버(같은 오리진)를 써요. 안드로이드 에뮬레이터 웹뷰에서는 http://10.0.2.2:8000 을 입력하세요.' }),
          App.h('label', { class: 'field' }, App.h('span', { text: '서버 주소 (비우면 같은 오리진)' }), urlIn),
          App.h('div', { class: 'btn-row' },
            App.h('button', { class: 'btn outline', type: 'button', text: '이 주소 사용', onclick: apply }),
            App.h('button', { class: 'btn ghost', type: 'button', text: '기본(같은 오리진)', onclick: resetBase }),
            App.h('button', { class: 'btn ghost', type: 'button', text: '연결 확인', onclick: check })),
          status);
      }
    }
  };
})();
