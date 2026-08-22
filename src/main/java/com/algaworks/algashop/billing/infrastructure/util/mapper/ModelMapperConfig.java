package com.algaworks.algashop.billing.infrastructure.util.mapper;

import com.algaworks.algashop.billing.application.invoice.query.PaymentSettingsOutput;
import com.algaworks.algashop.billing.application.util.Mapper;
import com.algaworks.algashop.billing.domain.model.invoice.PaymentSettings;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.convention.NamingConventions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    public Mapper mapper() {
        ModelMapper modelMapper = new ModelMapper();
        configuration(modelMapper);

        // unica excecao ao STRICT: o contrato (billing.yml) chama o campo de "method",
        // mas o agregado chama de "paymentMethod" - sem este mapping o campo sai null
        modelMapper.typeMap(PaymentSettings.class, PaymentSettingsOutput.class)
                .addMapping(PaymentSettings::getPaymentMethod, PaymentSettingsOutput::setMethod);

        return modelMapper::map;
    }

    // configurando o mapper para entender a mapear o objeto sem precisar ser exatamente como
    // o padrão java Bean como get e setter, strategy indica como deve ser mapeado os objetos
    // ou seja, STRICT indica que o nome dos dois DEVEM ser iguais
    private void configuration(ModelMapper modelMapper) {
        modelMapper.getConfiguration()
                .setSourceNamingConvention(NamingConventions.NONE)
                .setDestinationNamingConvention(NamingConventions.NONE)
                .setMatchingStrategy(MatchingStrategies.STRICT);

    }
}
