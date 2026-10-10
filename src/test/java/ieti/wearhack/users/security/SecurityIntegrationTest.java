package ieti.wearhack.users.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@ActiveProfiles("test")
class SecurityIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void loginEndpointIsPublicAndReturnsAuthorizationUrl() throws Exception {
		mockMvc.perform(get("/api/auth/google/login"))
			.andExpect(status().isOk())
			.andExpect(cookie().exists("oauth_state"))
			.andExpect(jsonPath("$.authorizationUrl", containsString("accounts.google.com")))
			.andExpect(jsonPath("$.authorizationUrl", containsString("test-client-id")));
	}

	@Test
	void loginEndpointRedirectsWhenAsked() throws Exception {
		mockMvc.perform(get("/api/auth/google/login").param("redirect", "true"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("accounts.google.com")));
	}

	@Test
	void callbackWithoutCodeIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/auth/google/callback")).andExpect(status().isBadRequest());
	}

	@Test
	void callbackWithMismatchedStateIsBadRequest() throws Exception {
		mockMvc
			.perform(get("/api/auth/google/callback").param("code", "abc")
				.param("state", "one")
				.cookie(new Cookie("oauth_state", "two")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void idTokenEndpointRejectsMissingBodyField() throws Exception {
		mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("idToken")));
	}

	@Test
	void idTokenEndpointRejectsMalformedJson() throws Exception {
		mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void idTokenEndpointRejectsInvalidGoogleToken() throws Exception {
		mockMvc
			.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
				.content("{\"idToken\":\"not-a-google-token\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void logoutWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.path").value("/api/auth/logout"));
	}

	@Test
	void protectedEndpointWithInvalidJwtIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/anything").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized());
	}

}
