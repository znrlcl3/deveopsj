document.querySelectorAll('.duplicate-confirm').forEach(checkbox => {
    const select = document.getElementById(checkbox.dataset.selectId);
    if (!select) return;
    const update = () => {
        select.disabled = !checkbox.checked;
        if (!checkbox.checked) select.value = '';
        else if (select.options.length === 2 && !select.value) select.selectedIndex = 1;
    };
    checkbox.addEventListener('change', update);
    update();
});


const mappingFields = [...document.querySelectorAll('.foreign-fields')];
mappingFields.forEach(fields => {
    const select = document.getElementById(fields.dataset.selectId);
    const picker = fields.querySelector('.asset-picker');
    if (!select || !picker) return;
    const update = () => {
        picker.disabled = select.disabled;
        picker.required = !select.disabled && !!select.value;
        fields.querySelector('.asset-search').disabled = select.disabled;
    };
    select.addEventListener('change', update);
    document.querySelectorAll('.duplicate-confirm').forEach(box => {
        if (box.dataset.selectId === fields.dataset.selectId) box.addEventListener('change', update);
    });
    picker.addEventListener('change', () => {
        const chosen = picker.selectedOptions[0];
        mappingFields.forEach(otherFields => {
            const other = otherFields.querySelector('.asset-picker');
            if (other.dataset.sourceName !== picker.dataset.sourceName) return;
            if (picker.value && ![...other.options].some(option => option.value === picker.value)) other.add(new Option(chosen.text, chosen.value));
            other.value = picker.value;
            const target = document.getElementById(otherFields.dataset.selectId);
            if (target && !target.disabled) {
                if (picker.value && !target.value) target.selectedIndex = 1;
                if (!picker.value) target.value = '';
                target.dispatchEvent(new Event('change'));
            }
        });
    });
    let requestNumber = 0;
    fields.querySelector('.asset-search').addEventListener('click', async () => {
        const status = fields.querySelector('.asset-search-status');
        const keyword = fields.querySelector('.asset-query').value.trim();
        if (keyword.length < 2) { status.textContent = '두 글자 이상 입력하세요.'; return; }
        const request = ++requestNumber;
        status.textContent = '종목 검색 중…';
        try {
            const response = await fetch('/trades/import/assets?keyword=' + encodeURIComponent(keyword), {headers:{Accept:'application/json'}});
            if (!response.ok) throw new Error();
            const results = await response.json();
            if (request !== requestNumber) return;
            // Keep the current selection; searching alone never changes the saved target.
            const current = picker.selectedOptions[0];
            const selectedValue = picker.value;
            const selectedText = current && current.text;
            picker.replaceChildren(new Option('종목 후보를 선택하세요', ''));
            results.forEach(item => picker.add(new Option(item.label, String(item.id))));
            if (selectedValue && !results.some(item => String(item.id) === selectedValue)) picker.add(new Option(selectedText, selectedValue));
            picker.value = selectedValue;
            status.textContent = results.length ? '검색 결과에서 연결할 종목을 선택하세요.' : '등록된 종목이 없습니다. 종목 목록 동기화 후 다시 확인하세요.';
        } catch (error) { if (request === requestNumber) status.textContent = '검색하지 못했습니다. 다시 시도하세요.'; }
    });
    update();
});
