/*
  페이지 공용 접근성 디렉티브 (Alpine). Alpine 스크립트보다 먼저, defer 없이 불러온다.

  x-dialog="열림 상태 식"
    모달 시트에 붙인다. 마크업에는 role="dialog" aria-modal="true" aria-labelledby tabindex="-1"을 함께 둔다.
    - 열릴 때: 직전 포커스를 기억하고 [data-autofocus](보이는 경우) 또는 시트 자체로 포커스를 옮긴다
    - 열려 있는 동안: Tab / Shift+Tab이 시트 밖으로 나가지 않고, 배경 스크롤을 잠근다
    - 닫힐 때: 기억해 둔 요소로 포커스를 돌려준다
    Esc 닫기는 시트의 @keydown.escape 로 각 페이지가 자기 닫기 함수를 부른다.

  x-arrow-nav
    검색 결과 목록 컨테이너에 붙이면 안의 [data-nav-item] 사이를 ↑/↓로 이동한다.
    첫 항목에서 ↑를 누르면 [data-nav-input](같은 검색 영역의 입력창)으로 돌아간다.
*/
document.addEventListener('alpine:init', () => {
    const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), '
        + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const isVisible = el => el.getClientRects().length > 0;
    let openDialogs = 0;   // 모달 위에 모달이 겹칠 수 있어서 잠금은 개수로 관리한다

    Alpine.directive('dialog', (el, { expression }, { evaluateLater, effect, cleanup }) => {
        const readOpen = evaluateLater(expression);
        let wasOpen = false;
        let returnTo = null;

        const onKeydown = e => {
            if (e.key !== 'Tab') return;
            const nodes = [...el.querySelectorAll(FOCUSABLE)].filter(isVisible);
            if (nodes.length === 0) { e.preventDefault(); el.focus(); return; }
            const first = nodes[0];
            const last = nodes[nodes.length - 1];
            if (e.shiftKey && (document.activeElement === first || document.activeElement === el)) {
                e.preventDefault(); last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault(); first.focus();
            }
        };
        el.addEventListener('keydown', onKeydown);

        const setOpen = open => {
            if (open === wasOpen) return;
            wasOpen = open;
            if (open) {
                returnTo = document.activeElement;
                openDialogs++;
                document.body.style.overflow = 'hidden';
                // x-show가 화면에 띄운 다음 프레임에 포커스해야 실제로 옮겨진다
                requestAnimationFrame(() => {
                    if (el.contains(document.activeElement)) return;   // 페이지가 이미 시트 안에 포커스를 둔 경우
                    const target = [...el.querySelectorAll('[data-autofocus]')].find(isVisible);
                    (target || el).focus({ preventScroll: true });
                });
            } else {
                openDialogs = Math.max(0, openDialogs - 1);
                if (openDialogs === 0) document.body.style.overflow = '';
                const target = returnTo;
                returnTo = null;
                if (target && target.isConnected && typeof target.focus === 'function') target.focus({ preventScroll: true });
            }
        };

        effect(() => readOpen(value => setOpen(!!value)));
        cleanup(() => {
            el.removeEventListener('keydown', onKeydown);
            setOpen(false);
        });
    });

    Alpine.directive('arrow-nav', (el, _, { cleanup }) => {
        const onKeydown = e => {
            if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
            const items = [...el.querySelectorAll('[data-nav-item]')].filter(i => isVisible(i) && !i.disabled);
            const index = items.indexOf(document.activeElement);
            if (index === -1) return;
            e.preventDefault();
            if (e.key === 'ArrowDown') {
                items[Math.min(index + 1, items.length - 1)].focus();
            } else if (index > 0) {
                items[index - 1].focus();
            } else {
                el.parentElement?.querySelector('[data-nav-input]')?.focus();
            }
        };
        el.addEventListener('keydown', onKeydown);
        cleanup(() => el.removeEventListener('keydown', onKeydown));
    });
});

/* 검색 입력창에서 ↓: 결과 목록의 첫 항목으로 포커스를 옮긴다.
   사용: <input data-nav-input @keydown.down="focusFirstNavItem($event, $refs.results)"> */
window.focusFirstNavItem = function (event, container) {
    const first = container && [...container.querySelectorAll('[data-nav-item]')]
        .find(i => i.getClientRects().length > 0 && !i.disabled);
    if (!first) return;
    event.preventDefault();
    first.focus();
};
