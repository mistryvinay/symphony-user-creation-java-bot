package com.mycompany.bot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.symphony.bdk.core.activity.ActivityMatcher;
import com.symphony.bdk.core.activity.form.FormReplyActivity;
import com.symphony.bdk.core.activity.form.FormReplyContext;
import com.symphony.bdk.core.activity.model.ActivityInfo;
import com.symphony.bdk.core.activity.model.ActivityType;
import com.symphony.bdk.core.auth.AuthSession;
import com.symphony.bdk.core.service.message.MessageService;
import com.symphony.bdk.core.service.message.model.Message;


public class UserCreationFormActivityAPI extends FormReplyActivity<FormReplyContext> {

  @Override
  public ActivityInfo info() {
    return new ActivityInfo().type(ActivityType.FORM).name("User Creation Form Activity");
  }
  private final MessageService messageService;
  private final String apiUrl;
  private final AuthSession authSession;

  private static final Logger LOGGER = LoggerFactory.getLogger(UserCreationFormActivityAPI.class);

  public UserCreationFormActivityAPI(MessageService messageService, AuthSession authSession, String apiUrl) {
    this.messageService = messageService;
    this.apiUrl = apiUrl;
    this.authSession = authSession;
  }

  @Override
  public ActivityMatcher<FormReplyContext> matcher() {
    return context -> "user-creation-form".equals(context.getFormId())
        && "submit".equals(context.getFormValue("action"));
  }

@Override
public void onActivity(FormReplyContext context) {
    final String firstName = context.getFormValue("firstName");
    final String lastName = context.getFormValue("lastName");
    final String email = context.getFormValue("email");
    final String password = context.getFormValue("password");

    final String message = "<messageML>User details are '" + firstName + " " + lastName + " " + email + " " + password + "'</messageML>";
    this.messageService.send(context.getSourceEvent().getStream(), Message.builder().content(message).build());

    try {
        // Retrieve the session token
        String sessionToken = authSession.getSessionToken();

        // Create user creation payload
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode userAttributes = mapper.createObjectNode();
        userAttributes.put("accountType", "NORMAL");
        userAttributes.put("firstName", firstName);
        userAttributes.put("lastName", lastName);
        userAttributes.put("displayName", firstName + " " + lastName);
        userAttributes.put("emailAddress", email);
        userAttributes.put("userName", email);

        ObjectNode userCreatePayload = mapper.createObjectNode();
        userCreatePayload.set("userAttributes", userAttributes);

        // Hash the password if provided
        if (password != null && !password.isEmpty()) {
            SecureRandom sr = new SecureRandom();
            byte[] saltBytes = new byte[16];
            sr.nextBytes(saltBytes);
            String salt = Base64.getEncoder().encodeToString(saltBytes);
            byte[] hashedPasswordBytes = generateStrongPasswordHash(password, saltBytes);
            String hashedPassword = Base64.getEncoder().encodeToString(hashedPasswordBytes);

            ObjectNode passwordNode = mapper.createObjectNode();
            passwordNode.put("hSalt", salt);
            passwordNode.put("hPassword", hashedPassword);
            passwordNode.put("khSalt", salt);
            passwordNode.put("khPassword", hashedPassword);

            userCreatePayload.set("password", passwordNode);
        }

        // Build the HTTP request
        LOGGER.info("Creating user with payload: " + userCreatePayload.toString());
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl + "/pod/v2/admin/user/create"))
            .header("sessionToken", sessionToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(userCreatePayload)))
            .build();

        // Send the request and handle the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Parse the response to get user details
            ObjectNode responseBody = (ObjectNode) mapper.readTree(response.body());
            String userId = responseBody.path("userSystemInfo").path("id").asText();
            String userCreatedMessage = "<messageML>User created: " + userId + "</messageML>";
            this.messageService.send(context.getSourceEvent().getStream(), Message.builder().content(userCreatedMessage).build());
        } else {
            String errorMessage = "<messageML>Failed to create user. API response: " + response.body() + "</messageML>";
            this.messageService.send(context.getSourceEvent().getStream(), Message.builder().content(errorMessage).build());
        }
    } catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidKeySpecException e) {
        String errorMessage = "<messageML>Exception occurred: " + e.getMessage() + "</messageML>";
        this.messageService.send(context.getSourceEvent().getStream(), Message.builder().content(errorMessage).build());
    }
}

private static byte[] generateStrongPasswordHash(String password, byte[] salt)
    throws NoSuchAlgorithmException, InvalidKeySpecException {
    int iterations = 10000;
    char[] pb = password.toCharArray();
    PBEKeySpec spec = new PBEKeySpec(pb, salt, iterations, 256);
    SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    byte[] hash = skf.generateSecret(spec).getEncoded();
    return hash;
}
}