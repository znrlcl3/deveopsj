package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.market.config.NasdaqDirectoryProperties;

@ExtendWith(MockitoExtension.class)
class NasdaqDirectoryServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private NasdaqDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new NasdaqDirectoryService(restTemplate, new NasdaqDirectoryProperties());
    }

    @Test
    void 나스닥파일에서_테스트종목과_생성시각행을_제외한다() {
        String content = """
                Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares
                AAPL|Apple Inc. - Common Stock|Q|N|N|100|N|N
                QQQ|Invesco QQQ Trust|G|N|N|100|Y|N
                ZTEST|Test Security|Q|Y|N|100|N|N
                File Creation Time: 0728202621:00|||||||
                """;

        var result = service.parseNasdaqListed(content);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).shortCode()).isEqualTo("AAPL");
        assertThat(result.get(0).market()).isEqualTo("NASDAQ");
        assertThat(result.get(1).securityGroup()).isEqualTo("ETF");
    }

    @Test
    void 타거래소파일의_거래소와_ETF여부를_변환한다() {
        String content = """
                ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol
                BRK.B|Berkshire Hathaway Inc. Class B|N|BRK.B|N|100|N|BRK.B
                SPY|SPDR S&P 500 ETF Trust|P|SPY|Y|100|N|SPY
                File Creation Time: 0728202621:00|||||||
                """;

        var result = service.parseOtherListed(content);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).market()).isEqualTo("NYSE");
        assertThat(result.get(1).market()).isEqualTo("NYSE Arca");
        assertThat(result.get(1).securityGroup()).isEqualTo("ETF");
    }
}
