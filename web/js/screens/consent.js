/* 개인정보 동의 화면 — 서비스 시작 시 1회 (02문서 5절)
 * 동의/거부 버튼을 명확히 구분한다. 뒤로가기·빈 곳 터치는 동의가 아니며,
 * 이 화면은 전체 페이지라 바깥 클릭으로 닫히지도 않는다.
 * 거부하면 홈(가족 코드 화면)으로 돌아가고, 동의 전에는 다른 화면에 들어갈 수 없다. */
(function () {
  'use strict';
  Screens.consent = {
    sub: '개인정보 안내',
    render: function (root) {
      var deniedBefore = App.consent() === 'denied';
      var body = App.h('div', { class: 'page' },
        App.h('h1', { class: 'page-title', text: '개인정보 안내' }),
        App.h('p', { class: 'muted', text: '아로새김이 저장하는 정보를 알려 드려요. 동의해야 가족 기록을 시작할 수 있어요.' }),
        App.h('div', { class: 'card' },
          App.h('ul', { class: 'consent-list' },
            App.h('li', null, App.h('b', { text: '가족 코드' }), App.h('span', { text: ' — 8자리 영문·숫자로 된 가족 코드예요. 실명·연락처는 받지 않아요.' })),
            App.h('li', null, App.h('b', { text: '방문 장소·시각' }), App.h('span', { text: ' — 어디를 언제 방문했는지(스탬프) 기록해요. 실시간 위치는 저장하지 않아요.' })),
            App.h('li', null, App.h('b', { text: '올린 사진·메모' }), App.h('span', { text: ' — 그때 기록과 맛집 기록의 사진·메모는 가족만 볼 수 있어요.' })),
            App.h('li', null, App.h('b', { text: '영수증 인증 기록' }), App.h('span', { text: ' — 예시(시뮬레이션) 적립 내역이에요.' })),
            App.h('li', null, App.h('b', { text: '기기 저장' }), App.h('span', { text: ' — 가족 코드와 PIN은 이 기기에만 저장돼요.' })),
            App.h('li', null, App.h('b', { text: '삭제' }), App.h('span', { text: ' — 가족 관리 화면에서 언제든 전체 삭제할 수 있어요.' })))),
        App.h('div', { class: 'btn-col' },
          App.h('button', { class: 'btn primary big', type: 'button', text: '동의하고 시작하기', onclick: agree }),
          App.h('button', { class: 'btn outline big', type: 'button', text: '동의하지 않고 홈으로', onclick: deny })));

      if (deniedBefore) {
        body.insertBefore(App.h('p', { class: 'notice', text: '이전에 거부하셨어요. 동의하면 서비스를 시작할 수 있어요.' }), body.children[1]);
      }
      root.appendChild(body);

      function agree() {
        App.setConsent('granted');
        location.hash = Api.hasAuth() ? '#/courses' : '#/home';
      }
      function deny() {
        App.setConsent('denied');
        location.hash = '#/home';
      }
    }
  };
})();
