/* 레버리지·인버스 ETF 분석 */
(function (global) {
  'use strict';
  // 코드 → [기초, 배수, 개별 종목 여부]
  const MAP = {
    TQQQ: ['QQQ', 3], SQQQ: ['QQQ', -3], QLD: ['QQQ', 2], QID: ['QQQ', -2], PSQ: ['QQQ', -1],
    UPRO: ['SPY', 3], SPXL: ['SPY', 3], SPXU: ['SPY', -3], SPXS: ['SPY', -3], SSO: ['SPY', 2], SDS: ['SPY', -2], SH: ['SPY', -1],
    SOXL: ['SOXX', 3], SOXS: ['SOXX', -3],
    TNA: ['IWM', 3], TZA: ['IWM', -3], UWM: ['IWM', 2], TWM: ['IWM', -2],
    FAS: ['XLF', 3], FAZ: ['XLF', -3], TECL: ['XLK', 3], TECS: ['XLK', -3], LABU: ['XBI', 3], LABD: ['XBI', -3],
    NVDL: ['NVDA', 2, true], TSLL: ['TSLA', 2, true],
    '122630': ['069500', 2], '252670': ['069500', -2], '114800': ['069500', -1],
    '233740': ['229200', 2], '251340': ['229200', -1],
  };
  const RSI2_TEST = {
    QQQ: ['62~70%', true], SPY: ['72~74%', true], XLK: ['69~74%', true], XLF: ['68~71%', true], XBI: ['62~67%', true],
    SOXX: ['57~76%', false], IWM: ['58~67%', false], XLE: ['52~71%', false], TLT: ['59~67%', false],
  };
  function rsi2Test(under) { const t = RSI2_TEST[under]; return t ? { win: t[0], ok: t[1] } : null; }
  function info(code) {
    const m = MAP[String(code || '').toUpperCase()];
    return m ? { under: m[0], k: m[1], single: !!m[2] } : null;
  }
  const num = v => (v == null ? NaN : Number(v));
  const sma = (a, n) => a.length >= n ? a.slice(-n).reduce((s, x) => s + x, 0) / n : null;

  function rsi2(C) {
    if (C.length < 10) return null;
    let ag = 0, al = 0;
    for (let i = 1; i < C.length; i++) {
      const ch = C[i] - C[i - 1], g = Math.max(ch, 0), l = Math.max(-ch, 0);
      if (i <= 2) { ag += g / 2; al += l / 2; } else { ag = (ag + g) / 2; al = (al + l) / 2; }
    }
    return al === 0 ? 100 : 100 - 100 / (1 + ag / al);
  }

  /* day/week: 기초의 일봉·주봉 */
  function analyze(day, week, k) {
    const C = day.map(b => num(b.close)).filter(x => x > 0);
    const W = week.map(b => num(b.close)).filter(x => x > 0);
    if (C.length < 25) return null;
    const cur = C[C.length - 1];
    const lr = []; for (let i = C.length - 20; i < C.length; i++) lr.push(Math.log(C[i] / C[i - 1]));
    const mu = lr.reduce((s, x) => s + x, 0) / lr.length;
    const sd = Math.sqrt(lr.reduce((s, x) => s + (x - mu) ** 2, 0) / (lr.length - 1));
    const drag = n => n * (k * k - k) / 2 * sd * sd * 100;          // % (기초 k배 대비 손해)
    let path = 0; for (let i = C.length - 20; i < C.length; i++) path += Math.abs(C[i] - C[i - 1]);
    const er = path > 0 ? Math.abs(cur - C[C.length - 21]) / path : 0;
    const w40 = sma(W, 40), wCur = W.length ? W[W.length - 1] : null;
    const up = w40 && wCur ? wCur >= w40 : null;
    const dirOk = up == null ? null : (k > 0 ? up : !up);
    // ATR(14) 기초
    const tr = []; for (let i = Math.max(1, day.length - 14); i < day.length; i++) {
      const h = num(day[i].high), l = num(day[i].low), pc = num(day[i - 1].close);
      if (h > 0 && l > 0 && pc > 0) tr.push(Math.max(h - l, Math.abs(h - pc), Math.abs(l - pc)));
    }
    const atr = tr.length ? tr.reduce((s, x) => s + x, 0) / tr.length : 0;
    const line = atr > 0 ? (k > 0 ? cur - 2 * atr : cur + 2 * atr) : null;
    const lineEtf = line ? k * (line / cur - 1) * 100 : null;           // 기준선까지 ETF 대략 등락(%)
    const r2 = rsi2(C), s5 = sma(C, 5);
    // 흐름 기준선까지 거리: 기초가 40주선까지 몇 % 움직여야 하는지, 그때 ETF는 대략 몇 %
    const wGap = w40 && wCur ? (w40 / wCur - 1) * 100 : null;
    const wGapEtf = wGap != null ? k * wGap : null;
    let sig = null;
    if (k > 0 && up && r2 != null && r2 < 10) sig = { t: '기초 단기 과매도(RSI2 < 10) · 상승 추세 안', tone: 'good', d: '과거 시험: 승률 61~72%, 보통 2~5일 안에 5일선 회복. 사전 기준은 탈락한 참고 신호예요.' };
    else if (k > 0 && up && r2 != null && s5 && cur > s5) sig = { t: '과매도 신호 없음', tone: '', d: '기초가 5일선 위예요. RSI2 기준으로는 눌림 자리가 아니에요.' };
    else if (k < 0) sig = { t: '인버스 단기 신호는 시험하지 않았어요', tone: '', d: '방향과 감쇠만 참고하세요.' };
    const longHold = drag(21) > 3 || er < 0.3 || k < 0;
    return {
      cur, sdAnn: sd * Math.sqrt(252) * 100, etfVol: Math.abs(k) * sd * Math.sqrt(252) * 100,
      drag5: drag(5), drag21: drag(21), er, trend: er >= 0.3,
      up, dirOk, w40, wCur, wGap, wGapEtf, s5, line, lineEtf, r2, sig, longHold,
    };
  }
  global.LevEtf = { info, analyze, rsi2Test };
})(window);
