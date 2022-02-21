package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.qortal.api.Security;

@OpenAPIDefinition(
		info = @Info( title = "Qortal API", description = "NOTE: byte-arrays are encoded in Base58" ),
		tags = {
			@Tag(name = "Addresses"),
			@Tag(name = "Admin"),
			@Tag(name = "Arbitrary"),
			@Tag(name = "Assets"),
			@Tag(name = "Automated Transactions"),
			@Tag(name = "Blocks"),
			@Tag(name = "Chat"),
			@Tag(name = "Cross-Chain"),
			@Tag(name = "Groups"),
			@Tag(name = "Names"),
			@Tag(name = "Payments"),
			@Tag(name = "Peers"),
			@Tag(name = "Transactions"),
			@Tag(name = "Utilities")
		},
		extensions = {
			@Extension(name = "translation", properties = {
					@ExtensionProperty(name="title.key", value="info:title")
			})
		}
)
@SecuritySchemes({
	@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic"),
	@SecurityScheme(name = "apiKey", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Security.API_KEY_HEADER)
})
public class ApiDefinition {
}