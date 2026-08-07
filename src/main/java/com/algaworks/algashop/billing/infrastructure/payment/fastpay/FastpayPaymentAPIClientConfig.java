package com.algaworks.algashop.billing.infrastructure.payment.fastpay;

import com.algaworks.algashop.billing.infrastructure.creditcard.fastpay.FastpayCreditCardAPIClient;
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
public class FastpayPaymentAPIClientConfig {

    @Bean
    public FastpayPaymentAPIClient fastpayPaymentAPIClient(
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

        return proxyFactory.createClient(FastpayPaymentAPIClient.class);
    }

    // O timeout e a camada mais importante das quatro: e ele que transforma "pendurado" em
    // "falhou". Sem ele o circuito nunca abre - ele so reage a falhas que TERMINARAM - e a
    // thread fica presa para sempre. O default do JDK aqui e infinito.
    //
    // JdkClientHttpRequestFactory no lugar do SimpleClientHttpRequestFactory: o Simple usa
    // HttpURLConnection, sem pool de conexoes (handshake TLS inteiro a cada chamada contra
    // host externo) e com reenvio automatico de GET em falha de I/O.
    //
    // 20s de read, e nao 7s, DE PROPOSITO. Estourar o timeout NAO cancela a cobranca do
    // outro lado - so desiste de esperar a resposta. Gateway leva segundos mesmo (antifraude,
    // autorizacao do emissor), entao cortar cedo nao protege de nada: so aumenta a frequencia
    // do pior estado possivel, que e "nao sei se cobrou". Quem resolve a incerteza e a
    // conciliacao (webhook + findByCode), nao um timeout curto.
    //
    // Como o RestClient e por interface, este valor vale tambem para findById/refund/cancel.
    // Errar para o lado generoso e o mal menor: essas tres tem retry, e o pior caso delas
    // continua limitado pelo circuito.
    private ClientHttpRequestFactory generateClientHttpRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }
}
