(() => {
    const form = document.getElementById('holdingForm');
    if (!form) return;
    const search = document.getElementById('search');
    const id = form.elements.investmentAssetId;
    const results = document.getElementById('results');
    const info = document.getElementById('assetInfo');
    const currency = document.getElementById('currency');
    const fx = form.elements.exchangeRate;
    const plan = form.elements.assetPlanId;
    if (plan.options.length === 2 && !plan.value) plan.selectedIndex = 1;
    let timer, generation = 0;
    function preview() {
        const total = Number(form.elements.quantity.value) * Number(form.elements.averagePrice.value);
        document.getElementById('costPreview').textContent = id.value && total > 0 && Number.isFinite(total)
            ? `입력 기준 매입원금 약 ${total.toLocaleString('ko-KR', {maximumFractionDigits: 4})} ${currency.textContent}` : '';
    }
    form.addEventListener('input', preview);
    search.addEventListener('input', () => {
        const revision = ++generation;
        id.value = ''; results.replaceChildren(); currency.textContent = '';
        fx.disabled = true; fx.required = false;
        document.getElementById('fxFields').hidden = true;
        clearTimeout(timer); preview();
        const keyword = search.value.trim();
        info.textContent = '검색 결과에서 종목을 선택하세요.';
        if (keyword.length < 2) return;
        timer = setTimeout(async () => {
            try {
                const response = await fetch('/api/investment-assets/search?keyword=' + encodeURIComponent(keyword));
                if (!response.ok) throw new Error();
                const assets = await response.json();
                if (revision !== generation) return;
                results.replaceChildren();
                info.textContent = assets.length ? '종목을 선택하세요.' : '검색 결과가 없습니다. 종목정보 화면에서 종목을 동기화해 주세요.';
                for (const asset of assets) {
                    const button = document.createElement('button');
                    button.type = 'button'; button.className = 'list-group-item list-group-item-action';
                    button.textContent = `${asset.assetName} (${asset.symbol}) · ${asset.market} · ${asset.currency}`;
                    button.addEventListener('click', () => {
                        ++generation; id.value = asset.id; search.value = `${asset.assetName} (${asset.symbol})`;
                        search.setCustomValidity(''); currency.textContent = asset.currency;
                        info.textContent = `${asset.market} · ${asset.currency} 기준 평균 매수가를 입력하세요.`;
                        results.replaceChildren();
                        const foreign = asset.currency !== 'KRW';
                        fx.disabled = !foreign; fx.required = foreign;
                        document.getElementById('fxFields').hidden = !foreign;
                        preview();
                    });
                    results.appendChild(button);
                }
            } catch (error) {
                if (revision === generation) info.textContent = '종목 검색에 실패했습니다. 잠시 후 다시 시도하세요.';
            }
        }, 250);
    });
    form.addEventListener('submit', event => {
        if (!id.value) { event.preventDefault(); search.setCustomValidity('검색 결과에서 종목을 선택해 주세요.'); search.reportValidity(); }
    });
})();
