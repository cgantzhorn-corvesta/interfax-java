package net.interfax.rest.client.impl;

import net.interfax.rest.client.InterFAXClient;
import net.interfax.rest.client.config.ClientConfig;
import net.interfax.rest.client.config.ClientCredentials;
import net.interfax.rest.client.config.ConfigLoader;
import net.interfax.rest.client.domain.APIResponse;
import org.apache.tika.Tika;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.net.URI;
import java.util.concurrent.locks.ReentrantLock;

public class InterFAXJerseyClient implements InterFAXClient {

    private static String username;
    private static String password;
    private static String outboundFaxesEndpoint;
    private static HttpAuthenticationFeature httpAuthenticationFeature;
    private static Client client;
    private static Tika tika;

    private final ReentrantLock reentrantLock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(InterFAXJerseyClient.class);

    public InterFAXJerseyClient(String username, String password) {

        this.username = username;
        this.password = password;

        initializeClient(username, password);
    }

    public InterFAXJerseyClient() {

        initialiseCredentials();
        initializeClient(username, password);
    }

    public APIResponse sendFax(final String faxNumber, final File fileToSendAsFax) {

        javax.ws.rs.core.Response response = null;
        APIResponse apiResponse = null;

        try {

            String contentType = tika.detect(fileToSendAsFax);

            URI outboundFaxesUri = UriBuilder.fromUri(outboundFaxesEndpoint).queryParam("faxNumber", faxNumber).build();
            WebTarget target = client.target(outboundFaxesUri);
            response = target
                            .request()
                            .header("Content-Type", contentType)
                            .post(Entity.entity(fileToSendAsFax, contentType));

            apiResponse = new APIResponse();
            apiResponse.setStatusCode(response.getStatus());

            if (response.hasEntity())
                apiResponse.setResponseBody(response.readEntity(String.class));

        } catch (Exception e) {
            log.error("Exception occurred while sending fax", e);
        } finally {
            if (response != null)
                response.close();
        }

        return apiResponse;
    }

    public void closeClient() {

        client.close();
    }

    private void initialiseCredentials() {

        ClientCredentials clientCredentials = new ConfigLoader<>(ClientCredentials.class, "interfax-api-credentials.yaml").getTestConfig();

        username = clientCredentials.getUsername();
        password = clientCredentials.getPassword();
    }

    private void initializeClient(String username, String password) {

        reentrantLock.lock();
        try {

            if (client != null)
                return;

            ClientConfig clientConfig = new ConfigLoader<>(ClientConfig.class, "interfax-api-config.yaml").getTestConfig();
            httpAuthenticationFeature = HttpAuthenticationFeature.basic(username, password);
            client = ClientBuilder.newClient();
            client.register(httpAuthenticationFeature);

            tika = new Tika();

            outboundFaxesEndpoint = clientConfig.getInterFAX().getOutboundFaxesEndpoint();
        } finally {
            reentrantLock.unlock();
        }
    }
}
