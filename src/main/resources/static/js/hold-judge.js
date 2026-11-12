/* 보유 종목 판정 */
(function (global) {
  // 투자 성향 설정 없으면 '기본' 값. 성향은 판정이 아니라 크기·보험 규칙만 바꾼다
  const DEFAULT_PARAMS = { riskPct: 0.01, maxWeight: 0.20, slots: 8, brakeDd: 0.15, wideStop: 0.10, warnRiskPct: 2.0, marketWeak: 'HALF', trailAtr: 3.0 };
  function cfg() {
    const st = global.__investSetting;
    return Object.assign({}, DEFAULT_PARAMS, st && st.params ? st.params : {}, { label: st?.profileLabel || '기본', brakeOn: !!st?.brakeOn, ddPct: Number(st?.ddPct) || 0, accountKnown: !!st?.accountKnown });
  }
  function pct(v) { const n = Number(v) || 0; return (n > 0 ? '+' : '') + n.toFixed(1) + '%'; }

  /* 기준선 계산 */
  function holdLine(s) {
    const avg = Number(s.avg) || 0, chart = Number(s.stop) || 0, atr = Number(s.atr) || 0, peak = Number(s.peak) || 0;
    const entryStop = Number(s.entryStop) || 0;
    const c = cfg();
    if (s.style === 'MOMENTUM') {
      return { mode: 'mom', line: 0, closeBased: true, beHit: false, breakEven: false, wideActive: false, entryFloor: false, wideK: c.wideStop };
    }
    if (entryStop > 0) {
      const K = Number(c.trailAtr) || 3;
      const trail = peak > 0 && atr > 0 ? peak - K * atr : 0;
      const line = Math.max(entryStop, trail);
      return { mode: 'plan', line, trail, K, trailOn: trail > entryStop, entryFloor: trail <= entryStop, closeBased: true,
               beHit: false, breakEven: false, wideActive: false, wideK: c.wideStop };
    }
    if (!(chart > 0)) return { mode: 'free', line: 0, closeBased: false, beHit: false, breakEven: false, wideActive: false, entryFloor: false, wideK: c.wideStop };
    const wideK = c.wideStop, wideLine = avg > 0 ? avg * (1 - wideK) : 0;
    const beHit = avg > 0 && atr > 0 && peak - avg >= 2 * atr;
    const breakEven = beHit && chart < avg;
    const wideActive = !beHit && wideLine > 0 && wideLine < chart;
    const line = beHit ? Math.max(chart, avg) : (wideActive ? wideLine : chart);
    return { mode: 'free', line, closeBased: false, beHit, breakEven, wideActive, entryFloor: false, wideK };
  }

  function holdJudge(input, fmt) {
    if (!input) return null;
    fmt = fmt || (v => String(v));

      const s = input;
      if (!s) return null;
      const { cur, avg, target } = s;
      // plan: 앱 진입 계획대로 산 종목(매수 때 손절가 저장) → 기준선 = max(오늘 차트 손절선, 매수 때 손절가)
      const L = holdLine(s);
      const planMode = L.mode === 'plan';
      const closeBased = L.closeBased;
      const entryFloor = planMode && L.entryFloor;
      const wideK = L.wideK, wideActive = L.wideActive, breakEven = L.breakEven;
      const peak = Number(s.peak) || 0;   // 서버가 보유 시작일 이후 확정 종가 최고로 계산 (장중 진행 중인 봉 제외)
      const ret = avg > 0 && cur > 0 ? (cur / avg - 1) * 100 : null;
      if (L.mode === 'mom') return momJudge(s, ret, fmt);
      const stop = L.line || 0;
      const hasStop = stop > 0 && cur > 0;
      // 종가 판정: 확정 종가(장중이면 직전 거래일 종가). 없으면 현재가
      const lastClose = Number(s.lastClose) > 0 ? Number(s.lastClose) : cur;
      const closeBroken = closeBased && hasStop && lastClose < stop;
      const intraBelow = closeBased && hasStop && !closeBroken && cur < stop;   // 장중 기준선 아래, 아직 종가 확정 전
      const profitLine = hasStop && avg > 0 && stop >= avg;
      const lineName = breakEven ? '본전선' : (profitLine ? '수익 보호선' : '손절선');
      const stopTip = closeBased
          ? ' 종가 기준이라 장중에 잠깐 깨지는 건 괜찮아요. 장 마감 종가가 이 아래면 다음 날 시가에 정리해요.'
          : ' 이 가격에 감시(스탑) 주문을 걸어 두면 장중에 깨질 때 바로 정리돼요.';
      const gap = hasStop ? (cur - stop) / cur * 100 : null;           // 기준선 위 여유 (%)
      const brokenNow = closeBased ? closeBroken : (hasStop && gap <= 0);
      const sellWhen = s.sessionOpen ? '기준대로라면 오늘 장에서 정리할 때예요.' : '기준대로라면 다음 거래일 시가에 정리할 때예요.';
      const closeWhen = s.closeDate ? `${String(s.closeDate).slice(5).replace('-', '/')} 종가` : '최근 종가';
      const tgap = target > 0 && cur > 0 ? (target - cur) / cur * 100 : null;
      const atLine = hasStop && avg > 0 ? (stop / avg - 1) * 100 : null; // 기준선에서 팔면 수익률
      // 종가로 이미 이탈했는데 장중 가격은 기준선 위로 돌아온 경우
      const rebound = closeBroken && cur > 0 && cur >= stop;
      const reboundSub = rebound ? `지금 가격 ${fmt(cur)}는 기준선보다 ${gap.toFixed(1)}% 위로 올라왔어요. 그래도 기준은 종가라 ${closeWhen} 이탈로 정리 신호가 이미 났어요.` : '';
      const reboundTodo = '오늘 다시 올라와도 신호는 취소되지 않아요. 기준이 종가라서, 장중에 회복했다고 이미 난 정리 신호가 사라지지는 않아요.';

      const fp = fmt;
      const atrPct = s.atr > 0 && cur > 0 ? s.atr / cur * 100 : 0;
      // 주의 정보: 40주선 아래 + 종가가 20일선 아래 3일 이상. 매도 규칙으로 쓰면 새 데이터에서 수익이 줄어 '주의'로만 쓴다
      const trendWeak = !!s.longDown && (Number(s.below20Days) || 0) >= 3;
      const gapDown = !closeBased && hasStop && s.dayOpen > 0 && s.dayOpen <= stop;
      const touched = hasStop && gap > 0 && s.dayLow > 0 && s.dayLow <= stop;

      // 문구: 판정(state·level)은 위 규칙 그대로, 상황을 더 잘게 나눠 숫자와 '할 일'을 붙인다
      const n20 = Number(s.below20Days) || 0;
      // 반등 사실(판정에 쓰지 않음): 확정 종가 기준 최근 20일 저점 대비 · 마지막 거래일 등락
      const prevC = Number(s.prevClose) || 0, low20 = Number(s.low20) || 0;
      const lastChg = prevC > 0 && lastClose > 0 ? (lastClose / prevC - 1) * 100 : null;
      const fromLow = low20 > 0 && lastClose > 0 ? (lastClose / low20 - 1) * 100 : null;
      const bounceFact = fromLow !== null && fromLow >= 10;
      // 긴 추세 지표(40주선·20일선)는 판정 기준이 아니라 참고로만 말한다
      const longNote = s.longDown ? ` 40주선${n20 > 0 ? '·20일선' : ''} 아래라 긴 추세 지표는 아직 하락 쪽이에요(판정 기준은 아니에요).` : '';
      const lockText = atLine === null ? '' : (atLine >= 0 ? `여기서 정리돼도 평단 대비 ${pct(atLine)} 수익이에요` : `여기서 정리되면 평단 대비 ${pct(atLine)}예요`);
      const sellNow = s.sessionOpen ? '오늘 장에서 정리하기' : '다음 거래일 시가에 정리하기';
      const watchHow = closeBased ? '장 마감 종가만 확인하면 돼요. 주가가 오르면 기준선은 앱이 자동으로 올려요.'
                                  : `${fp(stop)}에 감시(스탑) 주문을 걸어 두세요. 기준선이 오르면 주문 가격도 옮겨 주세요.`;
      const dayMove = atrPct > 0 ? ` (하루 평균 변동폭 ${atrPct.toFixed(1)}%)` : '';

      let state, title, sub, tone, short, todo;
      if (!hasStop) {
          state = 'unknown'; tone = 'neutral'; short = '기준선 계산 불가';
          title = '차트 기준선을 계산하지 못했어요';
          sub = '차트 데이터가 부족해요. 수익률과 차트를 직접 확인해 판단해 주세요.';
          todo = { t: '손절 가격을 직접 정해 두기', d: '살 때 생각한 손절 가격이 있다면 그 가격에 감시 주문을 걸어 두세요.' };
      } else if (brokenNow && !profitLine) {
          state = 'lossBroken'; tone = 'alert'; short = '손절선 이탈';
          const late = s.atr > 0 && cur < stop - s.atr;
          title = closeBased ? `${closeWhen} ${fp(lastClose)}, 손절선 ${fp(stop)} 아래로 마감했어요` : `손절선 ${fp(stop)}을 깼어요`;
          sub = (rebound ? reboundSub : '처음 정한 기준대로라면 정리할 때예요.') + (ret !== null ? ` 지금 팔면 평단 대비 ${pct(ret)}예요.` : '')
              + (late ? ' 손절선보다 하루 변동폭 이상 더 내려와 있어요. 기다릴수록 손실이 더 커질 수 있어요.' : '');
          todo = { t: sellNow, d: rebound ? reboundTodo + ' 다시 진입 신호가 나오면 그때 새로 들어가면 돼요.'
                                          : '손절선을 낮추거나 반등을 기다리지 마세요. 다시 진입 신호가 나오면 그때 새로 들어가면 돼요.' };
      } else if (brokenNow && breakEven) {
          state = 'beBroken'; tone = 'caution'; short = '본전선 이탈';
          title = `수익을 냈다가 평단 ${fp(avg)}까지 밀렸어요`;
          sub = '매수 후 충분히 올랐다가 본전으로 돌아왔어요. 본전선 기준대로라면 손실로 바뀌기 전에 끝낼 자리예요.';
          todo = { t: sellNow, d: '본전에서 끝내고, 다시 진입 신호가 나오면 새로 들어가세요.' };
      } else if (brokenNow) {
          state = 'profitBroken'; tone = 'caution'; short = '수익 보호선 이탈';
          title = closeBased ? `${closeWhen} ${fp(lastClose)}, 수익 보호선 ${fp(stop)} 아래로 마감했어요` : `수익 보호선 ${fp(stop)} 아래로 내려왔어요`;
          sub = (rebound ? reboundSub + ' ' : '') + (ret !== null ? (ret >= 0 ? `지금 정리해도 평단 대비 ${pct(ret)} 수익이에요. 오른 만큼을 지키는 매도예요.` : `지금 팔면 평단 대비 ${pct(ret)}예요.`) : '기준대로라면 정리할 때예요.');
          todo = { t: sellNow, d: rebound ? reboundTodo + ' 다시 신호가 나오면 새로 진입하면 돼요.' : '수익을 더 돌려주기 전에 정리하세요. 다시 신호가 나오면 새로 진입하면 돼요.' };
      } else if (intraBelow) {
          state = 'intraBelow'; tone = 'caution'; short = `장중 ${profitLine ? '수익 보호선' : '손절선'} 아래`;
          title = `장중에 ${lineName} ${fp(stop)} 아래로 내려왔어요`;
          sub = `지금 ${fp(cur)}로 ${(-gap).toFixed(1)}% 아래예요. 기준은 종가라 아직 확정이 아니에요.`;
          todo = { t: '지금 팔지 말고 장 마감 종가 확인하기', d: `마감이 ${fp(stop)} 아래면 다음 거래일 시가에 정리하고, 위로 회복하면 그대로 보유해요.` };
      } else if (!profitLine && gap <= 3) {
          state = 'lossNear'; tone = 'caution'; short = `손절선까지 ${gap.toFixed(1)}%`;
          title = atrPct >= gap ? `손절선 ${fp(stop)}까지 ${gap.toFixed(1)}%, 하루 만에 닿을 수 있어요` : `손절선 ${fp(stop)}까지 ${gap.toFixed(1)}% 남았어요`;
          sub = (lockText ? lockText + '. ' : '') + '처음 정한 손실 범위 안이니, 깨지면 기준대로 끝내면 돼요.';
          todo = closeBased
              ? { t: '오늘 종가가 손절선 아래인지 확인하기', d: '장중엔 기다리고, 마감이 아래면 다음 날 시가에 정리해요. 손절선을 내리거나 물타기하지 마세요.' }
              : { t: `${fp(stop)}에 감시 주문이 걸려 있는지 확인하기`, d: '안 걸었다면 지금 걸어 두세요. 손절선을 내리거나 물타기하지 마세요.' };
      } else if (tgap !== null && tgap >= 0 && tgap <= 2 && (ret === null || ret >= 0)) {   // 목표를 이미 넘었으면(tgap < 0) 아래 수익 보호 문구로
          state = 'targetNear'; tone = 'good'; short = `목표까지 ${tgap.toFixed(1)}%`;
          title = `목표 ${fp(target)}까지 ${tgap.toFixed(1)}%, 다 팔 자리는 아니에요`;
          sub = `위쪽 저항 구간이에요. 돌파하면 ${lineName}이 따라 올라가요.`;
          todo = { t: `팔지 말고 ${lineName} 따라가기`, d: `${lineName} ${fp(stop)}${lockText ? ' · ' + lockText : ''}. ${watchHow}` };
      } else if (profitLine) {
          state = 'holdProfit'; tone = 'good'; short = '보유 유지 · 수익 보호 중';
          title = breakEven ? '본전은 지켜 둔 상태예요'
              : (ret !== null && ret >= 30 ? `수익 ${pct(ret)}, 큰 수익을 기준선이 지키고 있어요`
              : (planMode && L.trailOn ? '수익 보호선이 최고 종가를 따라 올라가고 있어요' : '수익을 지키며 보유 중이에요'));
          sub = (tgap !== null && tgap < 0 ? `목표 ${fp(target)}를 넘어섰어요. ` : '') + `${lineName} ${fp(stop)}까지 ${gap.toFixed(1)}% 여유가 있어요${dayMove}.` + (lockText ? ` ${lockText}.` : '');
          todo = { t: closeBased ? '따로 할 일 없음 · 종가만 확인하기' : `${fp(stop)} 감시 주문 유지하기`, d: watchHow + (s.overheat ? ' 과열 신호가 있어도 기준선 위라면 파는 이유가 아니에요.' : '') };
      } else {
          state = 'holdLoss'; tone = 'neutral'; short = '보유 유지';
          if (ret === null) title = `손절선 ${fp(stop)} 위에서 보유 중이에요`;
          else if (ret >= 0) title = planMode ? `수익 ${pct(ret)}, 매수 때 정한 손절가를 지키는 중이에요` : `수익 ${pct(ret)}, 아직 본전 보호 전이에요`;
          else if (ret > -5) title = `평단 근처(${pct(ret)})에서 흔들리는 중이에요`;
          else title = `손실 ${pct(ret)}, 손절선까지 ${gap.toFixed(1)}% 남았어요`;
          sub = `손절선 ${fp(stop)}까지 ${gap.toFixed(1)}% 여유가 있어요${dayMove}.`
              + (ret !== null && ret < 0 && ret > -5 ? ' 이 정도 흔들림은 정상 범위예요.' : '')
              + (ret !== null && ret <= -5 && lockText ? ` ${lockText}. 처음 정한 손실 범위 안이에요.` : '');
          todo = { t: closeBased ? '종가만 확인하며 보유하기' : `${fp(stop)}에 감시 주문 걸어 두고 보유하기`,
                   d: ((ret !== null && ret < 0 ? '반등을 바라며 손절선을 내리지 마세요. ' : '')
                    + (wideActive ? `계획 없이 산 종목이라 차트 손절선보다 넓은 평단 −${Math.round(wideK * 100)}%를 손절선으로 써요.` : (closeBased ? '장중에 잠깐 깨지는 건 괜찮아요.' : '감시 주문은 기준선이 바뀔 때마다 옮겨 주세요.'))).trim() };
      }
      if (gapDown && brokenNow) sub += ` 오늘 ${lineName} 아래에서 시작했어요(갭 하락). 감시 주문도 시가 근처에서 체결되니 손실이 계획보다 커질 수 있어요.`;
      if (todo) todo.tone = ['lossBroken', 'beBroken', 'profitBroken'].includes(state) ? 'sell' : (['lossNear', 'intraBelow'].includes(state) ? 'caution' : 'hold');

      const level = !hasStop ? null
          : (['lossBroken', 'beBroken', 'profitBroken'].includes(state) ? 'sell'
          : (['lossNear', 'intraBelow'].includes(state) ? 'caution' : 'hold'));

      // 비중이 커서 손절선에서 정리될 때 계좌 손실이 성향 참고 기준(종목당 위험의 2배)을 넘으면, 보유 판정은 그대로 두고 할 일을 비중 점검으로
      const acctW = Number(s.acctWeight) || 0;
      if (level === 'hold' && acctW > 0 && hasStop && gap > 0 && avg > 0 && cur > 0 && stop < avg) {
          const c0 = cfg(), costLoss = acctW * (avg - stop) / cur;
          if (costLoss > c0.warnRiskPct) {
              todo = { t: `비중 점검하기 · 손절선에서 정리되면 원금 기준 계좌 약 −${costLoss.toFixed(1)}%`, tone: 'caution',
                       d: `보유 판정은 '유지'예요. 다만 비중 ${acctW.toFixed(0)}%라 손절선에서 정리되면 원금 기준 손실이 '${c0.label}' 성향 참고 기준(종목당 위험의 2배, ${c0.warnRiskPct}%)보다 커요(갭 하락이면 더). 줄일지는 직접 정하세요.` };
          }
      }

      // 판단 근거 체크리스트
      const checks = [];
      if (hasStop) {
          if (closeBroken) checks.push({ s: 'bad', t: `${closeWhen} ${fp(lastClose)} · ${lineName} 아래 마감`, d: `기준선 ${fp(stop)}. 종가가 기준선 아래면 다음 거래일 시가에 정리하는 게 기준이에요.` + (rebound ? ` 지금 가격 ${fp(cur)}는 기준선 위지만, 판정은 종가로 해요.` : '') });
          else if (intraBelow) checks.push({ s: 'warn', t: `장중 ${lineName}보다 ${(-gap).toFixed(1)}% 아래`, d: `기준선 ${fp(stop)}. 종가가 확정돼야 판정해요.` });
          else if (gap <= 0) checks.push({ s: 'bad', t: `${lineName}보다 ${(-gap).toFixed(1)}% 아래`, d: `기준선 ${fp(stop)}. 깨지면(장중 포함) 정리하는 게 기준이에요.` });
          else if (!profitLine && gap <= 3) checks.push({ s: 'warn', t: `${lineName}까지 ${gap.toFixed(1)}%`, d: atrPct > 0 ? `하루 평균 변동폭(${atrPct.toFixed(1)}%) 안팎이라 하루 만에 닿을 수 있어요.` : '' });
          else checks.push({ s: 'ok', t: `${lineName}까지 ${gap.toFixed(1)}% 여유`, d: atLine !== null ? `기준선 ${fp(stop)} · 여기서 팔면 ${pct(atLine)}` : `기준선 ${fp(stop)}` });
      }
      if (planMode && hasStop) checks.push(L.trailOn
          ? { s: 'info', t: `추적 손절 (최고 종가 − ${L.K} ATR)`, d: `매수 후 최고 종가 ${fp(peak)}에서 평소 하루 변동폭의 ${L.K}배 아래예요. 주가가 오르면 따라 올라가요. 장중 출렁임에 털리지 않게 넓게, 종가로만 판단해요.` }
          : { s: 'info', t: '매수 때 정한 손절가 유지 중', d: `매수 후 최고 종가 − ${L.K} ATR${peak > 0 && s.atr > 0 ? `(${fp(peak - L.K * s.atr)})` : ''}가 매수 때 손절가보다 높아지면 그때부터 따라 올라가요.` });
      if (wideActive) checks.push({ s: 'info', t: `여유 있는 손절 (평단 −${Math.round(wideK * 100)}%)`, d: `앱 진입 계획 없이 산 종목이라 차트 손절선(${fp(s.stop)})보다 넓게 잡아요. 차트 손절선만 쓰면 자주 털리기 쉬워요.` + (wideK > 0.1 ? ' −15%는 더 오래 버티는 대신, 손절될 때 손실이 더 커요.' : '') + ' 본전 보호가 시작되면 차트 기준으로 바뀌어요.' });
      if (touched && closeBased) checks.push({ s: 'info', t: `오늘 장중에 ${lineName} 아래로 내려갔다가 회복했어요`, d: '종가 기준이라 아직 보유예요.' });
      else if (touched) checks.push({ s: 'warn', t: `오늘 장중에 ${lineName}을 건드렸다가 회복했어요`,
              d: '감시 주문을 걸어 뒀다면 이미 팔렸을 가격이에요.' });
      if (gapDown) checks.push({ s: 'bad', t: '오늘 갭 하락으로 시작', d: `시가 ${fp(s.dayOpen)}가 기준선 아래였어요.` });
      if (Number(s.ma20) > 0 && cur > 0) {
          const n = Number(s.below20Days) || 0;
          if (cur >= s.ma20) checks.push({ s: 'ok', t: '20일선 위', d: `20일선 ${fp(s.ma20)} · 단기 흐름 유지` });
          else if (trendWeak) checks.push({ s: 'info', t: `20일선 아래 ${n}일째 · 40주선도 아래`, d: '긴 추세 지표는 하락 쪽이에요. 정리 기준은 아니고 참고예요.' });
          else checks.push({ s: 'info', t: n > 0 ? `20일선 아래 ${n}일째` : '20일선 아래', d: s.longMa > 0 && !s.longDown ? '긴 흐름(40주선)은 위라 흔한 눌림일 수 있어요.' : '' });
      }
      if (Number(s.longMa) > 0) {
          checks.push(s.longDown
              ? { s: 'info', t: '40주선(약 200일선) 아래', d: `40주선 ${fp(s.longMa)} · 긴 흐름 지표는 하락 쪽이에요(참고).` }
              : { s: 'ok', t: '40주선(약 200일선) 위', d: `40주선 ${fp(s.longMa)} · 긴 흐름은 상승 쪽이에요.` });
      }
      if (bounceFact || (lastChg !== null && Math.abs(lastChg) >= 5)) checks.push({ s: 'info',
          t: [fromLow !== null && fromLow >= 10 ? `최근 20일 저점 ${fp(low20)}에서 ${pct(fromLow)}` : '', lastChg !== null && Math.abs(lastChg) >= 5 ? `${closeWhen} ${pct(lastChg)}` : ''].filter(Boolean).join(' · '),
          d: `사실만 보여 줘요. 반등이 추세 전환인지는 판정에 쓰지 않아요.${low20 > 0 ? ` 저점 ${fp(low20)}이 지켜지는지가 전환 판단을 확인하는 자리예요.` : ''}` });
      if (breakEven) checks.push({ s: 'ok', t: '본전 보호 중', d: '매수 후 최고가가 평단 + 2 ATR을 넘어 기준선을 평단으로 올렸어요.' });
      else if (!planMode && avg > 0 && s.atr > 0 && ret !== null && ret > 0 && !profitLine) checks.push({ s: 'info', t: '본전 보호 전', d: `매수 후 최고가가 ${fp(avg + 2 * s.atr)}를 넘으면 기준선이 평단으로 올라가요.` });
      // 상황 1: 실적 발표 임박. 판정은 그대로, 갭 위험 크기만 숫자로 (앱에서 시험 안 함)
      const ed = Number.isFinite(Number(s.earnDays)) && s.earnDays !== null ? Number(s.earnDays) : null;
      const earnSoon = ed !== null && ed >= 0 && ed <= 7;
      const gapLoss = earnSoon && hasStop && acctW > 0 && cur > 0 && s.atr > 0 ? acctW * Math.max(0, cur - (stop - s.atr)) / cur : null;
      if (earnSoon && !brokenNow) checks.unshift({ s: 'warn', t: ed === 0 ? '오늘 실적 발표' : `실적 발표 D-${ed}`,
          d: '발표 다음 날 시가가 기준선 아래로 열리면 손절가보다 낮게 팔려요.' + (gapLoss !== null ? ` 기준선보다 1 ATR 아래에서 팔리면 계좌 약 −${gapLoss.toFixed(1)}%예요.` : '') });
      // 상황 2: 시장 약세. 보유 판정은 바꾸지 않는다
      if (s.marketWeak) checks.push({ s: 'info', t: `${s.marketName || '시장'} 40주선 아래`, d: '시장이 약해도 보유 판정은 기준선 그대로예요. 새로 사거나 더 살 때만 크기를 줄여요.' });
      if (s.overheat) checks.push({ s: 'info', t: '과열 구간' + (s.rsi > 0 ? ` (RSI ${Math.round(s.rsi)})` : ''), d: '매도 신호는 아니에요. 과열만 보고 팔거나 절반만 파는 것은 이 앱의 기준이 아니에요.' });
      if (tgap !== null && tgap >= 0 && tgap <= 2 && hasStop && gap > 0) checks.push({ s: 'info', t: `목표(저항)까지 ${tgap.toFixed(1)}%`, d: '목표는 익절 지점이 아니라 참고선이에요. 돌파하면 기준선이 따라 올라가요.' });

      // 지금 판다면: 기준과 비교한 매도 판단
      let ifSell = null;
      if (level === 'sell') {
          const late = state === 'lossBroken' && s.atr > 0 && cur < stop - s.atr;
          ifSell = { s: 'ok', t: late ? '늦었지만 기준에 맞는 매도예요' : '기준에 맞는 매도예요',
              d: state === 'lossBroken'
                  ? '손절선 아래에서 반등을 기다리며 버티면 손실이 커지는 경우가 많아요. 기준을 지키는 게 다음 기회를 지키는 방법이에요.'
                  : (state === 'beBroken' ? '번 것을 다 돌려주기 전에 본전을 지키는 매도예요. 다시 기준을 충족하면 그때 새로 진입하면 돼요.'
                                          : '오른 만큼을 지키는 매도예요. 다시 기준을 충족하면 그때 새로 진입하면 돼요.') };
      } else if (state === 'intraBelow') {
          ifSell = { s: 'info', t: '종가 확정 전 매도예요', d: `기준은 종가예요. 장 마감까지 지켜보는 게 기준에 맞아요.` };
      } else if (state === 'lossNear') {
          ifSell = { s: 'info', t: '손절선 근처에서의 매도예요', d: `손절선보다 ${gap.toFixed(1)}% 위예요. 기준상으로는 아직 보유 구간이에요.` + (closeBased ? ' 종가가 손절선 아래로 마감하면 그때 정리하면 돼요.' : ' 손절선에 감시 주문을 걸어 두면 기준대로 정리돼요.') };
      } else if (level === 'hold' && ret !== null && ret < 0) {
          ifSell = { s: 'info', t: '기준선보다 위에서의 매도예요', d: `손절선까지 ${gap.toFixed(1)}% 남아 있어요. 기준상으로는 보유 구간이에요. 일찍 파는 것도 선택이지만, 팔고 다시 사면 매번 거래 비용이 들어요.` };
      } else if (level === 'hold') {
          ifSell = { s: 'info', t: '기준선보다 위에서의 익절이에요', d: `기준상으로는 보유 구간이에요. 이 앱은 목표나 수익에서 다 파는 대신 ${lineName}을 따라 올리며 보유하는 쪽이 기준이에요.` };
      }

      // 가격 사다리: 위에서 아래로 높은 가격 순
      const rows = [];
      if (target > 0) rows.push({ k: '목표', v: target, d: tgap, note: s.targetBasis || '위쪽 저항 구간', target: true });
      if (cur > 0) rows.push({ k: '현재가', v: cur, d: null, note: ret !== null ? `평단 대비 ${pct(ret)}` : '', current: true });
      if (avg > 0) rows.push({ k: '평단', v: avg, d: cur > 0 ? (avg - cur) / cur * 100 : null, note: '' });
      if (hasStop) rows.push({ k: lineName, v: stop, d: -gap, line: true,
              note: planMode ? (L.trailOn ? `매수 후 최고 종가 ${fp(peak)} − ${L.K} ATR · 종가 기준` : '매수 때 정한 손절가 · 종가 기준') : breakEven ? '수익이 2 ATR을 넘어 기준선을 평단으로 올림' : (entryFloor ? '매수 때 정한 손절가 (차트 손절선이 더 내려가도 유지)' : (wideActive ? `평단 −${Math.round(wideK * 100)}% (앱 진입 계획 없이 산 종목은 여유 있게)` : (s.basis || ''))),
              note2: atLine !== null ? `이 가격에 팔면 ${pct(atLine)}` : '' });
      if ((breakEven || wideActive) && s.stop > 0 && cur > 0) rows.push({ k: '차트 손절선', v: s.stop, d: -(cur - s.stop) / cur * 100, note: s.basis || '' });
      rows.sort((a, b) => b.v - a.v);

      // 주의할 점 · 생각해 볼 것: 지금 상태에서 어떻게 대응하고 어떻게 생각하면 좋은지 (최대 4개)
      const notes = [];
      const c1 = cfg(), maxW1 = c1.maxWeight * 100;
      const over = acctW > 0 && acctW > maxW1, near = !over && acctW > 0 && acctW >= maxW1 * 0.8;
      const lossAtLine = hasStop && cur > 0 && stop < cur ? acctW * (cur - stop) / cur : 0;
      // R 배수(반 타프): 매수 때 손절가가 있으면 처음 위험(평단 − 매수 때 손절가)의 몇 배를 벌고 있는지
      const es = Number(s.entryStop) || 0, R0 = planMode && es > 0 && avg > es ? avg - es : 0;
      const rNow = R0 > 0 && cur > 0 ? (cur - avg) / R0 : null, rPeak = R0 > 0 && peak > 0 ? (peak - avg) / R0 : null;
      const rText = rNow !== null && rNow >= 1 ? `지금 +${rNow.toFixed(1)}R(처음 감수한 위험의 ${rNow.toFixed(1)}배)이에요. ` : '';
      if (!hasStop) {
          notes.push({ t: '손절 가격부터 정하기', d: '잃어도 괜찮은 범위를 먼저 정해 두세요. 가격을 정하지 않으면 떨어질 때마다 기준이 바뀌어요.' });
      } else if (level === 'sell') {
          notes.push({ t: '손절은 실패가 아니라 처음 정한 규칙이에요', d: '잃을 수 있는 범위를 미리 정해 두고 산 거라, 지금 정리하는 건 계획대로예요. 반등을 기다리며 기준선을 내리면 그 계획이 사라져요.', tag: '손실은 작을 때 끊기 · 윌리엄 오닐' });
          notes.push({ t: '본전을 만회하려는 매수는 하지 않기', d: '바로 다시 사거나 물타기하면 같은 손실을 한 번 더 겪기 쉬워요. 다시 들어가려면 진입 신호와 손절가를 새로 정하세요.', tag: '물타기 금지 · 폴 튜더 존스' });
          notes.push((gapDown || (state === 'lossBroken' && s.atr > 0 && cur < stop - s.atr))
              ? { t: '체결 가격은 기준선보다 낮을 수 있어요', d: '갭 하락이나 급락 뒤에는 시가가 기준선보다 많이 낮을 수 있어요. 실제 손실은 계획보다 클 수 있다는 걸 알고 정리하세요.' }
              : { t: '체결은 다음 거래일 시가예요', d: '종가로 판정하고 다음 날 시가에 체결돼서, 체결 가격이 기준선과 다를 수 있어요.' });
      } else if (state === 'intraBelow') {
          notes.push({ t: '장중 이탈은 아직 확정이 아니에요', d: `기준은 종가예요. 마감이 ${fp(stop)} 아래인지 확인하기 전에는 팔지도, 기준선을 내리지도 마세요.` });
          notes.push({ t: '마감 뒤에는 고민하지 않기', d: '아래로 마감하면 다음 날 시가에 정리하고, 위로 회복하면 그대로 보유해요. 미리 정한 대로만 하세요.' });
      } else if (state === 'lossNear') {
          notes.push({ t: atrPct >= gap ? '하루 만에 닿을 수 있어요' : '손절선이 가까워요', d: `하루 평균 변동폭이 ${atrPct > 0 ? atrPct.toFixed(1) + '%' : '손절선까지 거리와 비슷한 수준'}예요. 오늘 아래로 마감해도 이상하지 않은 거리라고 생각해 두세요.` });
          notes.push({ t: '손절선을 내리거나 물타기하지 않기', d: '가까워질수록 기준을 바꾸고 싶어져요. 그때 바꾼 기준은 처음 계획보다 손실이 더 커지는 쪽으로 가기 쉬워요.', tag: '물타기 금지 · 폴 튜더 존스' });
          notes.push({ t: '깨지면 고민 없이 규칙대로', d: '종가가 아래로 마감하면 다음 날 시가에 정리해요. 정리한 뒤에 오르더라도 규칙을 지킨 결정이에요.' });
      } else {
          // 우선순위: 비중 초과 → 큰 수익 반납 → 산 이유 점검 → 흔들림 → 손절선/수익 → 목표 → 비중 근접/추가 매수 (최대 4개)
          if (over) notes.push({ t: `비중이 최대를 넘었어요 (계좌의 ${acctW.toFixed(0)}%)`, d: `'${c1.label}' 성향 최대 ${maxW1.toFixed(0)}%보다 커요. 더 사지 마세요.${lossAtLine > 0 ? ` 손절선에서 정리되면 계좌가 약 ${lossAtLine.toFixed(1)}% 줄어요.` : ''} 이 크기를 감당할 수 있는지 보고 줄일지 정하세요.`, tag: '한 종목에 너무 많이 걸지 않기' });
          const giveback = rPeak !== null && rPeak >= 2 && rNow !== null && rNow < 1 && stop < avg;
          if (giveback)
              notes.push({ t: '큰 수익을 손실로 끝내지 않기', d: `한때 +${rPeak.toFixed(1)}R까지 올랐다가 지금 ${rNow >= 0 ? '+' : ''}${rNow.toFixed(1)}R이에요. 기준선 ${fp(stop)}이 아직 평단 아래라, 이대로 깨지면 손실로 끝나요. 이 위험을 그대로 둘지, 크기를 줄일지 직접 정하세요.`, tag: '마크 미너비니' });
          if (trendWeak) notes.push({ t: '산 이유를 다시 말해 보기', d: '긴 추세가 약해지고 있어요. 처음 산 이유를 한두 문장으로 말해 보세요. 그대로라면 기준선대로 들고 가고, 이유가 사라졌다면 크기를 줄일지 생각해 보세요.', tag: '2분 설명 · 피터 린치' });
          if (gap > 0) notes.push({ t: '손절선 전까지는 흔들려도 괜찮아요', d: `${atrPct > 0 ? `하루 평균 변동폭이 ${atrPct.toFixed(1)}%예요. 이 안의 움직임에는 반응하지 않아도 돼요. ` : ''}행동하는 기준은 종가가 손절선 아래로 마감하는 것 하나예요.`, tag: '기다림이 돈을 번다 · 제시 리버모어' });
          if (ret !== null && ret < 0) notes.push({ t: '손절선은 내리지 않기', d: '떨어지는 걸 보며 기준선을 낮추면 잃을 수 있는 범위가 처음 계획보다 커져요. 물타기도 같은 이유로 조심하세요.' });
          else if (ret !== null && ret > 0 && !giveback && (profitLine || (planMode && L.trailOn))) notes.push({ t: '수익은 손절선이 지켜 줘요', d: rText + '오른 만큼 손절선이 따라 올라가요. 더 오를지 떨어질지 맞히려 하기보다, 기준선이 깨질 때만 행동하세요.', tag: '수익은 길게 · 에드 세이코타' });
          if (tgap !== null && tgap > 0 && ret !== null && ret > 0) notes.push({ t: '목표는 참고선이에요', d: '닿았다고 다 팔 필요는 없어요. 닿은 뒤에도 손절선이 따라 올라가는지만 확인하세요.' });
          if (near) notes.push({ t: `최대 비중에 가까워요 (계좌의 ${acctW.toFixed(0)}%)`, d: `'${c1.label}' 성향 최대 ${maxW1.toFixed(0)}%까지 ${(maxW1 - acctW).toFixed(0)}%p 남았어요. 지금 크기는 괜찮지만, 더 사면 한도에 닿아요.` });
          else if (!over && !giveback && ret !== null && ret > 0) notes.push({ t: '더 사고 싶을 때', d: '오른 종목에 더 사면 평단이 올라가고 손절선까지 거리가 늘어요. 추가분의 손절가와 크기를 새로 정하고, 정하지 못했다면 사지 않는 게 나아요.' });
      }

      if (earnSoon && level !== 'sell') notes.unshift({ t: ed <= 1 ? '실적 발표 직전이에요' : `실적 발표까지 ${ed}일`,
          d: `발표 뒤에는 하루에 크게 움직일 수 있어요.${gapLoss !== null ? ` 기준선보다 1 ATR 아래에서 팔리면 계좌 약 −${gapLoss.toFixed(1)}%예요.` : ''} 이 크기가 부담되면 발표 전에 줄일지 정하세요. 줄이지 않아도 기준선대로 하면 돼요(발표 전 줄이기는 앱에서 시험 안 함).`, tag: '갭 위험' });
      const urgent = ['lossBroken', 'lossNear', 'beBroken', 'profitBroken', 'targetNear', 'intraBelow'].includes(state) || (earnSoon && ed <= 2);
      return { state, level, title, sub, tone, short, todo, rows, urgent, checks, ifSell, notes: notes.slice(0, 4),
       line: hasStop ? stop : 0, lineName, breakEven, profitLine, gap, ret, tgap, trendWeak, entryFloor, wideActive, planMode, closeBased, trailK: L.K, chartLine: s.stop };

  }

  /* 보유자가 지금 할 일 */
  /* 성향별 '신규 위험' 문구: 기본 위험(%)과 브레이크·시장 약세를 반영한 권장 범위 */
  function newRisk(plan) {
    const c = cfg();
    let r = c.riskPct * 100, why = [];
    if (c.brakeOn) { r /= 2; why.push(`계좌가 고점 대비 ${c.ddPct.toFixed(1)}%라 신규 위험을 절반으로`); }
    if (plan?.marketWeak) {
      if (c.marketWeak === 'HOLD') return { hold: true, text: `${plan.marketName || '시장'}이 40주선 아래예요. '${c.label}' 성향이라 신규 매수는 보류를 권해요.` };
      if (c.marketWeak === 'HALF') { r /= 2; why.push(`${plan.marketName || '시장'}이 40주선 아래라 절반으로`); }
      else why.push(`${plan.marketName || '시장'}이 40주선 아래예요 · 참고`);
    }
    return { hold: false, r, text: why.join(' · ') };
  }

  /* 분기 교체 직후인지: 이때 상위 20% 밖이면 기준 전략상 정리 자리 */
  function momExitWindow(d) { d = d || new Date(); return [0, 3, 6, 9].includes(d.getMonth()) && d.getDate() <= 10; }

  /* 모멘텀 기준 전략 보유 판정. s.mo: { ranked, pct, candidate, nextRebalance }. 없으면 순위 확인 필요 */
  function momJudge(s, ret, fmt) {
    const mo = s.mo || null, cur = Number(s.cur) || 0, avg = Number(s.avg) || 0;
    const ranked = !!mo?.ranked && mo.pct != null, pctTop = ranked ? Math.max(1, Math.ceil(Number(mo.pct))) : null;
    const next = mo?.nextRebalance || null, nextText = next ? `다음 교체일 ${next}` : '다음 분기 첫 거래일';
    const retText = ret !== null ? ` 평단 대비 ${pct(ret)}예요.` : '';
    let state, level, tone, short, title, sub, todo;
    if (!ranked) {
      state = 'momUnknown'; level = 'hold'; tone = 'neutral'; short = '모멘텀 · 순위 확인 필요';
      title = '모멘텀 전략으로 보유 중 · 순위를 아직 못 불러왔어요';
      sub = 'S&P500 밖 종목이거나 순위 계산 전이에요. 손절선 없이 분기 교체일에 순위로만 판단해요.' + retText;
      todo = { t: '모멘텀 화면에서 순위 확인하기', d: '상위 20% 안이면 교체일까지 보유, 밖이면 교체일에 정리해요.', tone: 'hold' };
    } else if (mo.candidate) {
      state = 'momHold'; level = 'hold'; tone = 'good'; short = `모멘텀 상위 ${pctTop}%`;
      title = `모멘텀 상위 ${pctTop}% · 분기 교체까지 보유`;
      sub = `기준 전략(상위 20%) 안이에요. 중간에 손절하지 않고 ${nextText}에 순위만 확인해요.` + retText;
      todo = { t: '할 일 없음 · 교체일에 순위만 확인', d: '오르내림에 반응하지 않는 게 이 전략의 규칙이에요. 위험은 비중으로 관리해요.', tone: 'hold' };
    } else if (momExitWindow()) {
      state = 'momExit'; level = 'sell'; tone = 'caution'; short = '모멘텀 20% 밖 · 교체 정리';
      title = `분기 교체 때 상위 ${pctTop}%로 20% 밖이에요`;
      sub = '기준 전략은 교체일에 상위 20%에 남은 종목만 들고 가요. 기준대로라면 정리하는 자리예요.' + retText;
      todo = { t: '정리하고 상위 20% 종목으로 바꾸기', d: '모멘텀 화면의 상위 20% 목록에서 새로 들어온 종목을 같은 비중으로 사는 게 기준 전략이에요.', tone: 'sell' };
    } else {
      state = 'momOut'; level = 'caution'; tone = 'caution'; short = `모멘텀 상위 ${pctTop}% · 20% 밖`;
      title = `지금 상위 ${pctTop}%로 20% 밖이에요 · 교체일까지는 보유`;
      sub = `기준 전략은 분기 중간에 팔지 않아요. ${nextText}에도 20% 밖이면 그때 정리해요.` + retText;
      todo = { t: `${nextText}에 다시 확인하기`, d: '순위는 매일 바뀌어요. 교체일 순위로만 판단해요.', tone: 'caution' };
    }
    const checks = [
      ranked ? { s: mo.candidate ? 'ok' : 'warn', t: `S&P500 12-1 모멘텀 상위 ${pctTop}%`, d: '상위 20% 안이면 기준 전략 대상이에요.' }
             : { s: 'info', t: '모멘텀 순위 없음', d: '' },
      { s: 'info', t: '손절선 없음 · 분기 교체로만 정리', d: '시험에서 중간 손절은 같은 투자 비중으로 나눠 든 것보다 낫지 않았어요. 손절된 종목의 절반 넘게는 계속 들고 있는 편이 나았어요.' },
      { s: 'info', t: '큰 하락은 비중으로 대비', d: '과거 상위 20% 보유의 최악 분기는 −15~−20%, 개별 종목은 하루 만에 −75%도 있었어요. 한 종목에 너무 많이 넣지 않는 게 유일한 대비예요.' }
    ];
    const med = Number.isFinite(Number(s.earnDays)) && s.earnDays !== null ? Number(s.earnDays) : null;
    if (med !== null && med >= 0 && med <= 7) checks.unshift({ s: 'warn', t: med === 0 ? '오늘 실적 발표' : `실적 발표 D-${med}`, d: '모멘텀 전략은 실적 발표에도 교체일까지 들고 가요. 하루 크게 빠질 수 있으니 비중이 견딜 만한지만 확인하세요.' });
    if (s.longMa > 0) checks.push(s.longDown ? { s: 'info', t: '40주선 아래 (참고)', d: '모멘텀 전략의 판정 기준은 아니에요.' } : { s: 'ok', t: '40주선 위 (참고)', d: '' });
    const rows = [];
    if (cur > 0) rows.push({ k: '현재가', v: cur, d: null, note: ret !== null ? `평단 대비 ${pct(ret)}` : '', current: true });
    if (avg > 0) rows.push({ k: '평단', v: avg, d: cur > 0 ? (avg - cur) / cur * 100 : null, note: '' });
    rows.sort((a, b) => b.v - a.v);
    const notes = [
      { t: '흔들려도 교체일까지 기다리기', d: '분기 중간의 급락에 팔면 이 전략의 수익 대부분을 놓치기 쉬워요. 견디기 어려운 크기라면 처음부터 비중을 줄이는 게 맞아요.', tag: '모멘텀 기준 전략' },
      { t: '추가 매수는 교체일에만', d: '기준 전략은 교체일에 같은 비중으로 맞춰요. 중간에 오른 종목을 더 사거나 떨어진 종목에 물타기하지 않아요.' }
    ];
    const ifSell = level === 'sell' ? { s: 'ok', t: '기준에 맞는 정리예요', d: '분기 교체에서 상위 20% 밖이라 기준 전략상 바꾸는 자리예요.' }
                 : { s: 'info', t: '교체일 전 매도예요', d: '기준 전략은 분기 중간에 팔지 않아요. 파는 것도 선택이지만 전략의 규칙과는 달라요.' };
    return { state, level, title, sub, tone, short, todo, rows, urgent: level !== 'hold', checks, ifSell, notes,
      line: 0, lineName: '', breakEven: false, profitLine: false, gap: null, ret, tgap: null, trendWeak: false, entryFloor: false,
      wideActive: false, planMode: false, closeBased: true, trailK: null, chartLine: s.stop, momMode: true, mo };
  }

  /* 모멘텀 보유의 '지금 할 일' (분석 화면) */
  function momActions(j, s, h) {
    const acts = [];
    const nextText = j.mo?.nextRebalance ? `다음 교체일 ${j.mo.nextRebalance}` : '다음 분기 첫 거래일';
    if (j.level === 'sell') acts.push({ s: 'bad', t: '정리하고 상위 20% 종목으로 바꾸기', d: '분기 교체에서 20% 밖이에요. 모멘텀 화면의 상위 20% 목록을 같은 비중으로 사는 게 기준 전략이에요.' });
    else acts.push({ s: 'info', t: `${nextText}에 순위 확인`, d: j.state === 'momOut' ? '지금은 20% 밖이에요. 교체일에도 밖이면 정리해요.' : '상위 20% 안이면 그대로 들고 가요. 그 전에는 할 일이 없어요.' });
    const c = cfg(), w = Number(h?.accountWeight) || Number(h?.weight) || 0, maxW = c.maxWeight * 100;
    if (w > 0) acts.push(w > maxW
      ? { s: 'warn', t: `비중 ${w.toFixed(0)}% · 최대 ${maxW.toFixed(0)}%를 넘었어요`, d: '손절 없이 들고 가는 전략이라 크기가 유일한 위험 관리예요. 이 종목이 반토막 나도 견딜 수 있는지 보고 줄일지 정하세요.' }
      : { s: 'info', t: `비중 ${w.toFixed(0)}%`, d: `이 종목이 −50%면 계좌 약 −${(w / 2).toFixed(1)}%예요. 손절 대신 이 크기로 위험을 정해요.` });
    acts.push({ s: 'info', t: '중간 추가 매수 · 물타기 없음', d: '교체일에 같은 비중으로 맞추는 게 기준 전략이에요.' });
    return acts;
  }

  /* 보유자가 지금 할 일 */
  function holdActions(j, s, h, plan, fmt) {
    if (j?.momMode) return momActions(j, s, h);
    const acts = [];
    if (!j || !j.line) return acts;
    const c = cfg();
    const cur = s.cur, avg = s.avg, line = j.line;
    const hasAcct = Number(h?.accountWeight) > 0;
    const weight = hasAcct ? Number(h.accountWeight) : (Number(h?.weight) || 0), qty = Number(h?.quantity) || 0;
    const lossAtLine = cur > 0 ? (cur - line) / cur * 100 : 0;             // 현재가 → 기준선 하락폭
    const acctRisk = weight > 0 && lossAtLine > 0 ? weight * lossAtLine / 100 : null; // 기준선에서 정리되면 계좌 영향(%p)
    const base = c.riskPct * 100;

    if (j.level === 'sell') {
      acts.push({ s: 'bad', t: j.closeBased ? (s.sessionOpen ? '오늘 장에서 정리하기' : '다음 거래일 시가에 정리하기') : '정리하기', d: (j.closeBased ? `종가가 ${j.lineName} ${fmt(line)} 아래로 마감했어요.` : `${j.lineName} ${fmt(line)}을 깼어요.`) + (j.ret !== null ? ` 지금 팔면 평단 대비 ${pct(j.ret)}예요.` : '') + ' 다시 진입 신호가 나오면 그때 새로 들어가면 돼요.' });
      return acts;
    }
    // 이렇게 되면: 지금 가격에서 가까운 자리부터
    const atr = Number(s.atr) || 0;
    const far = p => cur > 0 ? Math.abs(p - cur) / cur * 100 : 0;
    const signed = p => pct((p / cur - 1) * 100);
    const sc = [];
    if (j.planMode && atr > 0) {
      const peak = Number(s.peak) || 0, K = j.trailK || 3, es = Number(s.entryStop) || 0;
      const trailStart = es > 0 ? es + K * atr : 0;
      if (trailStart > cur && line <= es + 1e-9) sc.push({ p: trailStart, a: { s: 'up', t: `종가가 ${fmt(trailStart)}(${signed(trailStart)}) 넘으면 손절선이 올라가기 시작`, d: '앱이 자동으로 올려요. 따로 할 일은 없어요.' } });
      else if (j.profitLine || line > es) sc.push({ p: cur + atr, a: { s: 'up', t: '오르면 손절선도 따라 올라가요', d: `지금 손절선은 최고 종가 ${fmt(peak)} − ${K} ATR이에요. 따로 할 일은 없어요.` } });
    } else if (!j.breakEven && !j.profitLine && avg > 0 && atr > 0 && avg + 2 * atr > cur) {
      const be = avg + 2 * atr;
      sc.push({ p: be, a: { s: 'up', t: `${fmt(be)}(${signed(be)}) 넘으면 손절선이 평단으로 올라가요`, d: '그 뒤로는 떨어져도 본전에서 정리돼요. 감시 주문 가격만 옮겨 주세요.' } });
    }
    if (s.target > 0 && j.tgap !== null && j.tgap > 0) sc.push({ p: s.target, a: { s: 'up', t: `${fmt(s.target)}(${signed(s.target)}, 목표) 부근에 가도 다 팔지 않기`, d: '위쪽 저항 구간이에요. 손절선을 따라 올리며 보유해요.' } });
    const ep = plan?.primaryEntry > 0 && plan.primaryEntry < cur && plan.primaryEntry > line ? plan.primaryEntry : 0;
    if (ep && plan?.verdict !== 'ACTIONABLE') sc.push({ p: ep, a: { s: 'down', t: `${fmt(ep)}(${signed(ep)}, 진입 구간)까지 눌리면`, d: '보유분은 그대로 두세요. 추가 매수는 양봉이 나올 때 아래 기준으로 정해요.' } });
    sc.push({ p: line, a: j.closeBased ? { s: 'down', t: `종가가 ${fmt(line)}(${signed(line)}) 아래로 마감하면 다음 날 시가에 정리`,
      d: '장중에 잠깐 깨지는 건 괜찮아요.' + (avg > 0 ? ` 정리되면 평단 대비 ${pct((line / avg - 1) * 100)}예요.` : '') }
      : { s: 'down', t: `${fmt(line)}(${signed(line)})에서 정리`, d: '감시 주문을 걸어 두면 깨질 때(장중 포함) 자동으로 정리돼요.' + (avg > 0 ? ` 평단 대비 ${pct((line / avg - 1) * 100)}예요.` : '') } });
    sc.sort((a, b) => far(a.p) - far(b.p)).forEach(x => acts.push(x.a));

    // 매도 기준 (미국 모멘텀 기준 전략)
    const mo = plan?.mo;
    if (mo?.ranked) acts.push(mo.candidate
      ? { s: 'info', t: `분기 교체 때 모멘텀 상위 20%에 남아 있으면 보유`, d: `지금 상위 ${Math.max(1, Math.ceil(mo.pct))}%예요.${mo.nextRebalance ? ` 다음 교체일 ${mo.nextRebalance}에` : ' 다음 분기 첫 거래일에'} 상위 20% 밖이면 기준 전략상 정리하는 자리예요. 이 전략으로 들고 있다면 포트폴리오 '보유 정보 수정'에서 모멘텀 전략을 고르면 손절선 대신 교체일 기준으로 판정해요.` }
      : { s: 'info', t: `모멘텀 상위 ${Math.ceil(mo.pct)}% · 기준 전략 대상 아님`, d: '이 종목의 매도 기준은 위의 손절선뿐이에요. 기준 전략(상위 20%)으로 갈아탈지는 분기 교체일에 따로 정하면 돼요.' });

    // 비중: 원금 기준 손실로 판단하고, 수익 반납분은 따로 말한다
    const costLoss = weight > 0 && avg > 0 && cur > 0 && line < avg ? weight * (avg - line) / cur : 0;
    if (costLoss > c.warnRiskPct && qty > 0) {
      const target = base * 1.5;
      const keep = Math.max(1, Math.floor(qty * target / costLoss));
      acts.push({ s: 'warn', t: '비중 줄이기 검토', d: `손절선에서 정리되면 원금 기준 계좌가 약 ${costLoss.toFixed(1)}% 줄어요(갭 하락이면 더). 참고 기준(성향별 종목당 위험의 2배, ${c.warnRiskPct}%)보다 커요. ${keep.toLocaleString('ko-KR')}주 정도로 줄이면 약 ${target.toFixed(1)}%예요.`
        + (hasAcct ? '' : ' (운용 자금을 넣지 않아 보유 종목 합계 기준이에요)') });
    } else if (acctRisk !== null && acctRisk > c.warnRiskPct) {
      acts.push({ s: 'info', more: true, t: `손절선까지 오면 지금 평가액보다 계좌 약 −${acctRisk.toFixed(1)}%`, d: `대부분 지금까지 난 수익을 돌려주는 몫이에요(원금 기준 ${costLoss > 0 ? '약 −' + costLoss.toFixed(1) + '%' : '손실 없음'}). 손절선이 따라 올라가면 이 폭은 줄어요.` });
    }

    // 추가 매수: 크기 한도 + 근거
    const v = plan?.verdict;
    const nr = newRisk(plan);
    const sizeScale = nr.hold || !(base > 0) ? 1 : Math.min(1, nr.r / base);
    const maxW = c.maxWeight * 100 * sizeScale, roomW = maxW - weight;
    const value = cur * qty, equity = weight > 0 && value > 0 ? value / (weight / 100) : 0;
    const roomAmt = equity > 0 && roomW > 0 ? equity * roomW / 100 : 0, roomQty = cur > 0 ? Math.floor(roomAmt / cur) : 0;
    const roomText = weight > 0 ? (roomW <= 0.5 ? ` 이미 '${c.label}' 성향 최대 비중 ${maxW.toFixed(0)}%에 닿아 있어 더 살 여유가 없어요.`
                       : (equity > 0 ? ` 최대 비중 ${maxW.toFixed(0)}%까지 약 ${fmt(roomAmt)} 여유가 있어요${roomQty > 0 ? `(약 ${roomQty}주)` : '(1주 가격보다 적어요)'}.` : ` 최대 비중 ${maxW.toFixed(0)}%까지 ${roomW.toFixed(0)}%p 여유가 있어요.`)) : '';
    const pyrNote = j.ret !== null && j.ret > 0 ? ' 오른 종목에 더 사면 평단이 올라가요. 추가해도 손절선은 지금보다 내려가지 않게 유지돼요.' : '';
    if (v === 'ACTIONABLE' && plan.stopLoss > 0 && plan.stopLoss < cur) {
      acts.push(nr.hold
        ? { s: 'info', t: '추가 매수 자리지만 보류 권장', d: nr.text }
        : { s: 'info', t: '추가 매수: 기술적 조건 충족',
            d: `진입 손절 ${fmt(plan.stopLoss)} 기준으로 새 진입처럼 보세요. 추가분의 손절까지 손실이 계좌의 ${nr.r.toFixed(nr.r < 1 ? 2 : 1).replace(/\.?0+$/, '')}% 안이 되게요.` + roomText
               + (nr.text ? ` (${nr.text})` : '') + (j.ret !== null && j.ret < 0 ? ' 평단을 낮추려는 목적이라면 하지 마세요.' : '') + pyrNote });
    } else if (j.ret !== null && j.ret < 0) {
      acts.push({ s: 'warn', t: '물타기 하지 않기', d: `평단 대비 ${pct(j.ret)}예요. 지금은 차트상 진입 자리가 아니라, 평단을 낮추려는 추가 매수는 손실을 키우기 쉬워요.` });
    } else {
      acts.push({ s: 'info', t: '추가 매수: 지금은 진입 자리 아님', d: (ep ? `진입 구간(${fmt(ep)} 부근)에서 양봉이 나오면 다시 확인해 보세요.` : '지금은 진입 계획이 없어요.') + roomText + pyrNote });
    }
    if (c.brakeOn) acts.push({ s: 'warn', t: `계좌 낙폭 ${c.ddPct.toFixed(1)}% · 신규 위험 절반`, d: `'${c.label}' 성향의 브레이크(고점 대비 −${Math.round(c.brakeDd * 100)}%)에 걸렸어요. 보유 종목은 기준선대로 두고, 새로 살 때만 평소의 절반으로 사요.` });
    return acts;
  }

  global.holdJudge = holdJudge;
  global.holdLine = holdLine;
  global.holdMomExitWindow = momExitWindow;
  global.holdActions = holdActions;
  global.holdPct = pct;
  global.holdCfg = cfg;
  global.holdNewRisk = newRisk;
})(window);
