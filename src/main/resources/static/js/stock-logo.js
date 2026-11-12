/* 종목 로고 공용 스토어 (Alpine.store('logos')) */
(function () {
    var KEY = 'stocklog.logos.v1';
    var saved = {};
    try { saved = JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch (e) { saved = {}; }

    document.addEventListener('alpine:init', function () {
        var queue = new Set(), tries = {}, timer = null, inflight = new Set();

        function norm(code) { return String(code || '').trim().toUpperCase(); }
        function persist(map) {
            try { localStorage.setItem(KEY, JSON.stringify(map)); } catch (e) { /* 저장 안 돼도 화면은 그대로 */ }
        }

        Alpine.store('logos', {
            map: Object.assign({}, saved),   // 코드 → URL ('' = 로고 없음)
            broken: {},                      // 이미지가 깨진 코드

            /* 로고 URL. 모르면 ''을 돌려주고 뒤에서 묻는다 */
            url: function (code) {
                var c = norm(code);
                if (!c || /^[0-9]+$/.test(c) || this.broken[c]) return '';
                // 'in'으로 확인해야 Alpine이 키 추가를 감지해 다시 그린다
                if (c in this.map) return this.map[c];
                this.request(c);
                return '';
            },
            /* 글자 아이콘: 이름 첫 글자 (없으면 코드 첫 글자) */
            letter: function (code, name) { return String(name || code || '?').trim().charAt(0).toUpperCase(); },
            fail: function (code) { this.broken[norm(code)] = true; },

            request: function (c) {
                if (queue.has(c) || inflight.has(c)) return;
                queue.add(c);
                var self = this;
                if (!timer) timer = setTimeout(function () { self.flush(); }, 60);
            },
            flush: async function () {
                timer = null;
                var codes = Array.from(queue); queue.clear();
                if (!codes.length) return;
                codes.forEach(function (c) { inflight.add(c); });
                var self = this, retry = [];
                for (var i = 0; i < codes.length; i += 100) {
                    var part = codes.slice(i, i + 100);
                    try {
                        var res = await fetch('/api/stock/logos?codes=' + encodeURIComponent(part.join(',')), { headers: { 'Accept': 'application/json' } });
                        if (!res.ok) throw new Error('HTTP ' + res.status);
                        var body = await res.json();
                        var logos = body.logos || {}, pending = new Set(body.pending || []);
                        part.forEach(function (c) {
                            if (Object.prototype.hasOwnProperty.call(logos, c)) self.map[c] = logos[c] || '';
                            else if (pending.has(c)) retry.push(c);
                            else self.map[c] = '';   // 서버가 묻지 않는 코드 → 글자 아이콘
                        });
                    } catch (e) {
                        part.forEach(function (c) { retry.push(c); });
                    }
                }
                codes.forEach(function (c) { inflight.delete(c); });
                persist(self.map);
                // 못 받은 코드는 8초 뒤 다시 (최대 8번). 그래도 안 되면 이번 화면에선 글자 아이콘
                var again = retry.filter(function (c) { tries[c] = (tries[c] || 0) + 1; return tries[c] <= 8; });
                if (again.length) setTimeout(function () { again.forEach(function (c) { self.request(c); }); }, 8000);
            }
        });
    });
})();
