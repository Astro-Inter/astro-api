package com.astro.api.config;

import com.astro.api.auth.security.AuthenticatedUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.utils.SpringDocUtils;

@Configuration
public class OpenApiConfig {

    public OpenApiConfig() {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthenticatedUser.class);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API do Astro")
                        .version("v1")
                        .description("""
                                API de gestão de conformidade com Normas Regulamentadoras do Astro.

                                ### Como autenticar
                                Faça login no aplicativo cliente com Firebase Authentication e obtenha o ID token.
                                Clique em **Authorize** e cole somente o token, sem o prefixo `Bearer`.
                                As chamadas protegidas usam `Authorization: Bearer <ID token>`.
                                Quando o token expirar, obtenha um novo pelo SDK Firebase do cliente.

                                O perfil e o vínculo do usuário determinam o acesso aos dados do workspace.
                                As rotas de identificação, ativação e cadastro inicial de workspace são públicas.
                                A API não possui endpoint próprio de login com senha.

                                ### Respostas
                                As respostas JSON usam, em geral, `ApiResult`: `success`, `message`, `data`,
                                `errors` e `path`. Consulte o schema de cada operação para as exceções.
                                Respostas **204** não possuem corpo. Os códigos e regras específicos estão
                                descritos em cada operação; erros de autenticação podem não conter esse envelope.
                                """))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("ID token emitido pelo Firebase Authentication. Cole apenas o token, sem Bearer.")
                        ))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("bearerAuth")
                );
    }
}

