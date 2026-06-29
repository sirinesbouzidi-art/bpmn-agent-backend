package com.example.bpmn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ADMIN_EMAIL = "admin@bouygues.com";
    private static final String ADMIN_PASSWORD = "admin123";

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("token").asText();
    }

    // ─────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────

    @Test
    void loginShouldReturnTokenForDefaultAdmin() throws Exception {
        String body = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginShouldReturnBadRequestWhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is required"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void loginShouldRejectUnknownUser() throws Exception {
        String body = """
                {
                  "email": "ghost@bouygues.com",
                  "password": "whatever123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────
    // Public self-registration must no longer exist
    // ─────────────────────────────────────────────────────────────────

    @Test
    void publicRegisterEndpointShouldNoLongerExist() throws Exception {
        String body = """
                {
                  "email": "sneaky@bouygues.com",
                  "password": "password123",
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────
    // /generate-bpmn-xml stays public
    // ─────────────────────────────────────────────────────────────────

    @Test
    void generateBpmnXmlShouldBePublic() throws Exception {
        String body = """
                {
                  "process": {
                    "id": "process_test",
                    "name": "Process Test"
                  },
                  "elements": [
                    {
                      "id": "task_1",
                      "type": "userTask",
                      "name": "Saisir demande"
                    },
                    {
                      "id": "end_1",
                      "type": "endEvent",
                      "name": "Fin"
                    }
                  ],
                  "flows": [
                    {
                      "id": "flow_start_task",
                      "from": "startEvent",
                      "to": "task_1"
                    },
                    {
                      "id": "flow_task_end",
                      "from": "task_1",
                      "to": "end_1"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/generate-bpmn-xml")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("BPMNShape")))
                .andExpect(content().string(containsString("BPMNEdge")))
                .andExpect(content().string(containsString("Bounds")))
                .andExpect(content().string(containsString("waypoint")));
    }

    // ─────────────────────────────────────────────────────────────────
    // Generic JWT protection
    // ─────────────────────────────────────────────────────────────────

    @Test
    void protectedEndpointShouldRequireJwt() throws Exception {
        mockMvc.perform(get("/api/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointShouldAcceptValidAdminJwt() throws Exception {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    // ─────────────────────────────────────────────────────────────────
    // Admin-only user management: /api/admin/users
    // ─────────────────────────────────────────────────────────────────

    @Test
    void adminUsersEndpointShouldRequireJwt() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateNewUserAndNewUserCanLogin() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createBody = """
                {
                  "email": "new-member@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User created successfully"));

        // Le nouvel utilisateur doit pouvoir se connecter avec le mot de passe choisi par l'admin
        String loginBody = """
                {
                  "email": "new-member@bouygues.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void adminCanCreateAnotherAdmin() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createBody = """
                {
                  "email": "second-admin@bouygues.com",
                  "password": "password123",
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "email": "second-admin@bouygues.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminCreateUserShouldRejectInvalidRole() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createBody = """
                {
                  "email": "bad-role@bouygues.com",
                  "password": "password123",
                  "role": "SUPER_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Role must be either USER or ADMIN"));
    }

    @Test
    void adminCreateUserShouldRejectDuplicateEmail() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createBody = """
                {
                  "email": "duplicate@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        // Deuxième création avec le même email -> doit échouer
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isConflict());
    }

    @Test
    void nonAdminUserCannotCreateUsers() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        // L'admin crée d'abord un utilisateur normal
        String createBody = """
                {
                  "email": "regular-member@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        // Ce nouvel utilisateur se connecte, puis tente de créer un autre compte -> doit être refusé
        String memberToken = loginAndGetToken("regular-member@bouygues.com", "password123");

        String attemptBody = """
                {
                  "email": "should-not-exist@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminUserCannotListUsers() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createBody = """
                {
                  "email": "viewer-member@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        String memberToken = loginAndGetToken("viewer-member@bouygues.com", "password123");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createBody = """
                {
                  "email": "list-check@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem("list-check@bouygues.com")))
                .andExpect(jsonPath("$[*].email", hasItem(ADMIN_EMAIL)));
    }

    @Test
    void adminCanDeleteUserAndDeletedUserCannotLoginAnymore() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createBody = """
                {
                  "email": "to-be-removed@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        // Confirme que l'utilisateur peut bien se connecter avant suppression
        loginAndGetToken("to-be-removed@bouygues.com", "password123");

        mockMvc.perform(delete("/api/admin/users/to-be-removed@bouygues.com")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deleted successfully"));

        String loginBody = """
                {
                  "email": "to-be-removed@bouygues.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminUserCannotDeleteUsers() throws Exception {
        String adminToken = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        String createTargetBody = """
                {
                  "email": "protected-member@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTargetBody))
                .andExpect(status().isCreated());

        String createAttackerBody = """
                {
                  "email": "attacker-member@bouygues.com",
                  "password": "password123",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAttackerBody))
                .andExpect(status().isCreated());

        String attackerToken = loginAndGetToken("attacker-member@bouygues.com", "password123");

        mockMvc.perform(delete("/api/admin/users/protected-member@bouygues.com")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());
    }
}