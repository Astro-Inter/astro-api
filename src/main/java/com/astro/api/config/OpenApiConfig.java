package com.astro.api.config;

import com.astro.api.auth.security.AuthenticatedUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

    @Bean
    public OpenApiCustomizer errorResponseSchemas() {
        return openApi -> {
            ObjectSchema error = errorSchema();
            ObjectSchema spreadsheetError = errorSchema();
            ObjectSchema spreadsheetMessage = new ObjectSchema();
            spreadsheetMessage.addProperty("row", new Schema<>().type("integer").format("int32"));
            spreadsheetMessage.addProperty("field", new StringSchema());
            spreadsheetMessage.addProperty("message", new StringSchema());
            spreadsheetError.addProperty("data", new ArraySchema().items(spreadsheetMessage));
            openApi.getComponents().addSchemas("ApiErrorResponse", error);
            openApi.getComponents().addSchemas("SpreadsheetImportErrorResponse", spreadsheetError);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }
                operation.getResponses().forEach((code, response) -> {
                    if (!code.matches("[45][0-9][0-9]") || response.getContent() == null) {
                        return;
                    }
                    String schemaName = code.equals("422") ? "SpreadsheetImportErrorResponse" : "ApiErrorResponse";
                    response.getContent().values().forEach(media ->
                            media.setSchema(new Schema<>().$ref("#/components/schemas/" + schemaName)));
                });
            }));
        };
    }

    private static ObjectSchema errorSchema() {
        ObjectSchema error = new ObjectSchema();
        error.addProperty("success", new Schema<>().type("boolean"));
        error.addProperty("message", new StringSchema());
        error.addProperty("data", new Schema<>().nullable(true));
        error.addProperty("errors", new ArraySchema().items(new StringSchema()));
        error.addProperty("path", new StringSchema());
        return error;
    }
}

