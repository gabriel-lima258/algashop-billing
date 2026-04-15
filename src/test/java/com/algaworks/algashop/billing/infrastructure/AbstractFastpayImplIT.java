package com.algaworks.algashop.billing.infrastructure;

import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardRepository;
import com.algaworks.algashop.billing.domain.model.creditcard.LimitedCreditCard;
import com.algaworks.algashop.billing.infrastructure.creditcard.fastpay.*;
import com.algaworks.algashop.billing.infrastructure.payment.AlgaShopPaymentProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.time.Year;
import java.util.Collections;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Import(FastpayCreditCardTokenizationAPIClientConfig.class)
public abstract class AbstractFastpayImplIT {

    @Autowired
    protected FastpayCreditCardTokenizationAPIClient tokenizationAPIClient;

    @Autowired
    protected CreditCardProviderServiceFastpayImpl cardProviderServiceFastpay;

    @Autowired
    protected CreditCardRepository creditCardRepository;

    @Autowired
    private AlgaShopPaymentProperties paymentProperties;

    protected static final UUID validCustomerId = UUID.randomUUID();
    protected static final String alwaysPaidCardNumber = "4622943127011022";

    protected static WireMockServer wireMockServer;

    // wiremock dentro do java
    public static void startMock() {
        wireMockServer = new WireMockServer(options()
                .port(8788)
                .usingFilesUnderDirectory("src/test/resources/wiremock/fastpay")
                .extensions(new ResponseTemplateTransformer(
                        TemplateEngine.defaultTemplateEngine(),
                        true,
                        new ClasspathFileSource("src/test/resources/wiremock/fastpay"),
                        Collections.emptyList()
                ))
        );

        wireMockServer.start();
    }

    public static void stopMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetWireMockScenarios() {
        String hostname = paymentProperties.getFastpay().getHostname();
        RestClient.create(hostname)
                .post()
                .uri("/__admin/scenarios/reset")
                .retrieve()
                .toBodilessEntity();
    }

    protected LimitedCreditCard registerCard() {
        FastpayTokenizationInput input = FastpayTokenizationInput.builder()
                .number(alwaysPaidCardNumber)
                .cvv("222")
                .expMonth(1)
                .holderName("John Doe")
                .holderDocument("12345")
                .expYear(Year.now().getValue() + 5)
                .build();

        FastpayTockenizedCreditCardModel response = tokenizationAPIClient.tokenize(input);
        return cardProviderServiceFastpay.register(validCustomerId, response.getTokenizedCard());
    }
}
