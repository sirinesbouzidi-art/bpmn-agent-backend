package com.example.bpmn.service;

import com.example.bpmn.dto.admin.AdminDashboardResponse;
import com.example.bpmn.dto.admin.DashboardActivityDTO;
import com.example.bpmn.dto.admin.DashboardProcessDTO;
import com.example.bpmn.dto.admin.DashboardStatusItemDTO;
import com.example.bpmn.dto.admin.DashboardSystemStatusDTO;
import com.example.bpmn.model.GeneratedBpmnModel;
import com.example.bpmn.model.Role;
import com.example.bpmn.repository.GeneratedBpmnModelRepository;
import com.example.bpmn.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

@Service
public class AdminDashboardService {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private final UserRepository userRepository;
    private final GeneratedBpmnModelRepository generatedBpmnModelRepository;
    private final DataSource dataSource;
    private final RestClient restClient;
    private final String fastApiHealthUrl;
    private final String camundaGatewayAddress;

    public AdminDashboardService(UserRepository userRepository,
                                 GeneratedBpmnModelRepository generatedBpmnModelRepository,
                                 DataSource dataSource,
                                 RestClient.Builder restClientBuilder,
                                 @Value("${app.fastapi.health-url:http://localhost:8000/health}") String fastApiHealthUrl,
                                 @Value("${camunda.gateway-address:localhost:26500}") String camundaGatewayAddress) {
        this.userRepository = userRepository;
        this.generatedBpmnModelRepository = generatedBpmnModelRepository;
        this.dataSource = dataSource;
        this.restClient = restClientBuilder.requestFactory(requestFactory()).build();
        this.fastApiHealthUrl = fastApiHealthUrl;
        this.camundaGatewayAddress = camundaGatewayAddress;
    }

    public AdminDashboardResponse getDashboard() {
        long totalAdmins = userRepository.countByRole(Role.ADMIN);
        long totalStandardUsers = userRepository.countByRole(Role.USER);

        return new AdminDashboardResponse(
                userRepository.count(),
                totalAdmins,
                totalStandardUsers,
                generatedBpmnModelRepository.count(),
                countGeneratedToday(),
                generationPerDay(),
                latestProcesses(),
                recentActivities(),
                systemStatus()
        );
    }

    private long countGeneratedToday() {
        LocalDate today = LocalDate.now();
        return generatedBpmnModelRepository.countByGenerationDateBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private List<Integer> generationPerDay() {
        LocalDate firstDay = LocalDate.now().minusDays(6);
        return firstDay.datesUntil(firstDay.plusDays(7))
                .map(day -> (int) generatedBpmnModelRepository.countByGenerationDateBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .toList();
    }

    private List<DashboardProcessDTO> latestProcesses() {
        return generatedBpmnModelRepository.findTop5ByOrderByGenerationDateDesc().stream()
                .map(this::toProcessDTO)
                .toList();
    }

    private List<DashboardActivityDTO> recentActivities() {
        return generatedBpmnModelRepository.findTop10ByOrderByGenerationDateDesc().stream()
                .map(model -> new DashboardActivityDTO(
                        "BPMN model generated",
                        model.processName(),
                        model.generationDate(),
                        "history"
                ))
                .toList();
    }

    private DashboardProcessDTO toProcessDTO(GeneratedBpmnModel model) {
        return new DashboardProcessDTO(model.processName(), model.author(), model.generationDate(), model.status());
    }

    private DashboardSystemStatusDTO systemStatus() {
        return new DashboardSystemStatusDTO(
                statusItem("Backend", "Spring Boot API", true, "server"),
                statusItem("FastAPI", fastApiHealthUrl, isHttpReachable(fastApiHealthUrl), "api"),
                statusItem("Database", "Primary datasource", isDatabaseOnline(), "database"),
                statusItem("Camunda", camundaGatewayAddress, isTcpReachable(camundaGatewayAddress), "workflow")
        );
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(HEALTH_TIMEOUT);
        factory.setReadTimeout(HEALTH_TIMEOUT);
        return factory;
    }

    private DashboardStatusItemDTO statusItem(String label, String value, boolean online, String icon) {
        return new DashboardStatusItemDTO(label, value, online ? "online" : "offline", icon);
    }

    private boolean isDatabaseOnline() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid((int) HEALTH_TIMEOUT.toSeconds());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isHttpReachable(String url) {
        try {
            return restClient.get().uri(url).retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isTcpReachable(String address) {
        String[] hostAndPort = address.split(":", 2);
        if (hostAndPort.length != 2) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1])), (int) HEALTH_TIMEOUT.toMillis());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}