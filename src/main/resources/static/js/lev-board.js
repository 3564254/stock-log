/* 레버리지 단타 신호판 공통. 시장 화면 · 홈에서 같이 쓴다 */
window.LevBoard = (() => {
  const fmt = (v, kr) => v == null ? '-' : (kr ? Math.round(v).toLocaleString() + '원' : '$' + Number(v).toFixed(2));
  const pct = v => v == null || !isFinite(v) ? '-' : (v > 0 ? '+' : '') + Number(v).toFixed(1) + '%';
  const first = s => String(s || '').split(' ')[0];
  async function load() {
    try {
      const r = await fetch('/api/lev-signal', { headers: { 'Accept': 'application/json' } });
      if (r.ok && !r.redirected) return await r.json();
    } catch (e) { /* 칸을 숨긴다 */ }
    return null;
  }
  const md = d => d ? String(d).slice(5).replace('-', '/') : '';
  // 국내 다음 마감에 쓰일 VIX = 이미 나온 최근 미국 VIX → 지금 알 수 있다
  function krVix(b) {
    if (!b || b.vix == null || b.vixAvg10 == null) return null;
    const need = Math.max(20, b.vixAvg10 * 1.1);
    return { ok: b.vix >= need, need };
  }
  function prep(b, reach) {
    reach = reach || 4;
    if (!b) return [];
    const out = [], kv = krVix(b);
    for (const x of (b.rows || [])) {
      if (x.state === 'SIGNAL' || x.state === 'NONE' || x.close == null) continue;
      if (x.up && x.bullTriggerPct != null && x.bullTriggerPct >= -reach) {
        const floorNear = x.bullFloor != null && x.bullFloor > x.bullTrigger * 0.97;
        const vixLine = x.kr
          ? (kv ? (kv.ok ? `VIX 조건 충족 (VIX ${b.vix.toFixed(1)} ≥ ${kv.need.toFixed(1)})` : `VIX 조건 미충족 (VIX ${b.vix.toFixed(1)} < ${kv.need.toFixed(1)}) → 신호 안 떠요`) : 'VIX 정보 없음')
          : (b.vixNeedTonight != null ? `마감 VIX ${b.vixNeedTonight.toFixed(1)} 이상이어야 해요` : 'VIX 기준 정보 없음');
        out.push({
          kind: 'bull', x, dist: Math.abs(x.bullTriggerPct), blocked: x.kr && kv && !kv.ok,
          t: `${x.name}(${x.code}) 종가 ${fmt(x.bullTrigger, x.kr)} (${pct(x.bullTriggerPct)}) 이하`,
          d: `${vixLine} · ${first(x.lev)}는 대략 ${pct(x.k * x.bullTriggerPct)} 자리` + (floorNear ? ` · 단, ${fmt(x.bullFloor, x.kr)} 아래로 마감하면 200일 평균 아래라 대상 아님` : ''),
          when: x.kr ? '국내 15:30 마감' : '미국 마감 (한국 새벽 5~6시)'
        });
      }
      if (!x.kr && x.up === false && x.inv && x.invTriggerPct != null && x.invTriggerPct <= reach) {
        const ceilNear = x.invCeil != null && x.invCeil < x.invTrigger * 1.03;
        out.push({
          kind: 'inv', x, dist: Math.abs(x.invTriggerPct),
          t: `${x.name}(${x.code}) 종가 ${fmt(x.invTrigger)} (${pct(x.invTriggerPct)}) 이상 → ${x.inv} 인버스 참고`,
          d: `${x.inv}는 대략 ${pct(-Math.abs(x.ki) * x.invTriggerPct)} 자리 · 근거가 약한 참고 신호예요` + (ceilNear ? ` · 단, ${fmt(x.invCeil)} 위로 마감하면 200일 평균 위라 대상 아님` : ''),
          when: '미국 마감 (한국 새벽 5~6시)'
        });
      }
    }
    return out.sort((a, b) => (a.blocked ? 1 : 0) - (b.blocked ? 1 : 0) || a.dist - b.dist);
  }
  const signals = b => (b?.rows || []).filter(x => x.state === 'SIGNAL');
  return { load, prep, signals, fmt, pct, md, krVix };
})();
