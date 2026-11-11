/* 아로새김 웹 — API 호출 계층 (02문서 3·11절 계약)
 * 공통 헤더: X-Family-Code. 가족 데이터 열람·수정에는 X-Family-Pin도 함께 보낸다.
 * 가족 코드 발급(생성)에는 X-Device-Token도 함께 보낸다(00문서 13절 — 기기당 생성 빈도 제한).
 * 서버 주소: 기본 같은 오리진(상대 경로). 에뮬레이터 웹뷰면 http://10.0.2.2:8000 으로 전환.
 * 오류는 항상 { code, status, message } 로 던진다 — 화면은 어떤 오류에도 한 줄 안내만 한다. */
(function () {
  'use strict';
  var LS_BASE = 'aro_server_base';
  var LS_CODE = 'aro_family_code';
  var LS_PIN = 'aro_family_pin';
  var LS_DEVICE = 'aro_device_token';

  function base() { return (localStorage.getItem(LS_BASE) || '').replace(/\/+$/, ''); }
  function code() { return localStorage.getItem(LS_CODE) || ''; }
  function pin() { return localStorage.getItem(LS_PIN) || ''; }

  /* 기기 토큰: 이 브라우저를 식별하는 랜덤 값(개인정보 아님). 없으면 발급해 저장.
   * 가족 코드 생성 빈도 제한(하루 1회) 판정용 — 서버가 미반영이면 헤더는 무시된다. */
  function deviceToken() {
    var t = localStorage.getItem(LS_DEVICE);
    if (t) return t;
    var bytes = new Uint8Array(16);
    if (window.crypto && window.crypto.getRandomValues) {
      window.crypto.getRandomValues(bytes);
    } else {
      for (var i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    var hex = '';
    for (var j = 0; j < bytes.length; j++) hex += (bytes[j] < 16 ? '0' : '') + bytes[j].toString(16);
    localStorage.setItem(LS_DEVICE, hex);
    return hex;
  }

  /* 서버 오류 코드 → 한국어 한 줄 (00문서 13절: 영수증 사유 확장 포함) */
  var CODE_MSG = {
    invalid_family: '가족 코드를 확인해 주세요.',
    invalid_pin: 'PIN이 올바르지 않아요.',
    unauthorized: '가족 코드나 PIN이 올바르지 않아요.',
    forbidden: '권한이 없어요.',
    locked: '잠시 잠겼어요. 10분 뒤에 다시 시도해 주세요.',
    not_found: '정보를 찾을 수 없어요.',
    device_limit: '하루 한 번만 만들 수 있어요. 기존 코드로 접속해 주세요.',
    ip_limit: '하루 한 번만 만들 수 있어요. 기존 코드로 접속해 주세요.',
    no_visit: '먼저 그 장소의 방문 기록(스탬프)이 필요해요.',
    duplicate: '이미 등록된 영수증이에요.',
    duplicate_photo: '같은 사진은 다시 쓸 수 없어요. 영수증 사진을 새로 찍어 주세요.',
    daily_limit: '오늘 적립 한도를 채웠어요.',
    invalid_amount: '금액을 확인해 주세요. (1원 ~ 100만 원)',
    missing_photo: '영수증 사진이 필요해요. 사진을 선택해 주세요.',
    missing_stay: '숙소는 체크인·체크아웃 날짜가 필요해요.',
    invalid_stay: '체크인·체크아웃 날짜를 확인해 주세요. (체크아웃이 뒤, 최대 3박)',
    invalid_datetime: '결제 일시를 확인해 주세요.',
    invalid_party_size: '함께 간 인원을 확인해 주세요.',
    invalid_year: '연도를 1900 ~ 현재 연도 사이로 입력해 주세요.',
    guardian_consent_required: '미성년 구성원 사진은 보호자 동의 후 올릴 수 있어요.',
    photo_too_large: '사진이 너무 커요. 5MB 이하로 올려 주세요.',
    unsupported_type: 'JPEG·PNG 사진만 올릴 수 있어요.',
    validation_error: '입력 값을 확인해 주세요.'
  };
  var STATUS_MSG = {
    400: '입력 값을 확인해 주세요.',
    401: '가족 코드나 PIN이 올바르지 않아요.',
    403: '권한이 없어요.',
    404: '정보를 찾을 수 없어요.',
    413: '파일이 너무 커요.',
    423: '잠시 잠겼어요. 10분 뒤에 다시 시도해 주세요.',
    429: '하루 한 번만 만들 수 있어요. 기존 코드로 접속해 주세요.',
    500: '서버에 문제가 있어요. 잠시 후 다시 시도해 주세요.',
    503: '서버를 준비 중이에요. 잠시 후 다시 시도해 주세요.'
  };

  function makeError(status, data, method, path) {
    var ec = data && data.error ? data.error.code : '';
    var msg = (data && data.error && data.error.message) || CODE_MSG[ec] ||
      STATUS_MSG[status] || '요청을 처리하지 못했어요.';
    // 콘솔 로그 1줄 — 개인정보 금지: 메서드·경로·상태·코드만 남긴다
    console.log('[aro] ' + method + ' ' + path + ' -> ' + status + (ec ? ' ' + ec : ''));
    return { code: ec || ('http_' + status), status: status, message: msg };
  }

  function request(method, path, opts) {
    opts = opts || {};
    var init = { method: method, headers: {} };
    if (code()) init.headers['X-Family-Code'] = code();
    // PIN: 가족 데이터 열람·수정에 필요(02문서 9절). 발급·공개 목록 등에서는 서버가 무시한다(가정 — README 참조).
    if (pin() && !(method === 'POST' && path === '/v1/families')) init.headers['X-Family-Pin'] = pin();
    // 기기 토큰: 가족 코드 생성(발급·재발급)에만 함께 보낸다(00문서 13절). 서버가 미반영이면 무시된다.
    if (method === 'POST' && (path === '/v1/families' || path === '/v1/families/me/rotate-code')) {
      init.headers['X-Device-Token'] = deviceToken();
    }
    if (opts.json !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(opts.json);
    } else if (opts.form) {
      init.body = opts.form; // multipart: Content-Type은 브라우저가 boundary와 함께 지정
    }
    return fetch(base() + path, init).then(function (res) {
      if (opts.raw) return res; // ZIP 등 바이너리는 호출자가 처리
      if (res.status === 204) return null;
      return res.json().catch(function () { return null; }).then(function (data) {
        if (!res.ok) throw makeError(res.status, data, method, path);
        return data;
      });
    }, function () {
      console.log('[aro] ' + method + ' ' + path + ' -> network-error');
      return Promise.reject({
        code: 'network', status: 0,
        message: '서버에 연결할 수 없어요. 홈 화면에서 서버 주소를 확인해 주세요.'
      });
    });
  }

  window.Api = {
    base: base,
    code: code,
    pin: pin,
    deviceToken: deviceToken,
    get: function (p) { return request('GET', p); },
    post: function (p, json) { return request('POST', p, { json: json || {} }); },
    patch: function (p, json) { return request('PATCH', p, { json: json || {} }); },
    postForm: function (p, form) { return request('POST', p, { form: form }); },
    del: function (p) { return request('DELETE', p); },
    raw: function (p) { return request('GET', p, { raw: true }); },
    health: function () { return request('GET', '/health'); },
    setBase: function (v) {
      if (v) localStorage.setItem(LS_BASE, v.replace(/\/+$/, ''));
      else localStorage.removeItem(LS_BASE);
    },
    /* 가족 코드·PIN은 이 기기(localStorage)에만 저장한다 */
    saveAuth: function (c, p) {
      localStorage.setItem(LS_CODE, c);
      if (p) localStorage.setItem(LS_PIN, p); else localStorage.removeItem(LS_PIN);
    },
    clearAuth: function () {
      localStorage.removeItem(LS_CODE);
      localStorage.removeItem(LS_PIN);
    },
    hasAuth: function () { return !!code(); }
  };
})();
