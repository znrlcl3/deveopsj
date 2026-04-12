/**
 * 마스터 코드를 조회하여 select 태그에 option을 자동으로 그려주는 공통 함수
 * @param {string} groupCode - 조회할 마스터 그룹 코드 (예: 'SPEND_CATEGORY')
 * @param {string} targetId - option을 붙일 select 태그의 ID
 * @param {string} defaultText - 맨 위에 띄울 기본 텍스트
 */
async function loadMasterCodes(groupCode, targetId, defaultText = "선택하세요") {
    const selectElement = document.getElementById(targetId);
    if (!selectElement) return;

    try {
        // 1. API 호출 (대리님이 만든 master_codes 찌르기)
        const response = await fetch(`/api/codes/${groupCode}`);
        if (!response.ok) throw new Error('코드 조회 실패');
        
        const codes = await response.json();

        // 2. 기존 option 초기화 및 기본 텍스트 세팅
        selectElement.innerHTML = `<option value="NONE">${defaultText}</option>`;

        // 3. 받아온 데이터로 동적 option 생성
        codes.forEach(code => {
            const option = document.createElement('option');
            option.value = code.codeValue; // DB의 code_value
            option.textContent = code.codeName; // DB의 code_name
            selectElement.appendChild(option);
        });
    } catch (error) {
        console.error(`[${groupCode}] 마스터 코드 로드 에러:`, error);
        selectElement.innerHTML = `<option value="NONE">코드 불러오기 실패</option>`;
    }
}