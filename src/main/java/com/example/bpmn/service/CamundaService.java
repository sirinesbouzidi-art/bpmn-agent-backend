package com.example.bpmn.service;

import com.example.bpmn.dto.CamundaDeployResponse;
import com.example.bpmn.exception.CamundaIntegrationException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class CamundaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CamundaService.class);

    private final ZeebeClient client;
    private final ZeebeCompatibilityService zeebeCompatibilityService;

    public CamundaService(
            @Value("${camunda.gateway-address:localhost:26500}") String gatewayAddress,
            ZeebeCompatibilityService zeebeCompatibilityService
    ) {
        // Local self-managed Camunda uses direct Zeebe gRPC (no OAuth).
        // Camunda SaaS/cloud requires OAuth and different connectivity.
        this.zeebeCompatibilityService = zeebeCompatibilityService;
        this.client = ZeebeClient.newClientBuilder()
                .gatewayAddress(gatewayAddress)
                .usePlaintext()
                .build();
    }

    public CamundaDeployResponse deployBpmn(String xml, String processName) {
        try {
            String zeebeCompatibleXml =
                zeebeCompatibilityService.enrich(xml);

        System.out.println(
                "========== ZEEBE XML ==========");
        System.out.println(zeebeCompatibleXml);
        System.out.println(
                "================================");
System.out.println("===== FLOW_3 =====");
int idx3 = zeebeCompatibleXml.indexOf("id=\"flow_3\"");
if (idx3 != -1) {
    System.out.println(
        zeebeCompatibleXml.substring(
            Math.max(0, idx3 - 200),
            Math.min(zeebeCompatibleXml.length(), idx3 + 600)
        )
    );
}

System.out.println("===== FLOW_4 =====");
int idx4 = zeebeCompatibleXml.indexOf("id=\"flow_4\"");
if (idx4 != -1) {
    System.out.println(
        zeebeCompatibleXml.substring(
            Math.max(0, idx4 - 200),
            Math.min(zeebeCompatibleXml.length(), idx4 + 600)
        )
    );
}
        DeploymentEvent deploymentEvent =
                client.newDeployResourceCommand()
                        .addResourceBytes( zeebeCompatibleXml.getBytes(StandardCharsets.UTF_8), sanitizeProcessName(processName) + ".bpmn")
                        .send()
                        .join();

            long deploymentKey = deploymentEvent.getKey();

            return new CamundaDeployResponse(
                    true,
                    String.valueOf(deploymentKey),
                    "Deployed successfully to local Camunda"
            );
        } catch (ClientException ex) {
            LOGGER.error("Camunda local deployment failed: {}", ex.getMessage(), ex);
            throw new CamundaIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to deploy BPMN to local Camunda", ex);
        } catch (Exception ex) {
            ex.printStackTrace();

    Throwable root = ex;
    while (root.getCause() != null) {
        root = root.getCause();
    }

    System.out.println( "ROOT CAUSE = " + root.getMessage());

    throw ex;
        }
    }

    private String sanitizeProcessName(String processName) {
        return processName.trim().replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}