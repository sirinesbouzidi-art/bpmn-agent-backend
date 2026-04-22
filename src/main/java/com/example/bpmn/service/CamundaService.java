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

    public CamundaService(@Value("${camunda.gateway-address:localhost:26500}") String gatewayAddress) {
        // Local self-managed Camunda uses direct Zeebe gRPC (no OAuth).
        // Camunda SaaS/cloud requires OAuth and different connectivity.
        this.client = ZeebeClient.newClientBuilder()
                .gatewayAddress(gatewayAddress)
                .usePlaintext()
                .build();
    }

    public CamundaDeployResponse deployBpmn(String xml, String processName) {
        try {
            DeploymentEvent deploymentEvent = client.newDeployResourceCommand()
                    .addResourceBytes(xml.getBytes(StandardCharsets.UTF_8), sanitizeProcessName(processName) + ".bpmn")
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
            LOGGER.error("Unexpected Camunda local deployment error", ex);
            throw new CamundaIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error while deploying BPMN to local Camunda", ex);
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