package com.mycompany.bot;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.symphony.bdk.core.activity.ActivityMatcher;
import com.symphony.bdk.core.activity.form.FormReplyActivity;
import com.symphony.bdk.core.activity.form.FormReplyContext;
import com.symphony.bdk.core.activity.model.ActivityInfo;
import com.symphony.bdk.core.activity.model.ActivityType;
import com.symphony.bdk.core.auth.AuthSession;
import com.symphony.bdk.core.service.message.MessageService;
import com.symphony.bdk.core.service.message.model.Message;
import com.symphony.bdk.core.service.user.UserService;
import com.symphony.bdk.gen.api.model.Password;
import com.symphony.bdk.gen.api.model.V2UserAttributes;
import com.symphony.bdk.gen.api.model.V2UserCreate;
import com.symphony.bdk.gen.api.model.V2UserDetail;

public class UserCreationFormActivityAPI extends FormReplyActivity<FormReplyContext> {

  @Override
  public ActivityInfo info() {
    return new ActivityInfo().type(ActivityType.FORM).name("User Creation Form Activity");
  }
  private final MessageService messageService;
  private final UserService userService;
  private final AuthSession authSession;

  private static final Logger LOGGER = LoggerFactory.getLogger(UserCreationFormActivityAPI.class);

  public UserCreationFormActivityAPI(MessageService messageService, UserService userService, AuthSession authSession) {
    this.messageService = messageService;
    this.userService = userService;
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

    final String message = "<messageML>User details are '" + firstName + " " + lastName + " " + email + "'</messageML>";
    this.messageService.send(context.getSourceEvent().getStream(), Message.builder().content(message).build());

    try {
        // Create user creation payload
        V2UserCreate v2UserCreate = new V2UserCreate();
        V2UserAttributes userAttributes = new V2UserAttributes();
        userAttributes.setAccountType(V2UserAttributes.AccountTypeEnum.NORMAL);
        userAttributes.setFirstName(firstName);
        userAttributes.setLastName(lastName);
        userAttributes.setDisplayName(firstName + " " + lastName);
        userAttributes.setEmailAddress(email);
        userAttributes.setUserName(email);

        v2UserCreate.setUserAttributes(userAttributes);

        // Hash the password if provided
        if (password != null && !password.isEmpty()) {
            SecureRandom sr = new SecureRandom();
            byte[] saltBytes = new byte[16];
            sr.nextBytes(saltBytes);
            String salt = Base64.getEncoder().encodeToString(saltBytes);
            byte[] hashedPasswordBytes = generateStrongPasswordHash(password, saltBytes);
            String hashedPassword = Base64.getEncoder().encodeToString(hashedPasswordBytes);

            Password passwordObject = new Password();
            passwordObject.sethSalt(salt);
            passwordObject.sethPassword(hashedPassword);
            passwordObject.setKhSalt(salt);
            passwordObject.setKhPassword(hashedPassword);

            v2UserCreate.setPassword(passwordObject);
        }

        // Create the user
        V2UserDetail userDetail = userService.create(v2UserCreate);

        String userCreatedMessage = "<messageML>User created: " + userDetail.getUserSystemInfo().getId() + "</messageML>";
        this.messageService.send(context.getSourceEvent().getStream(), Message.builder().content(userCreatedMessage).build());

    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
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