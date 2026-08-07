package com.algaworks.algashop.billing.infrastructure.creditcard.fastpay;

import com.algaworks.algashop.billing.infrastructure.payment.AlgaShopPaymentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class FastpayCreditCardAPIClientConfig {

    @Bean
    public FastpayCreditCardAPIClient fastpayCreditCardAPIClient(
            RestClient.Builder builder,
            AlgaShopPaymentProperties properties
    ) {
        var fastpayProperties = properties.getFastpay();

        // inserir o token privado em todas requisicoes
        RestClient restClient = builder.baseUrl(fastpayProperties.getHostname())
                .requestFactory(generateClientHttpRequestFactory())
                .requestInterceptor(((request, body, execution) -> {
                    // set e nao add: com add, um header ja presente viraria dois valores
                    request.getHeaders().set("Token", fastpayProperties.getPrivateToken());
                    return execution.execute(request, body);
                })).build();

        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(adapter).build();

        return proxyFactory.createClient(FastpayCreditCardAPIClient.class);
    }

    // O timeout e a camada mais importante das quatro: e ele que transforma "pendurado" em
    // "falhou". Sem ele o circuito nunca abre - ele so reage a falhas que TERMINARAM - e a
    // thread fica presa para sempre. O default do JDK aqui e infinito.
    //
    // JdkClientHttpRequestFactory no lugar do SimpleClientHttpRequestFactory: o Simple usa
    // HttpURLConnection, que nao tem pool de conexoes (handshake TLS inteiro a cada chamada
    // contra host externo) e ainda reenvia GETs sozinho em falha de I/O.
    //
    // 10s de read: cadastro de cartao no gateway envolve validacao com a bandeira. Nao e
    // instantaneo, mas tambem nao e cobranca - nao precisa da folga do capture.
    private ClientHttpRequestFactory generateClientHttpRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
