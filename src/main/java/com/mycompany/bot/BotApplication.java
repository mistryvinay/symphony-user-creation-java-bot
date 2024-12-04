package com.mycompany.bot;

import static java.util.Collections.singletonMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.symphony.bdk.core.SymphonyBdk;
import static com.symphony.bdk.core.activity.command.SlashCommand.slash;
import com.symphony.bdk.core.auth.AuthSession;
import static com.symphony.bdk.core.config.BdkConfigLoader.loadFromClasspath;
import com.symphony.bdk.core.service.datafeed.RealTimeEventListener;
import com.symphony.bdk.core.service.message.model.Message;
import com.symphony.bdk.gen.api.model.V4Initiator;
import com.symphony.bdk.gen.api.model.V4UserJoinedRoom;
import com.symphony.bdk.template.api.Template;

/**
 * Simple Bot Application.
 */
public class BotApplication {
  private static final Logger LOGGER = LoggerFactory.getLogger(BotApplication.class);

  public static void main(String[] args) throws Exception {
    // Initialize BDK entry point
    final SymphonyBdk bdk = new SymphonyBdk(loadFromClasspath("/config.yaml"));

    // Obtain the authenticated bot session token
    AuthSession botSession = bdk.botSession();
    LOGGER.info("Bot session token: " + botSession.getSessionToken());
    // Register a "slash" activity
    bdk.activities().register(slash("/gif", false, context -> {
        Template template = bdk.messages().templates().newTemplateFromClasspath("/templates/gif.ftl");
        bdk.messages().send(context.getStreamId(), Message.builder().template(template).build());
    }));

    bdk.activities().register(slash("/create", false, context -> {
        Template template = bdk.messages().templates().newTemplateFromClasspath("/templates/userform.ftl");
        bdk.messages().send(context.getStreamId(), Message.builder().template(template).build());
    }));

    // Register a "formReply" activity that handles the Gif category form submission
    bdk.activities().register(new GifFormActivity(bdk.messages()));
    bdk.activities().register(new UserCreationFormActivityAPI(bdk.messages(), botSession, "https://develop2.symphony.com"));

    // Subscribe to 'onUserJoinedRoom' Real Time Event
    bdk.datafeed().subscribe(new RealTimeEventListener() {
      @Override
      public void onUserJoinedRoom(V4Initiator initiator, V4UserJoinedRoom event) {
        LOGGER.info("User joined room");
        final String userDisplayName = event.getAffectedUser().getDisplayName();
        Template template = bdk.messages().templates().newTemplateFromClasspath("/templates/welcome.ftl");
        bdk.messages().send(event.getStream(),
            Message.builder().template(template, singletonMap("name", userDisplayName)).build());
      }
    });

    // finally, start the datafeed read loop
    bdk.datafeed().start();
  }
}
